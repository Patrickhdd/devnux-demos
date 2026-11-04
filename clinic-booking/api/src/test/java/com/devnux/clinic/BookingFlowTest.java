package com.devnux.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class BookingFlowTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    @Autowired MockMvc mvc;

    @Test
    void patientCanBrowseBookAndCancel() throws Exception {
        String token = login("patient@clinic.test");
        long doctorId = doctorId("General practice");
        LocalDate wednesday = nextWeek(DayOfWeek.WEDNESDAY);
        String start = at(wednesday, 11, 0);

        mvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", wednesday.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.startsAt == '%s')].available".formatted(start)).value(true));

        String body = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":%d,\"startsAt\":\"%s\",\"reason\":\"Back pain\"}".formatted(doctorId, start)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("BOOKED"))
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", wednesday.toString()))
            .andExpect(jsonPath("$[?(@.startsAt == '%s')].available".formatted(start)).value(false));

        mvc.perform(get("/api/appointments/mine").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %d)].reason".formatted(id)).value("Back pain"));

        mvc.perform(post("/api/appointments/{id}/cancel", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Once cancelled, the slot is free again.
        mvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", wednesday.toString()))
            .andExpect(jsonPath("$[?(@.startsAt == '%s')].available".formatted(start)).value(true));
    }

    @Test
    void sameSlotCannotBeBookedTwice() throws Exception {
        long doctorId = doctorId("General practice");
        String start = at(nextWeek(DayOfWeek.THURSDAY), 15, 0);

        book(login("patient@clinic.test"), doctorId, start).andExpect(status().isCreated());
        book(login("alex@clinic.test"), doctorId, start)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Sorry, that slot was just booked by someone else"));
    }

    @Test
    void concurrentBookingsForOneSlotProduceExactlyOneAppointment() throws Exception {
        long doctorId = doctorId("Cardiology");
        String start = at(nextWeek(DayOfWeek.TUESDAY), 9, 15);
        List<String> tokens = List.of(login("patient@clinic.test"), login("alex@clinic.test"),
                register("sam@example.com"), register("lee@example.com"));

        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String t : tokens) {
            Callable<Integer> attempt = () -> {
                go.await();
                return book(t, doctorId, start).andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(attempt));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());
        pool.shutdown();

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
    }

    @Test
    void rejectsTimesThatAreNotSlots() throws Exception {
        long doctorId = doctorId("General practice");
        String token = login("patient@clinic.test");
        book(token, doctorId, at(nextWeek(DayOfWeek.MONDAY), 9, 10)).andExpect(status().isBadRequest()); // not on the 30-min grid
        book(token, doctorId, at(nextWeek(DayOfWeek.SATURDAY), 10, 0)).andExpect(status().isBadRequest()); // clinic closed
        book(token, doctorId, at(nextWeek(DayOfWeek.MONDAY), 13, 30)).andExpect(status().isBadRequest()); // lunch break
    }

    @Test
    void enforcesRoles() throws Exception {
        long doctorId = doctorId("General practice");
        String start = at(nextWeek(DayOfWeek.FRIDAY), 9, 0);

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":%d,\"startsAt\":\"%s\"}".formatted(doctorId, start)))
            .andExpect(status().isUnauthorized());

        String doctorToken = login("amal.haddad@clinic.test");
        book(doctorToken, doctorId, start).andExpect(status().isForbidden());

        String patientToken = login("patient@clinic.test");
        mvc.perform(get("/api/doctor/schedule").param("from", LocalDate.now().toString())
                .header("Authorization", "Bearer " + patientToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void doctorSeesTheirSchedule() throws Exception {
        String token = login("amal.haddad@clinic.test");
        mvc.perform(get("/api/doctor/schedule").param("from", LocalDate.now(ZONE).toString()).param("days", "14")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.reason == 'Annual check-up')].patientName").value("Jordan Miller"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"patient@clinic.test\",\"password\":\"nope\"}"))
            .andExpect(status().isUnauthorized());
    }

    // helpers

    private org.springframework.test.web.servlet.ResultActions book(String token, long doctorId, String start) throws Exception {
        return mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":%d,\"startsAt\":\"%s\"}".formatted(doctorId, start)));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"demo1234\"}".formatted(email)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Test Patient\"}".formatted(email)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private long doctorId(String specialty) throws Exception {
        String body = mvc.perform(get("/api/doctors")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[?(@.specialty == '%s')].id".formatted(specialty));
        return ids.getFirst();
    }

    /** A day in the week after next, so it is always in the future and inside the booking window. */
    private static LocalDate nextWeek(DayOfWeek day) {
        return LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.next(day));
    }

    private static String at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toString();
    }
}
