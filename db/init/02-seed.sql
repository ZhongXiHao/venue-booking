-- 验收种子：5 个场地，主键固定为 1～5。5 号「旧礼堂」停用。
-- 不插入预约，联调从空的 reservations 开始。

SET NAMES utf8mb4;

INSERT INTO venues (id, name, location, capacity, open_time, close_time, status)
VALUES
    (1, '第一会议室', '行政楼 3 楼 301', 20,  '08:00:00', '22:00:00', 'OPEN'),
    (2, '学术报告厅', '图书馆 B1',       200, '08:00:00', '21:00:00', 'OPEN'),
    (3, '篮球球馆',   '体育中心 1 楼',   40,  '09:00:00', '21:00:00', 'OPEN'),
    (4, '烹饪空间',   '食堂 3 楼',     10,  '09:00:00', '19:00:00', 'OPEN'),
    (5, '旧礼堂',     '东区 5 号楼',     300, '08:00:00', '18:00:00', 'DISABLED');

ALTER TABLE venues AUTO_INCREMENT = 6;
