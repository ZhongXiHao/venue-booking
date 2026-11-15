-- 报告表。官方镜像只在数据目录为空时执行 db/init，重启不会再次建表。
-- 库由 MYSQL_DATABASE 创建，这里不 CREATE DATABASE。
-- 列名供 MyBatis 下划线转驼峰；id 为 BIGINT，接口层输出字符串。
-- schedule_key 只给定时任务填写，手工任务为 NULL。MySQL 唯一索引把 NULL 视为互不相同，因此多条手工记录可以并存。
-- report_results.venue_name 是生成时的场地名称快照，venue_id 不外键到 venues。

SET NAMES utf8mb4;

CREATE TABLE report_runs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    trigger_type  ENUM('MANUAL', 'SCHEDULED') NOT NULL,
    range_start   DATE          NOT NULL,
    range_end     DATE          NOT NULL,
    status        ENUM('PENDING', 'RUNNING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
    job_name      VARCHAR(253)  NULL,
    schedule_key  VARCHAR(64)   NULL,
    attempt       INT           NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at    DATETIME      NOT NULL DEFAULT (UTC_TIMESTAMP()),
    started_at    DATETIME      NULL,
    finished_at   DATETIME      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_job_name (job_name),
    UNIQUE KEY uk_report_runs_schedule_key (schedule_key),
    CONSTRAINT ck_report_runs_range CHECK (range_start <= range_end),
    -- 列表默认按创建时间倒序；状态、触发方式是可单独使用的筛选。
    KEY idx_report_runs_created (created_at DESC),
    KEY idx_report_runs_status_created (status, created_at DESC),
    KEY idx_report_runs_trigger_created (trigger_type, created_at DESC),
    KEY idx_report_runs_list (status, trigger_type, created_at DESC),
    KEY idx_report_runs_range (range_start, range_end)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_results (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            BIGINT       NOT NULL,
    venue_id          BIGINT       NOT NULL,
    venue_name        VARCHAR(100) NOT NULL,
    reservation_count INT          NOT NULL,
    total_minutes     INT          NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_result_run_venue (run_id, venue_id),
    CONSTRAINT fk_result_run FOREIGN KEY (run_id) REFERENCES report_runs (id) ON DELETE CASCADE,
    CONSTRAINT chk_result_nonneg CHECK (reservation_count >= 0 AND total_minutes >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
