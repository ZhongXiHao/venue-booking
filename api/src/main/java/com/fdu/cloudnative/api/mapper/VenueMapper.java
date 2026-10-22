package com.fdu.cloudnative.api.mapper;

import com.fdu.cloudnative.api.entity.Venue;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VenueMapper {
    @Select("SELECT COUNT(*) FROM venues")
    long countVenues();

    // 按 id 排序，保证翻页顺序稳定
    @Select("SELECT * FROM venues ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Venue> selectVenuePage(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT * FROM venues WHERE id = #{venueId}")
    Venue getVenueById(Long venueId);

    // created_at 由数据库默认值生成（UTC）
    @Insert("INSERT INTO venues(name,location,capacity,open_time,close_time,status) " +
            "VALUES(#{name},#{location},#{capacity},#{openTime},#{closeTime},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertVenue(Venue venue);
}
