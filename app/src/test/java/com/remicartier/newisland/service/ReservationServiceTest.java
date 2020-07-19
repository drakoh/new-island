package com.remicartier.newisland.service;

import com.remicartier.model.ConfirmedReservation;
import com.remicartier.model.Reservation;
import com.remicartier.model.ReservationDates;
import com.remicartier.newisland.exception.ValidationException;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Created by remicartier on 2020-07-19 11:38 a.m.
 */
@SuppressWarnings("unchecked")
class ReservationServiceTest {
    private final static String EMAIL = "user@domain.com";
    private final static String FULL_NAME = "John Doe";
    private final static String BOOKING_ID = UUID.randomUUID().toString();
    private final LocalDate now = LocalDate.now(Clock.systemUTC());

    private JdbcTemplate jdbcTemplate;
    private ReservationService reservationService;

    @BeforeEach
    void setup() {
        jdbcTemplate = mock(JdbcTemplate.class);
        reservationService = new ReservationService(jdbcTemplate, 3, 1, 30);
    }

    @Test
    void getVacancy() {
        List<ReservationDates> reservationDates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LocalDate startDate = now.plusDays(1 + i * 2);
            LocalDate endDate = now.plusDays(2 + i * 2);
            reservationDates.add(new ReservationDates().startDate(startDate).endDate(endDate));
        }
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(reservationDates);

