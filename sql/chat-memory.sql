CREATE DATABASE IF NOT EXISTS ai_helper
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;


-- 1. 会话主表 (管理会话元数据)
CREATE TABLE IF NOT EXISTS `session` (
     `id` VARCHAR(64) PRIMARY KEY COMMENT '会话ID (UUID或自定义格式)',
     `title` VARCHAR(100) DEFAULT '新对话' COMMENT '会话标题',
     `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
     INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话主表';

-- 2. 对话流水记录表 (按“条”存，user/assistant 分两条；turn_no 归并同一轮)
CREATE TABLE IF NOT EXISTS `chat_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `turn_no` BIGINT NOT NULL COMMENT '轮次号（同一轮 user/assistant 一致）',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: user, assistant, system, tool',
    `content` TEXT NOT NULL COMMENT '对话内容',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_session_id_id` (`session_id`, `id`),
    INDEX `idx_session_id_turn_no` (`session_id`, `turn_no`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话流水记录表';

-- 3. 会话摘要状态表 (摘要内容 + 双游标)
CREATE TABLE IF NOT EXISTS `chat_summary` (
    `session_id` VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    `summary_content` TEXT NOT NULL COMMENT '最新的滚动摘要内容',
    `latest_history_id` BIGINT NOT NULL DEFAULT 0 COMMENT '会话最新历史游标（每轮写历史后推进）',
    `last_summarized_history_id` BIGINT NOT NULL DEFAULT 0 COMMENT '摘要覆盖游标（摘要成功后推进）',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话摘要状态表';




-- 1. 创建一个名为 train-member 的用户，设置密码（请把 '你的密码' 换成实际密码）
-- '%' 表示允许该用户从任何 IP 地址连接，如果是本机开发也可以用 'localhost'
CREATE USER 'ai-helper'@'%' IDENTIFIED BY '123456';

-- 2. 给该用户授予对 train_member 数据库的所有权限
-- 注意：这里数据库名建议统一使用下划线 train_member，如果你坚持用 train-member，请加反引号
GRANT ALL PRIVILEGES ON `ai_helper`.* TO 'ai-helper'@'%';

-- 3. 刷新权限，使其立即生效
FLUSH PRIVILEGES;
