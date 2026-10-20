package com.fdu.cloudnative.api.mapper;

import com.fdu.cloudnative.api.entity.Venue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VenueMapper {
    @Select("SELECT * FROM venues")
    List<Venue> getVenues();
    @Select("SELECT * FROM venues WHERE id = #{venueId}")
    Venue getVenueById(Long venueId);
}
