package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateVenueRequest;
import com.fdu.cloudnative.api.vo.VenueVO;

public interface VenueService {
    PageResult<VenueVO> getVenues(int page, int pageSize);

    VenueVO getVenueById(Long venueId);

    VenueVO createVenue(CreateVenueRequest createVenueRequest);
}
