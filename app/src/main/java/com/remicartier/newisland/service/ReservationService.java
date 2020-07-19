package com.remicartier.newisland.service;

import com.remicartier.model.ConfirmedReservation;
import com.remicartier.model.Reservation;
import com.remicartier.model.ReservationDates;
import com.remicartier.newisland.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by remicartier on 2020-07-18 12:19 p.m.
 */
@Service
@Slf4j
public class ReservationService {
    private final JdbcTemplate jdbcTemplate;
    private final int maxConsecutiveDays;
    private final int minDaysAhead;
    private final int maxDaysAhead;

    @Autowired
    public ReservationService(JdbcTemplate jdbcTemplate,
                              @Value("${app.maxConsecutiveDays}") int maxConsecutiveDays,
                              @Value("${app.minDaysAhead}") int minDaysAhead,
                              @Value("${app.maxDaysAhead}") int maxDaysAhead) {
        this.jdbcTemplate = jdbcTemplate;
        this.maxConsecutiveDays = maxConsecutiveDays;
        this.minDaysAhead = minDaysAhead;
        this.maxDaysAhead = maxDaysAhead;
    }

    @Transactional
    public List<LocalDate> getVacancy() {
        LocalDate now = LocalDate.now(Clock.systemUTC());
        LocalDate nowPlus1Month = now.plus(1, ChronoUnit.MONTHS);
        List<LocalDate> vacancy = new ArrayList<>();
        for (LocalDate currentDate = now; currentDate.isBefore(nowPlus1Month); currentDate = currentDate.plusDays(1)) {
            vacancy.add(currentDate);
        }
        vacancy.add(nowPlus1Month);
        String sql = MessageFormat.format("SELECT lower(duration) as start_date,upper(duration) as end_date FROM reservation WHERE ''[{0}, {1}]''::daterange && duration", now, nowPlus1Month);
        jdbcTemplate.query(sql, (resultSet, i) -> {
            LocalDate startDate = resultSet.getDate(1).toLocalDate();
            LocalDate endDate = resultSet.getDate(2).toLocalDate();
            return new ReservationDates().startDate(startDate).endDate(endDate);
        }).forEach(rd -> {
            for (LocalDate currentDate = rd.getStartDate(); currentDate.isBefore(rd.getEndDate()); currentDate = currentDate.plusDays(1)) {
                vacancy.remove(currentDate);
            }
            vacancy.remove(rd.getEndDate());
        });
        return vacancy;
    }

    @Transactional
    public List<ConfirmedReservation> getReservations(String email) {
        LocalDate now = LocalDate.now(Clock.systemUTC());
        LocalDate nowPlus1Month = now.plus(1, ChronoUnit.MONTHS);
        String sql = MessageFormat.format("SELECT reservation.id,person.email,person.full_name,lower(reservation.duration) as start_date,upper(reservation.duration) as end_date FROM reservation JOIN person ON reservation.person_id=person.id WHERE person.email=''{2}'' AND ''[{0}, {1}]''::daterange && duration", now, nowPlus1Month, email);
        return jdbcTemplate.query(sql, ConfirmedReservationMapper.INSTANCE);
    }

