package com.remicartier.newisland;

import com.remicartier.model.ConfirmedReservation;
import com.remicartier.model.ErrorMessage;
import com.remicartier.model.Reservation;
import com.remicartier.model.ReservationDates;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;

@Testcontainers
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {NewIslandApplicationTests.Initializer.class})
class NewIslandApplicationTests {
    private final static String USERNAME = "postgres";
    private final static String PASSWORD = "password";
    private final static String EMAIL = "user@domain.com";
    private final static String FULL_NAME = "John Doe";

    @Autowired
    private TestRestTemplate restTemplate;

    private final LocalDate now = LocalDate.now(Clock.systemUTC());

    @Container
    private static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER = new PostgreSQLContainer<>()
            .withDatabaseName("new_island")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeAll
    static void setup() {
        Flyway flyway = Flyway.configure().dataSource(POSTGRE_SQL_CONTAINER.getJdbcUrl(), USERNAME, PASSWORD).load();
        flyway.migrate();
    }

    @AfterAll
    static void tearDown() {
        POSTGRE_SQL_CONTAINER.stop();
    }

    static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRE_SQL_CONTAINER.getUsername(),
                    "spring.datasource.password=" + POSTGRE_SQL_CONTAINER.getPassword()
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @Test
    void scenario() {
        ResponseEntity<LocalDateList> responseEntity = restTemplate.getForEntity("/vacancy", LocalDateList.class);

        Assertions.assertNotNull(responseEntity.getBody());
        LocalDateList localDates = responseEntity.getBody();

        Reservation reservation = (Reservation) new Reservation().email(EMAIL).fullName(FULL_NAME).startDate(now.plusDays(1)).endDate(now.plusDays(2));
        ResponseEntity<ConfirmedReservation> postResponseEntity = restTemplate.postForEntity("/reservations", reservation, ConfirmedReservation.class);

        Assertions.assertEquals(HttpStatus.CREATED, postResponseEntity.getStatusCode());

        int vacancyNow = restTemplate.getForEntity("/vacancy", LocalDateList.class).getBody().size();

        Assertions.assertEquals(localDates.size() - 2, vacancyNow);

        //Check booking overlap
        ResponseEntity<ErrorMessage> postResponseEntity2 = restTemplate.postForEntity("/reservations", reservation, ErrorMessage.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, postResponseEntity2.getStatusCode());
        Assertions.assertNotNull(postResponseEntity2.getBody());
        Assertions.assertEquals("Unable to create reservation, dates overlap with existing reservation", postResponseEntity2.getBody().getMessage());

        //List bookings
        ResponseEntity<ConfirmedReservationList> listResponseEntity = restTemplate.getForEntity("/reservations?email=" + EMAIL, ConfirmedReservationList.class);
        Assertions.assertNotNull(listResponseEntity.getBody());
        Assertions.assertEquals(1, listResponseEntity.getBody().size());
        ConfirmedReservation confirmedReservation = listResponseEntity.getBody().get(0);
        Assertions.assertEquals(FULL_NAME, confirmedReservation.getFullName());
        Assertions.assertEquals(EMAIL, confirmedReservation.getEmail());
        Assertions.assertEquals(now.plusDays(1), confirmedReservation.getStartDate());
        Assertions.assertEquals(now.plusDays(2), confirmedReservation.getEndDate());

        //Update booking
        ReservationDates reservationDates = new ReservationDates().startDate(now.plusDays(1)).endDate(now.plusDays(3));
        ResponseEntity<Object> objectResponseEntity = restTemplate.exchange(RequestEntity.patch(URI.create("/reservations/" + confirmedReservation.getId())).body(reservationDates), Object.class);

        Assertions.assertEquals(HttpStatus.NO_CONTENT, objectResponseEntity.getStatusCode());
        listResponseEntity = restTemplate.getForEntity("/reservations?email=" + EMAIL, ConfirmedReservationList.class);
        Assertions.assertNotNull(listResponseEntity.getBody());
        Assertions.assertEquals(1, listResponseEntity.getBody().size());
        confirmedReservation = listResponseEntity.getBody().get(0);
        Assertions.assertEquals(now.plusDays(3), confirmedReservation.getEndDate());

        //Delete booking
        objectResponseEntity = restTemplate.exchange(RequestEntity.delete(URI.create("/reservations/" + confirmedReservation.getId())).build(), Object.class);
        Assertions.assertEquals(HttpStatus.NO_CONTENT, objectResponseEntity.getStatusCode());

        listResponseEntity = restTemplate.getForEntity("/reservations?email=" + EMAIL, ConfirmedReservationList.class);
        Assertions.assertNotNull(listResponseEntity.getBody());
        Assertions.assertEquals(0, listResponseEntity.getBody().size());
    }

    static class LocalDateList extends ArrayList<LocalDate> {
    }

    static class ConfirmedReservationList extends ArrayList<ConfirmedReservation> {
    }
}
