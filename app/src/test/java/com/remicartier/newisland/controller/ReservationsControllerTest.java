package com.remicartier.newisland.controller;

import com.remicartier.model.ConfirmedReservation;
import com.remicartier.model.ErrorMessage;
import com.remicartier.model.Reservation;
import com.remicartier.model.ReservationDates;
import com.remicartier.newisland.exception.ValidationException;
import com.remicartier.newisland.service.ReservationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by remicartier on 2020-07-19 2:26 p.m.
 */
@SuppressWarnings("unused")
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationsControllerTest {
    private final static String EMAIL = "user@domain.com";
    private final static String FULL_NAME = "John Doe";
    private final static String BOOKING_ID = UUID.randomUUID().toString();
    private final LocalDate now = LocalDate.now(Clock.systemUTC());

    @MockBean
    private ReservationService reservationService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void getReservations() {
        when(reservationService.getReservations("user@domain.com")).thenReturn(Collections.singletonList(new ConfirmedReservation()));

        ResponseEntity<ConfirmedReservationList> responseEntity = restTemplate.getForEntity("/reservations?email=user@domain.com", ConfirmedReservationList.class);

        Assertions.assertEquals(1, Objects.requireNonNull(responseEntity.getBody()).size());
    }

    @Test
    void getReservationsAnyException() {
        when(reservationService.getReservations("user@domain.com")).thenThrow(new RuntimeException("Nope"));

        ResponseEntity<ConfirmedReservationList> responseEntity = restTemplate.getForEntity("/reservations?email=user@domain.com", ConfirmedReservationList.class);

        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, responseEntity.getStatusCode());
    }

    @Test
    void createReservation() {
        when(reservationService.bookReservation(any(Reservation.class))).thenReturn(new ConfirmedReservation().id(BOOKING_ID));

        Reservation reservation = (Reservation) new Reservation().fullName(FULL_NAME).email(EMAIL).startDate(now).endDate(now.plusDays(1));

        ResponseEntity<ConfirmedReservation> responseEntity = restTemplate.postForEntity("/reservations", reservation, ConfirmedReservation.class);

        Assertions.assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals(BOOKING_ID, responseEntity.getBody().getId());
    }

    @Test
    void createReservationBadRequest() {
        when(reservationService.bookReservation(any(Reservation.class))).thenThrow(new ValidationException("Nope"));

        Reservation reservation = (Reservation) new Reservation().fullName(FULL_NAME).email(EMAIL).startDate(now).endDate(now.plusDays(1));

        ResponseEntity<ErrorMessage> responseEntity = restTemplate.postForEntity("/reservations", reservation, ErrorMessage.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals("Nope", responseEntity.getBody().getMessage());
    }

    @Test
    void createReservationBadRequestMissingField() {
        Reservation reservation = (Reservation) new Reservation().fullName(FULL_NAME).email(EMAIL).startDate(now);

        ResponseEntity<ErrorMessage> responseEntity = restTemplate.postForEntity("/reservations", reservation, ErrorMessage.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals("Field 'endDate' is undefined", responseEntity.getBody().getMessage());
    }

    @Test
    void getReservationInfo() {
        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.of(new ConfirmedReservation().id(BOOKING_ID)));

        ResponseEntity<ConfirmedReservation> responseEntity = restTemplate.getForEntity("/reservations/" + BOOKING_ID, ConfirmedReservation.class);

        Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals(BOOKING_ID, responseEntity.getBody().getId());
    }

    @Test
    void getReservationInfoNotFound() {
        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.empty());

        ResponseEntity<ConfirmedReservation> responseEntity = restTemplate.getForEntity("/reservations/" + BOOKING_ID, ConfirmedReservation.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
    }

    @Test
    void deleteReservation() {
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.of(confirmedReservation));

        ResponseEntity<Object> responseEntity = restTemplate.exchange("/reservations/" + BOOKING_ID, HttpMethod.DELETE, null, Object.class);

        verify(reservationService).deleteReservation(confirmedReservation);

        Assertions.assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
    }

    @Test
    void deleteReservationNotFound() {
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.empty());

        ResponseEntity<Object> responseEntity = restTemplate.exchange("/reservations/" + BOOKING_ID, HttpMethod.DELETE, null, Object.class);

        verify(reservationService, never()).deleteReservation(confirmedReservation);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
    }

    @Test
    void updateReservation() {
        Reservation reservation = (Reservation) new Reservation().startDate(now).endDate(now.plusDays(1));
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.of(confirmedReservation));

        ResponseEntity<Object> responseEntity = restTemplate.exchange(RequestEntity.patch(URI.create("/reservations/" + BOOKING_ID)).body(reservation), Object.class);

        verify(reservationService).updateReservation(any(ConfirmedReservation.class), any(ReservationDates.class));

        Assertions.assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
    }

    @Test
    void updateReservationNotFound() {
        Reservation reservation = (Reservation) new Reservation().startDate(now).endDate(now.plusDays(1));
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.empty());

        ResponseEntity<Object> responseEntity = restTemplate.exchange(RequestEntity.patch(URI.create("/reservations/" + BOOKING_ID)).body(reservation), Object.class);

        verify(reservationService, never()).updateReservation(confirmedReservation, reservation);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
    }

    @Test
    void updateReservationBadRequest() {
        Reservation reservation = (Reservation) new Reservation().startDate(now).endDate(now.plusDays(1));
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.of(confirmedReservation));
        doThrow(new ValidationException("Nope")).when(reservationService).updateReservation(eq(confirmedReservation), any(ReservationDates.class));

        ResponseEntity<ErrorMessage> responseEntity = restTemplate.exchange(RequestEntity.patch(URI.create("/reservations/" + BOOKING_ID)).body(reservation), ErrorMessage.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals("Nope", responseEntity.getBody().getMessage());
    }

    @Test
    void updateReservationBadRequestMissingField() {
        Reservation reservation = (Reservation) new Reservation().startDate(now);
        ConfirmedReservation confirmedReservation = new ConfirmedReservation().id(BOOKING_ID);

        when(reservationService.getReservation(BOOKING_ID)).thenReturn(Optional.of(confirmedReservation));

        ResponseEntity<ErrorMessage> responseEntity = restTemplate.exchange(RequestEntity.patch(URI.create("/reservations/" + BOOKING_ID)).body(reservation), ErrorMessage.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertEquals("Field 'endDate' is undefined", responseEntity.getBody().getMessage());
    }

    // Helper class to simplify generics usage
    static class ConfirmedReservationList extends ArrayList<ConfirmedReservation> {
    }
}