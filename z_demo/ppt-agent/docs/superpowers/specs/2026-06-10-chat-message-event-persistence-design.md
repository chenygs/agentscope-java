# 扩展 chat_session_message 表 + 完善 ChatPersistenceMiddleware

**日期**：2026-06-10
**目标**：让 PPT Agent 把 AG-UI 前端可见的所有交互事件结构化落库，支持完整回放。

---

## 1. 背景与动机

### 现状

- `chat_session_message` 表只有 5 个字段（`id / session_id / role / content / created_at`），且只存 `user` 与 `assistant` 两种文本消息。
- `ChatPersistenceMiddleware` 用 `onReasoning` 钩子只累积 `TEXT_BLOCK_DELTA`，工具调用、思维链、审批等事件完全未落库。
- RC2 里 Agent 运行时状态被整块存进 `ppt_session.state_data`（blob），SQL 无法按字段查询；想"按事件回放对话"必须依赖另一份结构化存储。

### 目标

把 AG-UI 前端能看到的**所有事件**结构化存进 `chat_session_message`，前端按 `SELECT * WHERE session_id=? ORDER BY id` 就能完整重建对话视图。

### 非目标

- 不做 SaaS（不存 token、不做计费、不做用户活跃度分析）
- 不存框架内部生命周期事件（`AGENT_START/END`、`MODEL_CALL_START/END`）
- 不做多模态二进制本地存储（图片仅存 URL）

---

## 2. 事件落库范围

参照 AgentScope RC2 `AgentEventType`，按"AG-UI 前端是否渲染"为标准取舍：

| 事件类型 | AG-UI 渲染 | 落库 | 落库时机 |
|---------|----------|------|---------|
| `AGENT_START` / `AGENT_END` | ⚠️ 仅生命周期标记 | ❌ | — |
| `MODEL_CALL_START` / `MODEL_CALL_END` | ⚠️ 仅调试用 | ❌ | — |
| `TEXT_BLOCK_START/DELTA/END` | ✅ 主对话气泡 | ✅ | `END` 时累积落一行 |
| `THINKING_BLOCK_START/DELTA/END` | ✅ 折叠思维链 | ✅ | `END` 时累积落一行 |
| `TOOL_CALL_START/DELTA/END` | ✅ 工具调用卡片 | ✅ | `END` 时累积落一行 |
| `TOOL_RESULT_START/TEXT_DELTA/END` | ✅ 工具结果卡片 | ✅ | `END` 时累积落一行 |
| `TOOL_RESULT_DATA_DELTA` | ✅ 嵌入图片 | ✅ | 事件发生即落一行（URL 模式）|
| `DATA_BLOCK_START/DELTA/END` | ✅ 附件 | ✅ | `END` 时累积落一行 |
| `EXCEED_MAX_ITERS` | ✅ 错误提示 | ✅ | 即时 |
| `REQUEST_STOP` | ✅ "已停止" | ✅ | 即时 |
| `REQUIRE_USER_CONFIRM` | ✅ 审批弹窗 | ✅ | 即时 |
| `USER_CONFIRM_RESULT` | ✅ 审批结果 | ✅ | 即时 |
| `REQUIRE_EXTERNAL_EXECUTION` | ✅ 等待提示 | ✅ | 即时 |
| `EXTERNAL_EXECUTION_RESULT` | ✅ 执行结果 | ✅ | 即时 |
| `SUBAGENT_EXPOSED` | ✅ 子 Agent 卡片 | ✅ | 即时 |

**策略**：流式事件只在 `*_END` 时累积一次落库；即时事件直接落库。

---

## 3. 表结构

### Schema

