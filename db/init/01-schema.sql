SET NAMES utf8mb4;

-- 数据库本身由 MYSQL_DATABASE 环境变量创建，这里只建表

CREATE TABLE venues (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  location    VARCHAR(200) NOT NULL,
  capacity    INT          NOT NULL,
  open_time   TIME         NOT NULL COMMENT '每日开放开始时间',
  close_time  TIME         NOT NULL COMMENT '每日开放结束时间',
  status      ENUM('OPEN','DISABLED') NOT NULL DEFAULT 'OPEN',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_name (name),
  CONSTRAINT chk_venue_capacity CHECK (capacity > 0),
  CONSTRAINT chk_venue_hours    CHECK (open_time < close_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservations (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  venue_id         BIGINT       NOT NULL,
  booker_name      VARCHAR(100) NOT NULL COMMENT '预约人或预约组织',
  reservation_date DATE         NOT NULL,
  start_time       TIME         NOT NULL,
  end_time         TIME         NOT NULL,
  purpose          VARCHAR(500) NOT NULL,
  -- 取消采用逻辑取消：状态改为 CANCELLED，保留历史记录，冲突检查只比对 ACTIVE
  status           ENUM('ACTIVE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'UTC',
  cancelled_at     DATETIME     NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  -- 一个场地对应多个预约；场地有预约时不允许删除场地
  CONSTRAINT fk_reservation_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE RESTRICT,
  CONSTRAINT chk_reservation_time CHECK (start_time < end_time),
  -- 冲突检查的查询路径：同场地 + 同日期 + 有效状态
  KEY idx_conflict (venue_id, reservation_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
