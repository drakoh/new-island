package com.remicartier.newisland.controller;

import com.remicartier.newisland.service.ReservationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import static org.mockito.Mockito.when;

/**
 * Created by remicartier on 2020-07-19 2:17 p.m.
 */
@SuppressWarnings("unused")
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VacancyControllerTest {
    private final LocalDate now = LocalDate.now(Clock.systemUTC());

    @MockBean
    private ReservationService reservationService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getVacancy() {
        when(reservationService.getVacancy(null, null)).thenReturn(Collections.singletonList(now));

        ResponseEntity<LocalDateList> responseEntity = restTemplate.getForEntity("/vacancy", LocalDateList.class);

        Assertions.assertEquals(1, Objects.requireNonNull(responseEntity.getBody()).size());
        Assertions.assertEquals(now, Objects.requireNonNull(responseEntity.getBody()).get(0));
    }

    @Test
    void getVacancyAnyException() {
        when(reservationService.getVacancy(null, null)).thenThrow(new RuntimeException("Nope"));

        ResponseEntity<LocalDateList> responseEntity = restTemplate.getForEntity("/vacancy", LocalDateList.class);

        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, responseEntity.getStatusCode());
    }

    //Helper class to simplify generics usage
    static class LocalDateList extends ArrayList<LocalDate> {
    }
}