```sql
ALTER TABLE chat_session_message
  ADD COLUMN message_type   VARCHAR(32)  NOT NULL DEFAULT 'text'
      COMMENT 'text / thinking / tool_call / tool_result / data / require_confirm / confirm_result / require_external / external_result / exceed_iters / request_stop / subagent_exposed',
  ADD COLUMN reply_id       VARCHAR(64)  NULL
      COMMENT '同一轮对话所有事件共享，对应 AgentEvent.replyId',
  ADD COLUMN tool_call_id   VARCHAR(128) NULL
      COMMENT '配对 tool_call / tool_result / 审批事件',
  ADD COLUMN tool_name      VARCHAR(64)  NULL
      COMMENT '工具名（tool_call / tool_result 时填）',
  ADD COLUMN content_type   VARCHAR(32)  NOT NULL DEFAULT 'text'
      COMMENT 'text / json / image/png / image/jpeg / audio/mp3 / ...',
  ADD COLUMN extra          JSON         NULL
      COMMENT '事件特有字段：tool_result.state / image url / 审批超时 / 子 agent name 等',
  ADD INDEX idx_session_reply (session_id, reply_id),
  ADD INDEX idx_session_type  (session_id, message_type);
```

`content` 列保持 `LONGTEXT`（避免任何字段长度限制问题）。

### 完整字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK | 自增 |
| `session_id` | BIGINT | 业务会话 ID |
| `role` | VARCHAR(20) | `user / assistant / tool / system` |
| `message_type` | VARCHAR(32) | 见上表枚举 |
| `reply_id` | VARCHAR(64) | 来自 `AgentEvent.replyId`，同一次回复的所有事件共享 |
| `tool_call_id` | VARCHAR(128) | 来自 `ToolCall*Event.toolCallId`，配对工具调用与结果 |
| `tool_name` | VARCHAR(64) | 来自 `ToolCallStartEvent.toolCallName` |
| `content` | LONGTEXT | 主内容（文本 / 工具参数 JSON / 工具结果文本） |
| `content_type` | VARCHAR(32) | 多模态用。文本默认 `text`，图片填 `image/png` 等 |
| `extra` | JSON | 兜底字段 — 各事件特有属性 |
| `created_at` | DATETIME | 落库时间 |

### `extra` 字段约定

不同 `message_type` 在 `extra` 里放不同的 JSON：

| message_type | extra 内容示例 |
|--------------|----------------|
| `tool_result` | `{"state": "success"}` / `{"state": "error"}` / `{"state": "denied"}` |
| `data` / `tool_result_data` | `{"url": "https://...","mediaType":"image/png"}` |
| `require_confirm` | `{"timeoutSec": 30, "reason": "..."}` |
| `confirm_result` | `{"approved": true}` |
| `subagent_exposed` | `{"agentName": "researcher", "agentId": "..."}` |
| `exceed_iters` | `{"maxIters": 30}` |
| 其它 | NULL |

新增事件类型扩展走 `extra` 即可，避免频繁改表。

### 多模态图片处理

按 **方案 C**：现阶段只存外链 URL。
- `content_type = "image/png"` / `"image/jpeg"` 等
- `extra.url` 存图片 URL（来自工具返回值）
- `content` 留空或存图片说明文字
- 后续接 OSS 时，扩展 `extra.storage_key` 字段即可

---

## 4. Middleware 设计

### 钩子选择

切换到 **`onAgent`** 钩子（替代当前的 `onReasoning`）：

| 钩子 | 优点 | 缺点 |
|------|------|------|
| `onReasoning` | 当前实现 | 包不住完整 agent 调用，看不到 `onActing` 阶段的事件 |
| **`onAgent`** | 包整个 agent 调用，可见全部事件流 | 比当前实现复杂一些 |

### 数据流

```
onAgent 进入
  ├── 1. 从 input.messages() 中取最后一条 user 消息 → 落库（message_type=text, role=user）
  ├── 2. next.apply(input).flatMap(event → handleEvent(event))
  │       ├── START 事件   → EventBuffer.start(event)
  │       ├── DELTA 事件   → EventBuffer.append(event)
  │       ├── END 事件     → EventBuffer.flush(event) → persist*(buffered)
  │       └── 即时事件     → persistInstant(event)
  └── doFinally → buf.clear()
```

