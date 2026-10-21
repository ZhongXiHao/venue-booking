package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateReservationRequest;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.vo.ReservationVO;

import java.time.LocalDate;

public interface ReservationService {
    ReservationVO create(CreateReservationRequest createReservationRequest);

    // 预约不存在时抛 404 RESERVATION_NOT_FOUND
    ReservationVO getById(Long id);

    // venueId、reservationDate、status 为可选筛选条件；page、pageSize 不合法时抛 400 VALIDATION_ERROR
    PageResult<ReservationVO> list(int page, int pageSize, Long venueId, LocalDate reservationDate, ReservationStatus status);

    // 预约不存在 404；已取消 409 ALREADY_CANCELLED；返回取消后的预约
    ReservationVO cancel(Long id);
}
