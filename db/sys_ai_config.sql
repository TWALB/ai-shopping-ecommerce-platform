-- AI 提示词与运行参数配置表
-- 说明：后端启动时会自动执行同样的 CREATE TABLE IF NOT EXISTS（见 AiConfigService#ensureSchema），
-- 本文件仅作为 DDL 存档，便于手工建库或论文附录引用。

CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key`    VARCHAR(64)  NOT NULL COMMENT '配置键，如 chat.system_prompt',
  `config_name`   VARCHAR(64)  NOT NULL COMMENT '展示名称',
  `config_value`  TEXT         NOT NULL COMMENT '当前值，提示词支持 {{变量}} 占位符',
  `default_value` TEXT         NOT NULL COMMENT '内置默认值，用于一键恢复',
  `description`   VARCHAR(255) DEFAULT NULL COMMENT '说明与可用变量',
  `version`       INT          NOT NULL DEFAULT 1 COMMENT '每保存一次自增，用于前端展示与后续回滚',
  `updated_by`    BIGINT       DEFAULT NULL COMMENT '最后修改人（管理员 ID）',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 提示词与运行参数配置';
