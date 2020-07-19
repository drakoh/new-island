package com.remicartier.newisland.controller;

import com.remicartier.newisland.service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

/**
 * Created by remicartier on 2020-07-18 12:08 p.m.
 */
@Controller
public class VacancyController {
    private final ReservationService reservationService;

    @Autowired
    public VacancyController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping(path = "/vacancy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<LocalDate> getVacancy(@RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate) {
        return reservationService.getVacancy(startDate, endDate);
    }
}
