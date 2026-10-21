package com.fdu.cloudnative.api.controller;

import com.fdu.cloudnative.api.common.Result;
import com.fdu.cloudnative.api.dto.CreateReservationRequest;
import com.fdu.cloudnative.api.service.ReservationService;
import com.fdu.cloudnative.api.vo.ReservationVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<Result<ReservationVO>> createReservation(@RequestBody @Validated CreateReservationRequest createReservationRequest) {
        ReservationVO reservationVO = reservationService.create(createReservationRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("预约创建成功", reservationVO));
    }
}
