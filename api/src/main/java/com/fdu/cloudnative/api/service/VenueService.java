package com.fdu.cloudnative.api.service;

import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.vo.VenueVO;

import java.util.List;

public interface VenueService {
    List<VenueVO> getVenues();

    VenueVO getVenueById(Long venueId);
}
