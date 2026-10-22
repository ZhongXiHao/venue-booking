package com.fdu.cloudnative.api.controller;

import com.fdu.cloudnative.api.common.Result;
import com.fdu.cloudnative.api.dto.CreateVenueRequest;
import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.ErrorCode;
import com.fdu.cloudnative.api.service.VenueService;
import com.fdu.cloudnative.api.vo.VenueVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/venues")
public class VenueController {
    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    public ResponseEntity<Result<List<VenueVO>>> getVenues() {
        List<VenueVO> venues = venueService.getVenues();
        return ResponseEntity.ok(Result.success(venues));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<VenueVO>> getVenueById(@PathVariable Long id) {
        VenueVO venue = venueService.getVenueById(id);
        if (venue == null) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        }
        return ResponseEntity.ok(Result.success(venue));
    }

    @PostMapping
    public ResponseEntity<Result<VenueVO>> createVenue(@RequestBody @Validated CreateVenueRequest createVenueRequest) {
        VenueVO createdVenue = venueService.createVenue(createVenueRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("场地创建成功", createdVenue));
    }

}
