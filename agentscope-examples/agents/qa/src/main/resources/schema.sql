-- 会话主表：每个对话一条记录，作为 conversation_message / ai_conversation_shared 的父表。
CREATE TABLE IF NOT EXISTS ai_conversation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             VARCHAR(64)  NOT NULL COMMENT '用户 id',
    session_id          VARCHAR(128) NOT NULL COMMENT '会话 id（前端 threadId）',
    title               VARCHAR(128) NULL     COMMENT '会话标题（自动取首条 user 消息前 24 字）',
    pinned              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    current_message_id  BIGINT       NULL     COMMENT '当前叶子消息 id（用于树形回溯）',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话主表';

-- 对话消息存档表：完整轨迹落库（user / assistant / tool），依赖 ai_conversation。
-- 树形结构：parent_id 建立归属，path（物化路径）提供排序，depth 标记层级。
CREATE TABLE IF NOT EXISTS ai_conversation_message (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       VARCHAR(64)   NOT NULL COMMENT '鉴权用户 id',
    session_id    VARCHAR(128)  NOT NULL COMMENT '会话 id（前端 threadId）',
    run_id        VARCHAR(128)  NULL     COMMENT '本轮运行 id',
    msg_id        VARCHAR(64)   NULL     COMMENT 'agentscope Msg.id',
    role          VARCHAR(16)   NOT NULL COMMENT 'user / assistant / tool',
    message_type  VARCHAR(32)   NOT NULL COMMENT 'text / thinking / tool_use / tool_result',
    content       LONGTEXT      NULL     COMMENT '纯文本内容（便于查询/列表预览）',
    content_json  LONGTEXT      NULL     COMMENT '完整 Msg 序列化 JSON（保真存档，可还原 content blocks）',
    tool_name     VARCHAR(128)  NULL     COMMENT '工具名（tool_use/tool_result 时）',
    tool_call_id  VARCHAR(128)  NULL     COMMENT '工具调用关联 id',
    parent_id     BIGINT        NULL     COMMENT '父消息 id（NULL=根节点）',
    path          VARCHAR(2048) NULL     COMMENT '物化路径，如 /10/11/',
    depth         INT           NULL     COMMENT '深度，根=0',
    seq           INT           NULL     COMMENT '轮内顺序',
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_session_time (user_id, session_id, created_at),
    KEY idx_parent (parent_id),
    KEY idx_path (path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息完整轨迹存档（树形结构）';

-- citations 列幂等迁移：ddl-auto=update 不自动加列，用 information_schema 做存在性判断。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_conversation_message'
    AND COLUMN_NAME = 'citations'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE ai_conversation_message ADD COLUMN citations JSON NULL COMMENT ''引用来源 JSON''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- parent_id / path / depth 列幂等迁移（树形结构）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_conversation_message'
    AND COLUMN_NAME = 'parent_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE ai_conversation_message ADD COLUMN parent_id BIGINT NULL COMMENT ''父消息 id（NULL=根节点）'',
   ADD COLUMN path VARCHAR(2048) NULL COMMENT ''物化路径'',
   ADD COLUMN depth INT NULL COMMENT ''深度，根=0'',
   ADD KEY idx_parent (parent_id),
   ADD KEY idx_path (path(255))',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- current_message_id 列幂等迁移（会话主表）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ai_conversation'
    AND COLUMN_NAME = 'current_message_id'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE ai_conversation ADD COLUMN current_message_id BIGINT NULL COMMENT ''当前叶子消息 id''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 会话分享记录表：一个会话可被分享一次，生成唯一 token 供公开只读访问。
CREATE TABLE IF NOT EXISTS ai_conversation_shared (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    token         VARCHAR(64)  NOT NULL COMMENT '分享唯一标识（UUID）',
    user_id       VARCHAR(64)  NOT NULL COMMENT '分享者用户 id',
    session_id    VARCHAR(128) NOT NULL COMMENT '被分享的会话 id',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_session (user_id, session_id),
    UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话分享记录';
