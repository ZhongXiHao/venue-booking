package com.fdu.cloudnative.api;

import com.fdu.cloudnative.api.entity.Venue;
import com.fdu.cloudnative.api.service.VenueService;
import com.fdu.cloudnative.api.service.impl.VenueServiceImpl;
import com.fdu.cloudnative.api.vo.VenueVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class VenueTests {

    @Autowired
    private VenueService venueService;

    @Test
    public void testGetVenueById() {
        VenueVO venueVO = venueService.getVenueById(1L);
        System.out.println(venueVO);
    }

    @Test
    public void testGetVenues() {
        System.out.println(venueService.getVenues(1, 10));
    }
}
