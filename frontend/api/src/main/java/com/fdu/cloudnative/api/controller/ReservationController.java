package com.fdu.cloudnative.api.controller;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.common.Result;
import com.fdu.cloudnative.api.dto.CreateReservationRequest;
import com.fdu.cloudnative.api.entity.ReservationStatus;
import com.fdu.cloudnative.api.service.ReservationService;
import com.fdu.cloudnative.api.vo.ReservationVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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

    @GetMapping
    public ResponseEntity<Result<PageResult<ReservationVO>>> listReservations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long venueId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate reservationDate,
            @RequestParam(required = false) ReservationStatus status
    ) {
        PageResult<ReservationVO> list = reservationService.list(page, pageSize, venueId, reservationDate, status);
        return ResponseEntity.ok(Result.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<ReservationVO>> getReservationById(@PathVariable Long id) {
        ReservationVO reservationVO = reservationService.getById(id);
        return ResponseEntity.ok(Result.success(reservationVO));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Result<ReservationVO>> cancelReservation(@PathVariable Long id) {
        ReservationVO reservationVO = reservationService.cancel(id);
        return ResponseEntity.ok(Result.success("预约已取消", reservationVO));
    }
}
