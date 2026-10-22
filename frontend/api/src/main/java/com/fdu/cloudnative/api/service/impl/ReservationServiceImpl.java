package com.fdu.cloudnative.api.service.impl;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateReservationRequest;
import com.fdu.cloudnative.api.entity.Reservation;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.entity.VenueStatus;
import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.ErrorCode;
import com.fdu.cloudnative.api.exception.FieldErrorItem;
import com.fdu.cloudnative.api.mapper.ReservationMapper;
import com.fdu.cloudnative.api.service.ReservationService;
import com.fdu.cloudnative.api.vo.ReservationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationServiceImpl implements ReservationService {
    // 契约规定的业务时区，判断"预约已开始"时用它取当前时间
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationMapper reservationMapper;

    public ReservationServiceImpl(ReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    @Override
    @Transactional
    public ReservationVO create(CreateReservationRequest createReservationRequest) {
        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        LocalDate reservationDate = createReservationRequest.getReservationDate();
        LocalTime startTime = createReservationRequest.getStartTime();
        LocalTime endTime = createReservationRequest.getEndTime();

        if (!endTime.isAfter(startTime)) {
            fieldErrors.add(new FieldErrorItem("endTime", "结束时间必须晚于开始时间"));
        }
        //均为 30 分钟整点
        if (!isHalfHourSharp(startTime)) {
            fieldErrors.add(new FieldErrorItem("startTime", "开始时间必须为 30 分钟整点"));
        }
        if (!isHalfHourSharp(endTime)) {
            fieldErrors.add(new FieldErrorItem("endTime", "结束时间必须为 30 分钟整点"));
        }
        // 预约已开始（按业务时区取当前时间，早于现在就是已开始）
        if (reservationDate.atTime(startTime).isBefore(LocalDateTime.now(BUSINESS_ZONE))) {
            fieldErrors.add(new FieldErrorItem("reservationDate", "预约开始时间在当前时间之前"));
        }
        // 返回错误信息
        if (!fieldErrors.isEmpty()) {
            throw BusinessException.validation(fieldErrors);
        }

        // 加锁、查场地、并且插入
        // 注意：锁场地行必须是事务里的第一次查询。MySQL 默认的可重复读在第一次普通 SELECT 时才建立快照，
        // 先加锁再查冲突，后到的请求才能在等锁结束后看到先到请求已提交的预约
        Long venueId = createReservationRequest.getVenueId();
        Venue venue = reservationMapper.lockVenueByIdForUpdate(venueId);
        if (venue == null) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        }

        if (VenueStatus.DISABLED.equals(venue.getStatus())) {
            throw new BusinessException(ErrorCode.VENUE_CLOSED);
        }

        // 检查是否在开放时间内
        LocalTime openTime = venue.getOpenTime();
        LocalTime closeTime = venue.getCloseTime();
        if (startTime.isBefore(openTime) || endTime.isAfter(closeTime)) {
            throw BusinessException.outOfOpeningHours(openTime, closeTime);
        }

        // 检查时间段是否冲突
        int countConflicts = reservationMapper.countConflicts(venueId, reservationDate, startTime, endTime);
        if (countConflicts > 0) {
            throw new BusinessException(ErrorCode.RESERVATION_CONFLICT);
        }

        Reservation reservation = new Reservation();
        BeanUtils.copyProperties(createReservationRequest, reservation);

        reservationMapper.insertReservation(reservation);
        return reservationMapper.getReservationById(reservation.getId());
    }

    @Override
    public ReservationVO getById(Long id) {
        ReservationVO reservation = reservationMapper.getReservationById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    @Override
    public PageResult<ReservationVO> list(int page, int pageSize, Long venueId,
                                          LocalDate reservationDate, ReservationStatus status) {
        List<FieldErrorItem> fieldErrors = new ArrayList<>();
        if (page < 1) {
            fieldErrors.add(new FieldErrorItem("page", "页码必须大于等于 1"));
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            fieldErrors.add(new FieldErrorItem("pageSize", "每页条数必须在 1 到 " + MAX_PAGE_SIZE + " 之间"));
        }
        if (!fieldErrors.isEmpty()) {
            throw BusinessException.validation(fieldErrors);
        }

        String statusName = status == null ? null : status.name();
        long total = reservationMapper.count(venueId, reservationDate, statusName);
        // 用 long 计算偏移量，避免 page 很大时 int 溢出
        long offset = (long) (page - 1) * pageSize;
        List<ReservationVO> items = reservationMapper.selectPage(venueId, reservationDate, statusName, offset, pageSize);
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    @Transactional
    public ReservationVO cancel(Long id) {
        ReservationVO reservation = getById(id);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED);
        }
        // 影响行数为 0 说明被并发的取消请求抢先了，同样按重复取消处理
        if (reservationMapper.cancelReservation(id) == 0) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED);
        }
        return reservationMapper.getReservationById(id);
    }

    private boolean isHalfHourSharp(LocalTime time) {
        return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
