package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.entity.Reservation;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.mapper.ReservationMapper;
import com.fdu.cloudnative.api.vo.ReservationVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional   // 每个测试结束后自动回滚，不会污染数据库
class ReservationTests {

    @Autowired
    private ReservationMapper reservationMapper;

    private static final LocalDate DATE = LocalDate.of(2030, 1, 15);

    private Reservation newReservation(String start, String end) {
        return newReservation(1L, start, end);
    }

    private Reservation newReservation(Long venueId, String start, String end) {
        Reservation r = new Reservation();
        r.setVenueId(venueId);
        r.setBookerName("测试小组");
        r.setReservationDate(DATE);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setPurpose("单元测试");
        r.setStatus(ReservationStatus.ACTIVE);
        return r;
    }

    private int conflicts(String start, String end) {
        return reservationMapper.countConflicts(1L, DATE,
                LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void lockVenue_existingAndMissing() {
        assertNotNull(reservationMapper.lockVenueByIdForUpdate(1L));
        assertNull(reservationMapper.lockVenueByIdForUpdate(99999L));
    }

    @Test
    void insert_generatesIdAndJoinsVenueName() {
        Reservation r = newReservation("09:00:00", "10:00:00");
        assertEquals(1, reservationMapper.insertReservation(r));
        assertNotNull(r.getId());                      // useGeneratedKeys 回填了 id

        ReservationVO vo = reservationMapper.getReservationById(r.getId());
        assertNotNull(vo);
        assertEquals("第一会议室", vo.getVenueName());   // 取决于种子数据
        assertEquals(ReservationStatus.ACTIVE, vo.getStatus());
    }

    @Test
    void countConflicts_boundaries() {
        reservationMapper.insertReservation(newReservation("09:00:00", "10:00:00"));

        assertEquals(1, conflicts("09:30:00", "10:30:00")); // 部分重叠
        assertEquals(1, conflicts("08:00:00", "11:00:00")); // 把已有预约包住
        assertEquals(1, conflicts("09:00:00", "10:00:00")); // 完全相同
        assertEquals(0, conflicts("10:00:00", "11:00:00")); // 首尾相接，不冲突
        assertEquals(0, conflicts("08:00:00", "09:00:00")); // 首尾相接，不冲突
    }

    @Test
    void cancelledReservation_doesNotConflict() {
        Reservation r = newReservation("09:00:00", "10:00:00");
        reservationMapper.insertReservation(r);

        assertEquals(1, reservationMapper.cancelReservation(r.getId()));   // 取消成功
        assertEquals(0, conflicts("09:00:00", "10:00:00"));     // 不再占用时段
        assertEquals(0, reservationMapper.cancelReservation(r.getId()));   // 重复取消，影响行数为 0
    }

    @Test
    void selectPageAndCount_filtersOrderAndPaging() {
        // 用 DATE 把测试数据和库里已有的预约隔离开
        Reservation r1 = newReservation(1L, "09:00:00", "10:00:00");
        Reservation r2 = newReservation(1L, "10:00:00", "11:00:00");
        Reservation r3 = newReservation(2L, "09:00:00", "10:00:00");
        reservationMapper.insertReservation(r1);
        reservationMapper.insertReservation(r2);
        reservationMapper.insertReservation(r3);
        reservationMapper.cancelReservation(r2.getId());

        // 不筛选场地和状态：3 条
        assertEquals(3, reservationMapper.count(null, DATE, null));
        // 按场地、按状态、两者叠加
        assertEquals(2, reservationMapper.count(1L, DATE, null));
        assertEquals(2, reservationMapper.count(null, DATE, "ACTIVE"));
        assertEquals(1, reservationMapper.count(1L, DATE, "CANCELLED"));
        // 其他日期没有数据
        assertEquals(0, reservationMapper.count(null, DATE.plusDays(1), null));

        // 排序：日期、开始时间、id；r1 和 r3 同时开始，id 小的在前
        List<ReservationVO> all = reservationMapper.selectPage(null, DATE, null, 0, 10);
        assertEquals(List.of(r1.getId(), r3.getId(), r2.getId()),
                all.stream().map(ReservationVO::getId).toList());
        assertEquals("第一会议室", all.get(0).getVenueName());
        assertEquals("学术报告厅", all.get(1).getVenueName());

        // 分页：每页 1 条，第 2 页应是排序后的第 2 条
        List<ReservationVO> page2 = reservationMapper.selectPage(null, DATE, null, 1, 1);
        assertEquals(1, page2.size());
        assertEquals(r3.getId(), page2.get(0).getId());
        // 超出范围的页为空
        assertTrue(reservationMapper.selectPage(null, DATE, null, 3, 1).isEmpty());
    }
}