### EventBuffer 设计

按 **block key** 累积 DELTA。同一个 reply 可能有多个 block（多轮思考 + 多次工具），不能用单一 buffer。

block key 规则：
- `TEXT_BLOCK_*` → `"text:" + replyId`
- `THINKING_BLOCK_*` → `"thinking:" + replyId`
- `TOOL_CALL_*` → `"tool_call:" + toolCallId`
- `TOOL_RESULT_*` → `"tool_result:" + toolCallId`
- `DATA_BLOCK_*` → `"data:" + replyId + ":" + (block index 或 dataId，若事件提供)`

```java
class EventBuffer {
    private final Map<String, BlockState> blocks = new HashMap<>();

    void start(String key, AgentEvent e)     { blocks.put(key, new BlockState(e)); }
    void append(String key, String delta)    { blocks.get(key).append(delta); }
    BlockState flush(String key)             { return blocks.remove(key); }
    void clear()                             { blocks.clear(); }
}
```

`EventBuffer` 是 `onAgent` 方法栈内的局部变量（per-agent-call），不需要并发同步。

### 事件 → 落库映射

| 事件 | message_type | role | content | content_type | extra |
|------|--------------|------|---------|--------------|-------|
| 首次 input.messages 的 user | `text` | `user` | 文本 | `text` | NULL |
| `TEXT_BLOCK_END` | `text` | `assistant` | 累积文本 | `text` | NULL |
| `THINKING_BLOCK_END` | `thinking` | `assistant` | 累积思维 | `text` | NULL |
| `TOOL_CALL_END` | `tool_call` | `assistant` | 累积参数 JSON | `json` | NULL |
| `TOOL_RESULT_END` | `tool_result` | `tool` | 累积结果文本 | `text` | `{state: ...}` |
| `TOOL_RESULT_DATA_DELTA` | `tool_result` | `tool` | 空 | 媒体类型 | `{url: ...}` |
| `DATA_BLOCK_END` | `data` | `assistant` | 空或描述 | 媒体类型 | `{url: ...}` |
| `REQUIRE_USER_CONFIRM` | `require_confirm` | `system` | 提示文本 | `text` | `{timeoutSec, reason}` |
| `USER_CONFIRM_RESULT` | `confirm_result` | `user` | 空 | `text` | `{approved}` |
| `REQUIRE_EXTERNAL_EXECUTION` | `require_external` | `system` | 提示文本 | `text` | NULL |
| `EXTERNAL_EXECUTION_RESULT` | `external_result` | `system` | 结果文本 | `text` | NULL |
| `EXCEED_MAX_ITERS` | `exceed_iters` | `system` | 错误描述 | `text` | `{maxIters}` |
| `REQUEST_STOP` | `request_stop` | `system` | 空 | `text` | NULL |
| `SUBAGENT_EXPOSED` | `subagent_exposed` | `system` | 子 agent 名称 | `text` | `{agentName, agentId}` |

所有事件落库时填 `reply_id`（来自事件 `getReplyId()`）和 `tool_call_id`（如适用）。

---

## 5. 实际落库示例

用户问 "做一个 AI 的 PPT"，Agent 思考 → 调 `web_search` → 调 `search_image` → 回复：