    @Transactional
    public ConfirmedReservation bookReservation(Reservation reservation) {
        validateReservation(reservation);
        List<Long> personIdList = jdbcTemplate.query("SELECT id FROM person WHERE email=? AND full_name=?", new Object[]{reservation.getEmail(), reservation.getFullName()}, (resultSet, i) -> resultSet.getLong(1));
        long personId;
        if (CollectionUtils.isEmpty(personIdList)) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement pstmt = connection.prepareStatement("INSERT INTO person (email,full_name) VALUES (?,?)", new String[]{"id"});
                pstmt.setString(1, reservation.getEmail());
                pstmt.setString(2, reservation.getFullName());
                return pstmt;
            }, keyHolder);
            personId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        } else {
            personId = personIdList.get(0);
        }
        String reservationId = UUID.randomUUID().toString();
        String duration = MessageFormat.format("[''{0}'', ''{1}'')", reservation.getStartDate(), reservation.getEndDate());
        try {
            jdbcTemplate.update("INSERT INTO reservation (id, person_id, duration) VALUES (?,?,?::daterange)", reservationId, personId, duration);
        } catch (DataIntegrityViolationException x) {
            throw new ValidationException("Unable to create reservation, dates overlap with existing reservation", x);
        }
        return (ConfirmedReservation) new ConfirmedReservation().id(reservationId).fullName(reservation.getFullName())
                .email(reservation.getEmail()).startDate(reservation.getStartDate()).endDate(reservation.getEndDate());
    }

    private void validateReservation(ReservationDates reservationDates) {
        LocalDate now = LocalDate.now(Clock.systemUTC());
        if (reservationDates.getStartDate().isBefore(now) || dayDiff(now, reservationDates.getStartDate()) < minDaysAhead) {
            throw new ValidationException(MessageFormat.format("Start date has to be at least {0} day(s) ahead of arrival", minDaysAhead));
        }
        if (dayDiff(reservationDates.getStartDate(), reservationDates.getEndDate()) > maxConsecutiveDays) {
            throw new ValidationException(MessageFormat.format("You can''t book more than {0} day(s) at a time", maxConsecutiveDays));
        }
        if (dayDiff(reservationDates.getStartDate(), now) > maxDaysAhead) {
            throw new ValidationException(MessageFormat.format("Start date has to be no more than {0} day(s) ahead of arrival", maxDaysAhead));
        }
    }

    private long dayDiff(LocalDate date1, LocalDate date2) {
        return date1.equals(date2) ? 0 : Math.abs(ChronoUnit.DAYS.between(date1, date2)) + 1;
    }

    @Transactional
    public Optional<ConfirmedReservation> getReservation(String reservationId) {
        String sql = MessageFormat.format("SELECT reservation.id,person.email,person.full_name,lower(reservation.duration) as start_date,upper(reservation.duration) as end_date FROM reservation JOIN person ON reservation.person_id=person.id WHERE reservation.id=''{0}''", reservationId);
        List<ConfirmedReservation> confirmedReservations = jdbcTemplate.query(sql, ConfirmedReservationMapper.INSTANCE);
        return CollectionUtils.isEmpty(confirmedReservations) ? Optional.empty() : Optional.of(confirmedReservations.get(0));
    }

    @Transactional
    public void deleteReservation(ConfirmedReservation confirmedReservation) {
        jdbcTemplate.update("DELETE FROM reservation WHERE id=?", confirmedReservation.getId());
    }

    @Transactional
    public void updateReservation(ConfirmedReservation confirmedReservation, ReservationDates reservationDates) {
        validateReservation(reservationDates);
        String duration = MessageFormat.format("[''{0}'', ''{1}'')", reservationDates.getStartDate(), reservationDates.getEndDate());
        try {
            jdbcTemplate.update("UPDATE reservation SET duration=?::daterange WHERE id=?", duration, confirmedReservation.getId());
        } catch (DataIntegrityViolationException x) {
            throw new ValidationException("Unable to update reservationDates, dates overlap with existing reservationDates", x);
        }
    }

    static class ConfirmedReservationMapper implements RowMapper<ConfirmedReservation> {
        final static ConfirmedReservationMapper INSTANCE = new ConfirmedReservationMapper();

        @Override
        public ConfirmedReservation mapRow(ResultSet resultSet, int i) throws SQLException {
            return (ConfirmedReservation) new ConfirmedReservation()
                    .id(resultSet.getString(1))
                    .email(resultSet.getString(2))
                    .fullName(resultSet.getString(3))
                    .startDate(resultSet.getDate(4).toLocalDate())
                    .endDate(resultSet.getDate(5).toLocalDate());
        }
    }
}
