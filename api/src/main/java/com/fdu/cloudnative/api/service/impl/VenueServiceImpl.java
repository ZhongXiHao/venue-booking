package com.fdu.cloudnative.api.service.impl;

import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.mapper.VenueMapper;
import com.fdu.cloudnative.api.service.VenueService;
import com.fdu.cloudnative.api.vo.VenueVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VenueServiceImpl implements VenueService {
    private final VenueMapper venueMapper;

    public VenueServiceImpl(VenueMapper venueMapper) {
        this.venueMapper = venueMapper;
    }

    @Override
    public List<VenueVO> getVenues() {
        return venueMapper.getVenues().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public VenueVO getVenueById(Long venueId) {
        Venue venue = venueMapper.getVenueById(venueId);
        // 场地不存在时返回 null，由调用方映射为 404 VENUE_NOT_FOUND
        return venue == null ? null : toVO(venue);
    }

    private VenueVO toVO(Venue venue) {
        VenueVO venueVO = new VenueVO();
        venueVO.setId(String.valueOf(venue.getId()));
        venueVO.setName(venue.getName());
        venueVO.setLocation(venue.getLocation());
        venueVO.setCapacity(venue.getCapacity());
        venueVO.setOpenTime(venue.getOpenTime());
        venueVO.setCloseTime(venue.getCloseTime());
        venueVO.setStatus(venue.getStatus().getApiValue());
        return venueVO;
    }
}