```
id | session_id | role      | message_type | reply_id | tool_call_id | tool_name  | content_type | content              | extra
---|------------|-----------|--------------|----------|--------------|------------|--------------|----------------------|-------------------------
1  | 100        | user      | text         | NULL     | NULL         | NULL       | text         | 做一个 AI 的 PPT     | NULL
2  | 100        | assistant | thinking     | reply_1  | NULL         | NULL       | text         | 我先搜索一下最新资讯 | NULL
3  | 100        | assistant | tool_call    | reply_1  | call_x       | web_search | json         | {"query":"AI 2026"}  | NULL
4  | 100        | tool      | tool_result  | reply_1  | call_x       | web_search | text         | 搜索结果摘要...      | {"state":"success"}
5  | 100        | assistant | tool_call    | reply_1  | call_y       | search_img | json         | {"keyword":"AI"}     | NULL
6  | 100        | tool      | tool_result  | reply_1  | call_y       | search_img | text         | 图片 URL 列表        | {"state":"success"}
7  | 100        | tool      | tool_result  | reply_1  | call_y       | search_img | image/png    | (空)                 | {"url":"https://..."}
8  | 100        | assistant | text         | reply_1  | NULL         | NULL       | text         | PPT 大纲如下...      | NULL
```

前端 `SELECT * FROM chat_session_message WHERE session_id=100 ORDER BY id` 即可还原完整对话视图。

---

## 6. 失败与一致性

### 一致性边界

`chat_session_message`（middleware 写）与 `ppt_session`（框架写）**不在同一事务**，可能存在：
- middleware 写成功，框架 state 写失败 → 表里有事件流水，但 Agent 内存状态丢
- 框架写成功，middleware 某条事件写失败 → 表里事件流水残缺，但 Agent 状态完整

**约定**：`chat_session_message` 仅用于前端 UI 回放，缺一两条不影响 Agent 工作。每条落库异常单独 `try/catch + log.warn` 容错，绝不阻断事件流。

### 中断处理

`doFinally` 清理 `EventBuffer`。如果 Agent 中途异常退出，尚未 `END` 的 block 直接丢弃（不落库），这是有意为之 —— 半截内容存了也不能用。

### 并发

`EventBuffer` 是 per-Agent-call 的局部变量（在 `onAgent` 方法栈内创建），不需要跨调用同步。

---

## 7. 改动清单

### 数据库

新建 migration SQL（手动执行或 Flyway 管理）：
```sql
-- chat_session_message_v2.sql
ALTER TABLE chat_session_message ...  -- 见 §3
```

### Java 实体

`ChatSessionMessage.java`：新增 6 个字段（`message_type / reply_id / tool_call_id / tool_name / content_type / extra`），保留 lombok `@Data + @Builder`。

`extra` 字段用 `String` 存 JSON 字符串，避免 Hibernate 与 MySQL JSON 类型映射的复杂性 —— 业务层用 `JsonUtils` 序列化/反序列化。

### Middleware

`ChatPersistenceMiddleware.java` 重写：
- 钩子从 `onReasoning` 切到 `onAgent`
- 新增内部类 `EventBuffer`
- 新增事件分发方法 `handleEvent(...)`
- 保留 user 消息保存逻辑（迁移到 `onAgent` 入口）

### Service

`ChatAgentStateStore.java` 的 `addMessage` 扩展签名（或重载），接受完整事件字段。原 `addMessage(sessionId, role, content)` 保留供旧调用方使用。

新增方法：
```java
ChatSessionMessage addEventMessage(Long sessionId, String role, String messageType,
                                    String replyId, String toolCallId, String toolName,
                                    String content, String contentType, String extraJson);
```

### Controller

`ChatSessionController.listMessages` 返回类型不变（`List<ChatSessionMessage>`），新字段自动序列化给前端。

---

## 8. 不在本设计内的事

- 不删除/修改 `ChatAgentStateStore` 已有的前端 chat CRUD 接口
- 不动 `PptSessionStateStore`（Agent 运行时状态层）
- 不引入定时聚合任务、不引入新表
- 不重写 `SessionAwareAgentProxy`

---

## 9. 验收

1. 启动后跑一次完整对话（含工具调用），用 SQL 查 `chat_session_message`，应能看到 §5 样例那样的多事件行
2. 前端按 `session_id` 拉取消息列表，能按时间序还原对话
3. Middleware 异常落库时 log.warn，但不影响 Agent 正常返回
