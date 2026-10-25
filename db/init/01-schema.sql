-- 建表脚本。MySQL 官方镜像只在数据卷第一次初始化时执行 db/init。
-- 库由 MYSQL_DATABASE 创建，这里不 CREATE DATABASE。
-- 列名供 MyBatis 下划线转驼峰。预约里的场地名不单独存列，查询时 JOIN venues。

SET NAMES utf8mb4;

CREATE TABLE venues (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(200) NOT NULL,
    capacity   INT          NOT NULL,
    open_time  TIME         NOT NULL,
    close_time TIME         NOT NULL,
    status     ENUM('OPEN', 'DISABLED') NOT NULL DEFAULT 'OPEN',
    created_at DATETIME     NOT NULL DEFAULT (UTC_TIMESTAMP()),
    PRIMARY KEY (id),
    UNIQUE KEY uk_venues_name (name),
    CONSTRAINT ck_venues_capacity CHECK (capacity > 0),
    CONSTRAINT ck_venues_hours CHECK (open_time < close_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservations (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    venue_id         BIGINT       NOT NULL,
    booker_name      VARCHAR(100) NOT NULL,
    reservation_date DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    purpose          VARCHAR(500) NOT NULL,
    status           ENUM('ACTIVE', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME     NOT NULL DEFAULT (UTC_TIMESTAMP()),
    cancelled_at     DATETIME     NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservations_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_reservations_span CHECK (start_time < end_time),
    KEY idx_reservations_conflict (venue_id, reservation_date, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
