package com.fdu.cloudnative.api.mapper;

import com.fdu.cloudnative.api.entity.Reservation;
import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.vo.ReservationVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    @Select("SELECT * FROM venues WHERE id = #{venueId} FOR UPDATE")
    Venue lockVenueByIdForUpdate(Long venueId);

    @Select("SELECT COUNT(*) FROM reservations " +
            "WHERE venue_id=#{venueId} AND reservation_date=#{date} AND status='ACTIVE'" +
            "  AND start_time < #{end} AND end_time > #{start}")
    int countConflicts(@Param("venueId") Long venueId,
                       @Param("date") LocalDate date,
                       @Param("start") LocalTime start,
                       @Param("end") LocalTime end);

    @Insert("INSERT INTO reservations (venue_id, booker_name, reservation_date, start_time, end_time, purpose, status) " +
            "VALUES (#{venueId}, #{bookerName}, #{reservationDate}, #{startTime}, #{endTime}, #{purpose}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReservation(Reservation reservation);

    ReservationVO getReservationById(@Param("id") Long id);

    List<ReservationVO> selectPage(@Param("venueId") Long venueId,
                                   @Param("reservationDate") LocalDate reservationDate,
                                   @Param("status") String status,
                                   @Param("offset") long offset,
                                   @Param("pageSize") int pageSize);

    long count(@Param("venueId") Long venueId,
               @Param("reservationDate") LocalDate reservationDate,
               @Param("status") String status);

    // cancelled_at 按表注释存 UTC，不依赖 MySQL 会话时区
    @Update("UPDATE reservations SET status='CANCELLED', cancelled_at=UTC_TIMESTAMP() WHERE id=#{id} AND status='ACTIVE'")
    int cancelReservation(@Param("id") Long id);
}
