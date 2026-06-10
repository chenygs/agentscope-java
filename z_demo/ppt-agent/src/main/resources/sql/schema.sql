-- ============================================================
-- PPT Agent 数据库初始化脚本
-- 在 agent-scope-ppt 库中执行（数据库必须已存在）
-- ============================================================

-- ─────────── 用户表 ───────────
CREATE TABLE IF NOT EXISTS ppt_user (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username      VARCHAR(64) NOT NULL UNIQUE          COMMENT '登录用户名',
    password_hash VARCHAR(128) NOT NULL                COMMENT 'BCrypt 密码哈希',
    nickname      VARCHAR(64) DEFAULT NULL             COMMENT '显示昵称',
    avatar        VARCHAR(255) DEFAULT NULL            COMMENT '头像 URL',
    enabled       TINYINT(1)  NOT NULL DEFAULT 1       COMMENT '是否启用 (1=启用,0=禁用)',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at DATETIME    DEFAULT NULL             COMMENT '最近登录时间',
    PRIMARY KEY (id),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PPT Agent 用户表';

-- ─────────── 默认 admin 账号 ───────────
-- 用户名: admin    密码: 123456
-- 密码已用 BCrypt(strength=10) 哈希，与后端 BCryptPasswordEncoder 兼容
-- 如需重新生成哈希: 调用 new BCryptPasswordEncoder().encode("你的密码")
INSERT INTO ppt_user (username, password_hash, nickname)
VALUES ('admin', '$2a$10$OFl5B0.u0mnf3gMR2d9/ZOU2tKfCVXmCBmqgwfpfM0bCq3L1kQYoy', '管理员')
ON DUPLICATE KEY UPDATE username = username;

-- ─────────── 会话状态表（AgentScope Session 持久化） ───────────
-- 兼容官方 MysqlSession 表结构，由自实现的 JpaSession 读写
-- 复合主键: (session_id, state_key, item_index)
--   session_id  = SessionKey.toIdentifier(), 业务格式: "userId:conversationId"
--   state_key   = 状态键名 (如 memory_messages, agent_meta, etc.)
--   item_index  = 0 = 单值；列表元素 0,1,2,...；":_hash" 后缀键存列表哈希
--   state_data  = JSON 序列化数据
CREATE TABLE IF NOT EXISTS ppt_session (
    session_id  VARCHAR(255) NOT NULL                COMMENT '会话ID (userId:conversationId)',
    state_key   VARCHAR(255) NOT NULL                COMMENT '状态键名',
    item_index  INT          NOT NULL DEFAULT 0     COMMENT '列表项索引 (单值固定为0)',
    state_data  LONGTEXT     NOT NULL                COMMENT 'JSON 序列化的状态数据',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (session_id, state_key, item_index),
    INDEX idx_session_state (session_id, state_key, item_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentScope 会话状态表';


CREATE TABLE `chat_session` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
                                `user_id` bigint NOT NULL COMMENT '用户ID',
                                `agent_id` varchar(64) NOT NULL COMMENT '智能体标识（如 ppt-agent）',
                                `session_key` varchar(255) DEFAULT NULL COMMENT 'AgentScope SessionKey（如 userId:sessionId）',
                                `current_message_id` int DEFAULT NULL COMMENT '当前消息ID',
                                `title` varchar(255) DEFAULT NULL COMMENT '会话标题',
                                `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶',
                                `pin_time` datetime DEFAULT NULL COMMENT '置顶时间',
                                `created_at` datetime DEFAULT NULL COMMENT '创建时间',
                                `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                KEY `idx_session_key` (`session_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话表';

CREATE TABLE `chat_session_message` (
                                `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID',
                                `session_id` bigint NOT NULL COMMENT '会话ID',
                                `role` varchar(20) NOT NULL COMMENT '消息角色',
                                `content` text NOT NULL COMMENT '消息内容',
                                `parent_id` int DEFAULT NULL COMMENT '父消息ID',
                                `path` varchar(2048) DEFAULT NULL COMMENT '消息路径，格式如：/1/2/3/',
                                `depth` int DEFAULT NULL COMMENT '消息深度，从0开始，根消息深度为0',
                                `created_at` datetime DEFAULT NULL COMMENT '创建时间',
                                PRIMARY KEY (`id`) USING BTREE,
                                KEY `idx_session` (`session_id`) USING BTREE COMMENT '会话ID索引',
                                KEY `idx_parent` (`parent_id`) USING BTREE COMMENT '父消息ID索引',
                                KEY `idx_path` (`path`(255)) USING BTREE COMMENT '消息路径前缀索引'
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';