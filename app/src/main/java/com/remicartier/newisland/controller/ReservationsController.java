package com.remicartier.newisland.controller;

import com.remicartier.model.ConfirmedReservation;
import com.remicartier.model.Reservation;
import com.remicartier.model.ReservationDates;
import com.remicartier.newisland.exception.ValidationException;
import com.remicartier.newisland.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Created by remicartier on 2020-07-18 12:09 p.m.
 */
@Controller
public class ReservationsController {
    private final ReservationService reservationService;

    @Autowired
    public ReservationsController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping(path = "/reservations", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<ConfirmedReservation> getReservations(@RequestParam String email) {
        return reservationService.getReservations(email);
    }

    @PostMapping(path = "/reservations", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> createReservation(@RequestBody Reservation reservation) {
        validateReservation(reservation);
        return new ResponseEntity<>(reservationService.bookReservation(reservation), HttpStatus.CREATED);
    }

    @GetMapping(path = "/reservations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getReservationInfo(@PathVariable(name = "id") String reservationId) {
        return ResponseEntity.of(reservationService.getReservation(reservationId));
    }

    @DeleteMapping(path = "/reservations/{id}")
    public ResponseEntity<?> deleteReservation(@PathVariable(name = "id") String reservationId) {
        Optional<ConfirmedReservation> optionalConfirmedReservation = reservationService.getReservation(reservationId);

        if (optionalConfirmedReservation.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        reservationService.deleteReservation(optionalConfirmedReservation.get());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping(path = "/reservations/{id}")
    public ResponseEntity<?> updateReservation(@PathVariable(name = "id") String reservationId, @RequestBody ReservationDates reservationDates) {
        validateReservation(reservationDates);
        Optional<ConfirmedReservation> optionalConfirmedReservation = reservationService.getReservation(reservationId);

        if (optionalConfirmedReservation.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        reservationService.updateReservation(optionalConfirmedReservation.get(), reservationDates);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // -- Using manual validation as the Model objects are generated and do not contain Validations

    private void validateReservation(ReservationDates reservationDates) {
        if (reservationDates.getStartDate() == null) {
            throw new ValidationException("Field 'startDate' is undefined");
        }
        if (reservationDates.getEndDate() == null) {
            throw new ValidationException("Field 'endDate' is undefined");
        }
    }

    private void validateReservation(Reservation reservation) {
        validateReservation((ReservationDates) reservation);
        if (StringUtils.isEmpty(reservation.getEmail())) {
            throw new ValidationException("Field 'email' is undefined");
        }
        if (StringUtils.isEmpty(reservation.getFullName())) {
            throw new ValidationException("Field 'fullName' is undefined");
        }
    }
}
