package com.fdu.cloudnative.api.service.impl;

import com.fdu.cloudnative.api.common.PageResult;
import com.fdu.cloudnative.api.dto.CreateVenueRequest;
import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.entity.VenueStatus;
import com.fdu.cloudnative.api.exception.BusinessException;
import com.fdu.cloudnative.api.exception.ErrorCode;
import com.fdu.cloudnative.api.exception.FieldErrorItem;
import com.fdu.cloudnative.api.mapper.VenueMapper;
import com.fdu.cloudnative.api.service.VenueService;
import com.fdu.cloudnative.api.vo.VenueVO;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VenueServiceImpl implements VenueService {
    private final VenueMapper venueMapper;

    public VenueServiceImpl(VenueMapper venueMapper) {
        this.venueMapper = venueMapper;
    }

    @Override
    public PageResult<VenueVO> getVenues(int page, int pageSize) {
        PageResult.checkParams(page, pageSize);

        long total = venueMapper.countVenues();
        // 用 long 计算偏移量，避免 page 很大时 int 溢出
        long offset = (long) (page - 1) * pageSize;
        List<VenueVO> items = venueMapper.selectVenuePage(offset, pageSize).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(items, total, page, pageSize);
    }

    @Override
    public VenueVO getVenueById(Long venueId) {
        Venue venue = venueMapper.getVenueById(venueId);
        // 场地不存在时返回 null，由调用方映射为 404 VENUE_NOT_FOUND
        return venue == null ? null : toVO(venue);
    }

    @Override
    public VenueVO createVenue(CreateVenueRequest createVenueRequest) {
        LocalTime openTime = createVenueRequest.getOpenTime();
        LocalTime closeTime = createVenueRequest.getCloseTime();
        List<FieldErrorItem> fieldErrors = new ArrayList<>();
        // 严格小于，与数据库 CHECK (open_time < close_time) 一致
        if (!closeTime.isAfter(openTime)) {
            fieldErrors.add(new FieldErrorItem("closeTime", "关闭时间必须晚于开放时间"));
        }
        //均为 30 分钟整点
        if (!isHalfHourSharp(openTime)) {
            fieldErrors.add(new FieldErrorItem("openTime", "开放时间必须为 30 分钟整点"));
        }
        if (!isHalfHourSharp(closeTime)) {
            fieldErrors.add(new FieldErrorItem("closeTime", "关闭时间必须为 30 分钟整点"));
        }
        if (!fieldErrors.isEmpty()) {
            throw BusinessException.validation(fieldErrors);
        }

        Venue venue = new Venue();
        BeanUtils.copyProperties(createVenueRequest, venue);
        // status 可省略，默认 OPEN（显式插入 NULL 不会触发数据库默认值）
        if (venue.getStatus() == null) {
            venue.setStatus(VenueStatus.OPEN);
        }

        try {
            venueMapper.insertVenue(venue);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.VENUE_NAME_DUPLICATE);
        }

        return toVO(venue);
    }

    private boolean isHalfHourSharp(LocalTime time) {
        return time.getMinute() % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private VenueVO toVO(Venue venue) {
        VenueVO venueVO = new VenueVO();
        venueVO.setId(String.valueOf(venue.getId()));
        venueVO.setName(venue.getName());
        venueVO.setLocation(venue.getLocation());
        venueVO.setCapacity(venue.getCapacity());
        venueVO.setOpenTime(venue.getOpenTime());
        venueVO.setCloseTime(venue.getCloseTime());
        venueVO.setStatus(venue.getStatus().name());
        return venueVO;
    }
}