        List<LocalDate> vacancy = reservationService.getVacancy();
        reservationDates.forEach(rd -> vacancy.forEach(d -> Assertions.assertFalse(rd.getStartDate().equals(d) && rd.getEndDate().equals(d))));
    }

    @Test
    void getReservations() {
        reservationService.getReservations(EMAIL);

        verify(jdbcTemplate).query(anyString(), eq(ReservationService.ConfirmedReservationMapper.INSTANCE));
    }

    @Test
    void bookReservationTooEarly() {
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(now).endDate(now.plusDays(1));

        try {
            reservationService.bookReservation(reservation);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("Start date has to be at least 1 day(s) ahead of arrival", x.getMessage());
        }
    }

    @Test
    void bookReservationTooLong() {
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(1)).endDate(now.plusDays(4));

        try {
            reservationService.bookReservation(reservation);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("You can't book more than 3 day(s) at a time", x.getMessage());
        }
    }

    @Test
    void bookReservationTooFarInTime() {
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(40)).endDate(now.plusDays(42));

        try {
            reservationService.bookReservation(reservation);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("Start date has to be no more than 30 day(s) ahead of arrival", x.getMessage());
        }
    }

    @Test
    void bookReservationNewPerson() {
        LocalDate startDate = now.plusDays(1);
        LocalDate endDate = now.plusDays(2);
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(startDate).endDate(endDate);

        when(jdbcTemplate.query(anyString(), (Object[]) any(), any(RowMapper.class))).thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            invocationOnMock.getArgument(1, KeyHolder.class).getKeyList().add(ImmutableMap.of(BOOKING_ID, 1L));
            return 1;
        });
        when(jdbcTemplate.update(anyString(), anyString(), anyLong(), anyString())).thenReturn(1);

        ConfirmedReservation confirmedReservation = reservationService.bookReservation(reservation);

        Assertions.assertNotNull(confirmedReservation);
        Assertions.assertNotNull(confirmedReservation.getId());
        Assertions.assertEquals(EMAIL, confirmedReservation.getEmail());
        Assertions.assertEquals(FULL_NAME, confirmedReservation.getFullName());
        Assertions.assertEquals(startDate, confirmedReservation.getStartDate());
        Assertions.assertEquals(endDate, confirmedReservation.getEndDate());
    }

    @Test
    void bookReservationExistingPerson() {
        LocalDate startDate = now.plusDays(1);
        LocalDate endDate = now.plusDays(2);
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(startDate).endDate(endDate);

        when(jdbcTemplate.query(anyString(), (Object[]) any(), any(RowMapper.class))).thenReturn(Collections.singletonList(1L));
        when(jdbcTemplate.update(anyString(), anyString(), anyLong(), anyString())).thenReturn(1);

        ConfirmedReservation confirmedReservation = reservationService.bookReservation(reservation);

        Assertions.assertNotNull(confirmedReservation);
        Assertions.assertNotNull(confirmedReservation.getId());
        Assertions.assertEquals(EMAIL, confirmedReservation.getEmail());
        Assertions.assertEquals(FULL_NAME, confirmedReservation.getFullName());
        Assertions.assertEquals(startDate, confirmedReservation.getStartDate());
        Assertions.assertEquals(endDate, confirmedReservation.getEndDate());
    }

    @Test
    void bookReservationExistingPersonOverlappingDates() {
        LocalDate startDate = now.plusDays(1);
        LocalDate endDate = now.plusDays(2);
        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(startDate).endDate(endDate);

        when(jdbcTemplate.query(anyString(), (Object[]) any(), any(RowMapper.class))).thenReturn(Collections.singletonList(1L));
        when(jdbcTemplate.update(anyString(), anyString(), anyLong(), anyString())).thenThrow(new DataIntegrityViolationException("Nope"));

        try {
            reservationService.bookReservation(reservation);
        } catch (ValidationException x) {
            Assertions.assertEquals("Unable to create reservation, dates overlap with existing reservation", x.getMessage());
        }
    }

    @Test
    void getReservationAbsent() {
        when(jdbcTemplate.query(anyString(), eq(ReservationService.ConfirmedReservationMapper.INSTANCE))).thenReturn(Collections.emptyList());

        Optional<ConfirmedReservation> optionalConfirmedReservation = reservationService.getReservation(BOOKING_ID);

        Assertions.assertTrue(optionalConfirmedReservation.isEmpty());
    }

    @Test
    void getReservation() {
        when(jdbcTemplate.query(anyString(), eq(ReservationService.ConfirmedReservationMapper.INSTANCE))).thenReturn(Collections.singletonList(new ConfirmedReservation()));

        Optional<ConfirmedReservation> optionalConfirmedReservation = reservationService.getReservation(BOOKING_ID);

        Assertions.assertTrue(optionalConfirmedReservation.isPresent());
    }

    @Test
    void deleteReservation() {
        val id = BOOKING_ID;
        reservationService.deleteReservation(new ConfirmedReservation().id(id));

        verify(jdbcTemplate).update(anyString(), eq(id));
    }

    @Test
    void updateReservationTooEarly() {
        LocalDate endDate = now.plusDays(2);
        ReservationDates reservationDates = new ReservationDates().startDate(now).endDate(endDate);
        ConfirmedReservation confirmedReservation = (ConfirmedReservation) new ConfirmedReservation().id(BOOKING_ID).email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(3)).endDate(now.plusDays(4));

        try {
            reservationService.updateReservation(confirmedReservation, reservationDates);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("Start date has to be at least 1 day(s) ahead of arrival", x.getMessage());
        }
    }

    @Test
    void updateReservationTooLong() {
        LocalDate startDate = now.plusDays(1);
        LocalDate endDate = now.plusDays(4);
        ReservationDates reservationDates = new ReservationDates().startDate(startDate).endDate(endDate);
        ConfirmedReservation confirmedReservation = (ConfirmedReservation) new ConfirmedReservation().id(BOOKING_ID).email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(3)).endDate(now.plusDays(4));

        try {
            reservationService.updateReservation(confirmedReservation, reservationDates);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("You can't book more than 3 day(s) at a time", x.getMessage());
        }
    }

    @Test
    void updateReservationTooFarInTime() {
        LocalDate startDate = now.plusDays(35);
        LocalDate endDate = now.plusDays(37);
        ReservationDates reservationDates = new ReservationDates().startDate(startDate).endDate(endDate);
        ConfirmedReservation confirmedReservation = (ConfirmedReservation) new ConfirmedReservation().id(BOOKING_ID).email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(3)).endDate(now.plusDays(4));

        try {
            reservationService.updateReservation(confirmedReservation, reservationDates);
            fail();
        } catch (ValidationException x) {
            Assertions.assertEquals("Start date has to be no more than 30 day(s) ahead of arrival", x.getMessage());
        }
    }

    @Test
    void updateReservation() {
        LocalDate startDate = now.plusDays(1);
        LocalDate endDate = now.plusDays(2);
        ReservationDates reservationDates = new ReservationDates().startDate(startDate).endDate(endDate);
        ConfirmedReservation confirmedReservation = (ConfirmedReservation) new ConfirmedReservation().id(BOOKING_ID).email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(3)).endDate(now.plusDays(4));

        reservationService.updateReservation(confirmedReservation, reservationDates);

        verify(jdbcTemplate).update(anyString(), anyString(), anyString());
    }

    @Test
    void mapRow() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.getString(1)).thenReturn(BOOKING_ID);
        when(resultSet.getString(2)).thenReturn(EMAIL);
        when(resultSet.getString(3)).thenReturn(FULL_NAME);
        when(resultSet.getDate(4)).thenReturn(java.sql.Date.valueOf(now));
        when(resultSet.getDate(5)).thenReturn(java.sql.Date.valueOf(now.plusDays(1)));

        ConfirmedReservation confirmedReservation = ReservationService.ConfirmedReservationMapper.INSTANCE.mapRow(resultSet, 0);
    
        Assertions.assertNotNull(confirmedReservation);
        Assertions.assertEquals(BOOKING_ID, confirmedReservation.getId());
        Assertions.assertEquals(FULL_NAME, confirmedReservation.getFullName());
        Assertions.assertEquals(EMAIL, confirmedReservation.getEmail());
        Assertions.assertEquals(now, confirmedReservation.getStartDate());
        Assertions.assertEquals(now.plusDays(1), confirmedReservation.getEndDate());
    }
}