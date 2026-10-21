package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.dto.CreateReservationRequest;
import com.fdu.cloudnative.api.vo.ReservationVO;

public interface ReservationService {
    ReservationVO create(CreateReservationRequest createReservationRequest);
}
