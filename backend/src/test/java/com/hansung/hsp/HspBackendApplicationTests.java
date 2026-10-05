package com.hansung.hsp;

import com.hansung.hsp.admin.*;
import com.hansung.hsp.auth.*;
import com.hansung.hsp.common.ApiException;
import com.hansung.hsp.reservation.*;
import com.hansung.hsp.space.*;
import com.hansung.hsp.user.*;
import com.hansung.hsp.policy.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureMockMvc
class HspBackendApplicationTests {
    // Always a separate PostgreSQL container; never use DB_URL or the Compose volume.
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("hsp_api_test")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SpaceRepository spaces;
    @Autowired SeatRepository seats;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationMemberRepository members;
    @Autowired SpaceBlockRepository blocks;
    @Autowired AdminSpacePermissionRepository permissions;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired ReservationService reservationService;
    @Autowired SpaceBlockService blockService;
    @Autowired BookingRulesService bookingRules;
    @Autowired ReservationActionRepository reservationActions;
    @Autowired org.springframework.security.web.FilterChainProxy securityFilters;
    @Autowired org.springframework.security.web.csrf.CsrfTokenRepository csrfRepository;

    @BeforeEach void restoreProductionCsrfRepository() {
        // SecurityMockMvcRequestPostProcessors.csrf() replaces the filter repository.
        // Restore it between tests so real cookie/session tests are order-independent.
        securityFilters.getFilters("/api/auth/csrf").stream()
                .filter(org.springframework.security.web.csrf.CsrfFilter.class::isInstance)
                .map(org.springframework.security.web.csrf.CsrfFilter.class::cast)
                .forEach(filter -> org.springframework.test.util.ReflectionTestUtils.setField(filter, "tokenRepository", csrfRepository));
    }

    private String unique() { return UUID.randomUUID().toString().substring(0, 18); }
    private User user(String role) {
        String id = unique();
        return users.saveAndFlush(new User(id, id + "@example.test", "테스트 사용자", role));
    }
    private Space room(boolean enabled) {
        return spaces.saveAndFlush(new Space(unique(), "스터디룸", "STUDY_ROOM", "상상관",
                1, 6, "STUDENT_CENTER", "화이트보드", enabled));
    }
    private User manager(Space space) {
        var admin = user("ADMIN");
        permissions.saveAndFlush(new AdminSpacePermission(admin.getId(), space.getId()));
        return admin;
    }
    private Instant future() { return Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS); }
    private ReservationCreateRequest booking(Space space, Instant start, Instant end) {
        return new ReservationCreateRequest(space.getId(), null, start, end, "학습",
                ReservationKind.BOOKING, List.of());
    }
    private ReservationCreateRequest seatBooking(Space space, Seat seat, Instant start, Instant end) {
        return new ReservationCreateRequest(space.getId(), seat.getId(), start, end, "좌석 이용",
                ReservationKind.SEAT_USE, List.of());
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User u) {
        return request.with(SecurityMockMvcRequestPostProcessors.user(
                new HspPrincipal(u.getId(), u.getStudentId(), u.getRole(), null)));
    }
    private ResultActions create(User u, ReservationCreateRequest input) throws Exception {
        return mvc.perform(as(post("/api/reservations"), u).with(csrf().asHeader())
                .contentType("application/json").content(json.writeValueAsString(input)));
    }
    private long id(MvcResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    @Test void flywayAndHibernateValidateRealPostgresSchema() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select type from flyway_schema_history where version='1'", String.class))
                .isEqualTo("SQL");
    }

    @Test void batchSeatAvailabilityPreservesPrivacyAndUsesRealSeatIds() throws Exception {
        var space = room(true); var owner = user("STUDENT"); var other = user("STUDENT");
        var first = seats.saveAndFlush(new Seat(space.getId(), "001", "AVAILABLE"));
        var second = seats.saveAndFlush(new Seat(space.getId(), "162", "AVAILABLE"));
        var start = future(); var end = start.plusSeconds(3600);
        var reservation = reservationService.create(owner.getId(), seatBooking(space, first, start, end));
        var path = "/api/spaces/" + space.getId() + "/seats/availability";
        mvc.perform(get(path).param("startTime", start.toString()).param("endTime", end.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.seats.totalElements").value(2))
                .andExpect(jsonPath("$.seats.content[0].available").value(false))
                .andExpect(jsonPath("$.seats.content[0].mine").value(false))
                .andExpect(jsonPath("$.seats.content[0].reservationId").isEmpty())
                .andExpect(jsonPath("$.seats.content[0].owner").doesNotExist())
                .andExpect(jsonPath("$.seats.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.seats.content[1].available").value(true));
        mvc.perform(as(get(path).param("startTime", start.toString()).param("endTime", end.toString()), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats.content[0].mine").value(true))
                .andExpect(jsonPath("$.seats.content[0].reservationId").value(reservation.id()));
        mvc.perform(as(get(path).param("startTime", start.toString()).param("endTime", end.toString()), other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats.content[0].reservationId").isEmpty());
        mvc.perform(get(path).param("startTime", start.toString()).param("endTime", start.plus(32, ChronoUnit.DAYS).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test void instantUseClipsAtNextBookingThenReturnPreservesOriginalEndAndAudit() throws Exception {
        var space = room(true); var owner = user("STUDENT"); var nextOwner = user("STUDENT");
        var seat = seats.saveAndFlush(new Seat(space.getId(), "008", "AVAILABLE"));
        var nextStart = Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(1800);
        reservationService.create(nextOwner.getId(), seatBooking(space, seat, nextStart, nextStart.plusSeconds(3600)));
        mvc.perform(get("/api/spaces/" + space.getId() + "/seat-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instant").value(true))
                .andExpect(jsonPath("$.seats.content[0].available").value(true));
        var result = mvc.perform(as(post("/api/spaces/" + space.getId() + "/seats/" + seat.getId() + "/use"), owner)
                        .with(csrf().asHeader())).andExpect(status().isCreated()).andReturn();
        long reservationId = id(result);
        var saved = reservations.findById(reservationId).orElseThrow();
        assertThat(saved.getStartTime()).isBefore(Instant.now());
        assertThat(saved.getEndTime()).isEqualTo(nextStart.truncatedTo(ChronoUnit.MICROS));
        mvc.perform(as(post("/api/reservations/" + reservationId + "/return"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/reservations/" + reservationId + "/return"), nextOwner).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/reservations/" + reservationId + "/return"), owner).with(csrf().asHeader()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty()).andExpect(jsonPath("$.actions[0].action").value("RETURN_SEAT"));
        mvc.perform(as(post("/api/reservations/" + reservationId + "/return"), owner).with(csrf().asHeader()))
                .andExpect(status().isOk());
        var returned = reservations.findById(reservationId).orElseThrow();
        assertThat(returned.getEndTime()).isEqualTo(saved.getEndTime());
        assertThat(returned.getEndedAt()).isBefore(returned.getEndTime());
        assertThat(reservationActions.findByReservationIdOrderById(reservationId)).hasSize(1);
        mvc.perform(get("/api/spaces/" + space.getId() + "/seat-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seats.content[0].available").value(true));
    }

    @Test void instantUseRejectsBlockedAndDisabledSeatsAndSameOwnerAcrossRooms() throws Exception {
        var space = room(true); var otherRoom = room(true); var owner = user("STUDENT");
        var first = seats.saveAndFlush(new Seat(space.getId(), "001", "AVAILABLE"));
        var second = seats.saveAndFlush(new Seat(otherRoom.getId(), "002", "AVAILABLE"));
        var disabled = seats.saveAndFlush(new Seat(space.getId(), "003", "DISABLED"));
        assertThatThrownBy(() -> reservationService.startSeatUse(owner.getId(), space.getId(), disabled.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("사용할 수 없는");
        reservationService.startSeatUse(owner.getId(), space.getId(), first.getId());
        mvc.perform(as(post("/api/spaces/" + otherRoom.getId() + "/seats/" + second.getId() + "/use"), owner)
                        .with(csrf().asHeader())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_USING_SEAT"));
        var admin = manager(otherRoom);
        blocks.saveAndFlush(new SpaceBlock(otherRoom.getId(), admin.getId(), Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600), "점검", Instant.now()));
        assertThatThrownBy(() -> reservationService.startSeatUse(user("STUDENT").getId(), otherRoom.getId(), second.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("현재 이용할 수 없는");
    }

    @Test void concurrentInstantUsesAcrossSpacesAllowOnlyOneSeatForTheSameUser() throws Exception {
        var one = room(true); var two = room(true); var owner = user("STUDENT");
        var a = seats.saveAndFlush(new Seat(one.getId(), "A", "AVAILABLE"));
        var b = seats.saveAndFlush(new Seat(two.getId(), "B", "AVAILABLE"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            var tasks = List.of(a, b).stream().map(seat -> pool.submit(() -> {
                ready.countDown(); go.await();
                try { reservationService.startSeatUse(owner.getId(), seat.getSpaceId(), seat.getId()); return true; }
                catch (ApiException ex) { return false; }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            int created = 0;
            for (var task : tasks) if (task.get(15, TimeUnit.SECONDS)) created++;
            assertThat(created).isEqualTo(1);
        }
    }

    @Test void instantUseHonorsClosedHoursAndExamOverride() throws Exception {
        var space = room(true); var admin = manager(space); var owner = user("STUDENT");
        var seat = seats.saveAndFlush(new Seat(space.getId(), "E", "AVAILABLE"));
        var hours = Arrays.stream(OperatingPeriod.values()).flatMap(period -> Arrays.stream(java.time.DayOfWeek.values())
                .map(day -> new OperatingHoursInput(period, day, period != OperatingPeriod.EXAM,
                        period == OperatingPeriod.EXAM ? "00:00" : null, period == OperatingPeriod.EXAM ? "24:00" : null))).toList();
        savePolicy(admin, space, new OperatingPolicyInput(true, OperatingPeriod.SEMESTER, null, null, hours)).andExpect(status().isOk());
        mvc.perform(as(post("/api/spaces/" + space.getId() + "/seats/" + seat.getId() + "/use"), owner).with(csrf().asHeader()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE_NOW"));
        var today = Instant.now().atZone(OperatingCalendar.ZONE).toLocalDate();
        savePolicy(admin, space, new OperatingPolicyInput(true, OperatingPeriod.SEMESTER, today, today.plusDays(1), hours)).andExpect(status().isOk());
        mvc.perform(get("/api/spaces/" + space.getId() + "/seat-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.content[0].available").value(true));
        var result = reservationService.startSeatUse(owner.getId(), space.getId(), seat.getId());
        assertThat(java.time.Duration.between(result.startTime(), result.endTime())).isEqualTo(java.time.Duration.ofHours(3));
    }

    @Test void adminCancellationRequiresScopeReasonAndPreservesBookingAndParticipants() throws Exception {
        var space = room(true); var owner = user("STUDENT"); var admin = manager(space); var outsider = user("ADMIN");
        var start = future();
        var reservation = reservationService.create(owner.getId(), new ReservationCreateRequest(space.getId(), null,
                start, start.plusSeconds(3600), "회의", ReservationKind.BOOKING,
                List.of(new ReservationMemberRequest(unique(), "참여자"))));
        var path = "/api/admin/reservations/" + reservation.id() + "/cancel";
        mvc.perform(as(post(path), owner).with(csrf().asHeader()).contentType("application/json").content("{\"reason\":\"점검\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post(path), outsider).with(csrf().asHeader()).contentType("application/json").content("{\"reason\":\"점검\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post(path), admin).with(csrf().asHeader()).contentType("application/json").content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post(path), admin).with(csrf().asHeader()).contentType("application/json").content("{\"reason\":\"시설 점검\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.owner.studentId").value(owner.getStudentId()))
                .andExpect(jsonPath("$.actions[0].reason").value("시설 점검"))
                .andExpect(jsonPath("$.actions[0].actorId").value(admin.getId()));
        assertThat(members.findByReservationIdOrderById(reservation.id())).hasSize(1);
        assertThat(reservations.existsById(reservation.id())).isTrue();
        mvc.perform(as(post(path), admin).with(csrf().asHeader()).contentType("application/json").content("{\"reason\":\"재시도\"}"))
                .andExpect(status().isOk());
        assertThat(reservationActions.findByReservationIdOrderById(reservation.id())).hasSize(1);
    }

    @Test void adminEndsActiveSeatUseWithoutErasingOriginalTimes() throws Exception {
        var space = room(true); var admin = manager(space); var owner = user("STUDENT");
        var seat = seats.saveAndFlush(new Seat(space.getId(), "A", "AVAILABLE"));
        var reservation = reservationService.startSeatUse(owner.getId(), space.getId(), seat.getId());
        mvc.perform(as(post("/api/admin/reservations/" + reservation.id() + "/cancel"), admin).with(csrf().asHeader())
                        .contentType("application/json").content("{\"reason\":\"운영 종료\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actions[0].action").value("ADMIN_END"));
        assertThat(reservations.findById(reservation.id()).orElseThrow().getEndTime())
                .isCloseTo(reservation.endTime(), within(1, ChronoUnit.MICROS));
    }

    private BookingRulesInput libraryRules(boolean enabled) {
        return new BookingRulesInput(enabled, 30, 30, 180, 7, 180,
                BookingRulesInput.UsageScope.VENUE, true, true, 180);
    }

    @Test void bookingRulesAreOptInScopedAndEnforceDurationHorizonAndPurpose() throws Exception {
        var space = room(true); var admin = manager(space); var owner = user("STUDENT");
        mvc.perform(get("/api/spaces/" + space.getId() + "/booking-rules"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.enabled").value(false));
        var path = "/api/admin/spaces/" + space.getId() + "/booking-rules";
        mvc.perform(as(put(path), user("ADMIN")).with(csrf().asHeader()).contentType("application/json")
                        .content(json.writeValueAsString(libraryRules(true)))).andExpect(status().isForbidden());
        mvc.perform(as(put(path), admin).with(csrf().asHeader()).contentType("application/json")
                        .content(json.writeValueAsString(libraryRules(true)))).andExpect(status().isOk());
        var start = future().truncatedTo(ChronoUnit.DAYS);
        create(owner, booking(space, start, start.plusSeconds(4 * 3600))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOKING_DURATION_LIMIT"));
        create(owner, booking(space, start.plusSeconds(1), start.plusSeconds(3601))).andExpect(status().isBadRequest());
        create(owner, booking(space, start.plus(10, ChronoUnit.DAYS), start.plus(10, ChronoUnit.DAYS).plusSeconds(3600)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOOKING_ADVANCE_LIMIT"));
        create(owner, new ReservationCreateRequest(space.getId(), null, start, start.plusSeconds(3600), " ",
                ReservationKind.BOOKING, List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PURPOSE_REQUIRED"));
        bookingRules.replace(admin.getId(), space.getId(), libraryRules(false));
        create(owner, booking(space, start.plus(10, ChronoUnit.DAYS), start.plus(10, ChronoUnit.DAYS).plusSeconds(4 * 3600)))
                .andExpect(status().isCreated());
    }

    @Test void sharedVenueDailyLimitAndAdjacentRuleApplyAcrossRoomsAndCancellationRestoresQuota() throws Exception {
        var a = room(true); var b = room(true); var owner = user("STUDENT");
        bookingRules.replace(manager(a).getId(), a.getId(), libraryRules(true));
        bookingRules.replace(manager(b).getId(), b.getId(), libraryRules(true));
        var start = future().truncatedTo(ChronoUnit.DAYS);
        var reservation = reservationService.create(owner.getId(), booking(a, start, start.plusSeconds(3600)));
        create(owner, booking(b, start.plusSeconds(3600), start.plusSeconds(7200))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADJACENT_BOOKING_NOT_ALLOWED"));
        create(owner, booking(b, start.plusSeconds(7200), start.plusSeconds(18000))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAILY_USAGE_LIMIT"));
        reservationService.cancel(owner.getId(), reservation.id());
        create(owner, booking(b, start.plusSeconds(7200), start.plusSeconds(18000))).andExpect(status().isCreated());
    }

    @Test void adminSummaryUsesRealDurationsAndOnlyManagedSpaces() throws Exception {
        var space = room(true); var admin = manager(space); var owner = user("STUDENT");
        var start = future().truncatedTo(ChronoUnit.DAYS);
        reservationService.create(owner.getId(), booking(space, start, start.plusSeconds(5400)));
        var cancelled = reservationService.create(owner.getId(), booking(space, start.plusSeconds(7200), start.plusSeconds(9000)));
        reservationService.cancel(owner.getId(), cancelled.id());
        reservationService.create(owner.getId(), booking(room(true), start, start.plusSeconds(7200)));
        mvc.perform(as(get("/api/admin/summary").param("date", start.atZone(OperatingCalendar.ZONE).toLocalDate().toString()), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.managedSpaces").value(1))
                .andExpect(jsonPath("$.reservationCount").value(1)).andExpect(jsonPath("$.reservedMinutes").value(90.0));
        mvc.perform(as(get("/api/admin/summary"), owner)).andExpect(status().isForbidden());
    }

    @Test void concurrentBookingsCannotExceedDailyLimitAcrossDifferentRooms() throws Exception {
        var a = room(true); var b = room(true); var owner = user("STUDENT");
        bookingRules.replace(manager(a).getId(), a.getId(), libraryRules(true));
        bookingRules.replace(manager(b).getId(), b.getId(), libraryRules(true));
        var start = future().truncatedTo(ChronoUnit.DAYS);
        var inputs = List.of(booking(a, start, start.plusSeconds(7200)),
                booking(b, start.plusSeconds(10800), start.plusSeconds(18000)));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            var tasks = inputs.stream().map(input -> pool.submit(() -> {
                ready.countDown(); go.await();
                try { reservationService.create(owner.getId(), input); return "CREATED"; }
                catch (ApiException ex) { return ex.getMessage(); }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var results = new ArrayList<String>();
            for (var task : tasks) results.add(task.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter("CREATED"::equals).count()).isEqualTo(1);
            assertThat(results.stream().anyMatch(value -> value.contains("하루 이용시간 한도"))).isTrue();
        }
    }

    @Test void listsSpacesWithAllFiltersAndPagination() throws Exception {
        var space = room(true);
        mvc.perform(get("/api/spaces").param("venue", "STUDENT_CENTER").param("type", "STUDY_ROOM")
                        .param("bookingEnabled", "true").param("minCapacity", "5").param("q", "상상관")
                        .param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == " + space.getId() + ")]").exists())
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/api/spaces").param("q", "NO_MATCH_%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void missingSpaceIs404AndBadPaginationIs400() throws Exception {
        mvc.perform(get("/api/spaces/9223372036854775807")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        mvc.perform(get("/api/spaces").param("size", "1000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mvc.perform(get("/api/spaces").param("minCapacity", "-1")).andExpect(status().isBadRequest());
    }

    @Test void createsReservationWithMembersAndLocation() throws Exception {
        var u = user("STUDENT");
        var space = room(true);
        var start = future();
        var input = new ReservationCreateRequest(space.getId(), null, start, start.plusSeconds(3600),
                "팀 학습", ReservationKind.BOOKING, List.of(new ReservationMemberRequest(unique(), "참여자")));
        var result = create(u, input).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.userId").value(u.getId()))
                .andExpect(jsonPath("$.status").value("UPCOMING")).andExpect(jsonPath("$.members.length()").value(1))
                .andReturn();
        assertThat(members.findByReservationIdOrderById(id(result))).hasSize(1);
        mvc.perform(as(get("/api/reservations/" + id(result)), u)).andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("참여자"));
    }

    @Test void rejectsInvalidTimeAndDisabledSpace() throws Exception {
        var u = user("STUDENT");
        var start = future();
        create(u, booking(room(true), start, start.minusSeconds(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_TIME"));
        create(u, booking(room(false), start, start.plusSeconds(3600))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_DISABLED"));
        create(u, new ReservationCreateRequest(Long.MAX_VALUE, null, start, start.plusSeconds(3600),
                null, ReservationKind.BOOKING, List.of())).andExpect(status().isNotFound());
    }

    @Test void rejectsOverlapButAllowsAdjacentReservations() throws Exception {
        var u = user("STUDENT");
        var space = room(true);
        var start = future();
        create(u, booking(space, start, start.plusSeconds(7200))).andExpect(status().isCreated());
        create(u, booking(space, start.plusSeconds(3600), start.plusSeconds(10800)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        create(u, booking(space, start.minusSeconds(3600), start)).andExpect(status().isCreated());
        create(u, booking(space, start.plusSeconds(7200), start.plusSeconds(10800))).andExpect(status().isCreated());
    }

    @Test void cancelsWithoutDeletingAndMakesTimeAvailableAgain() throws Exception {
        var u = user("STUDENT");
        var space = room(true);
        var start = future();
        var reservation = reservationService.create(u.getId(), booking(space, start, start.plusSeconds(3600)));
        mvc.perform(as(delete("/api/reservations/" + reservation.id()), u).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/reservations/" + reservation.id()), u).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        var saved = reservations.findById(reservation.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.getCancelledAt()).isNotNull();
        create(u, booking(space, start, start.plusSeconds(3600))).andExpect(status().isCreated());
    }

    @Test void reservationOwnershipIsEnforcedOnListDetailAndCancel() throws Exception {
        var owner = user("STUDENT");
        var other = user("STUDENT");
        var start = future();
        var reservation = reservationService.create(owner.getId(), booking(room(true), start, start.plusSeconds(3600)));
        mvc.perform(as(get("/api/reservations/" + reservation.id()), other)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/reservations/" + reservation.id()), other).with(csrf().asHeader()))
                .andExpect(status().isForbidden());
        mvc.perform(as(get("/api/reservations"), other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get("/api/reservations"), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reservation.id()));
    }

    @Test void checksParticipantsCapacityAndInputValidation() throws Exception {
        var u = user("STUDENT");
        var space = room(true);
        var start = future();
        var duplicates = List.of(new ReservationMemberRequest(u.getStudentId(), "중복"));
        create(u, new ReservationCreateRequest(space.getId(), null, start, start.plusSeconds(3600),
                null, ReservationKind.BOOKING, duplicates)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MEMBER"));
        var tooMany = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new ReservationMemberRequest(unique(), "참여자")).toList();
        create(u, new ReservationCreateRequest(space.getId(), null, start, start.plusSeconds(3600),
                null, ReservationKind.BOOKING, tooMany)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACITY_VIOLATION"));
        mvc.perform(as(post("/api/reservations"), u).with(csrf().asHeader()).contentType("application/json")
                        .content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void seatReservationsAreIndependentAndValidateMembershipAndStatus() throws Exception {
        var u = user("STUDENT");
        var space = room(true);
        var a = seats.saveAndFlush(new Seat(space.getId(), "A", "AVAILABLE"));
        var b = seats.saveAndFlush(new Seat(space.getId(), "B", "AVAILABLE"));
        var disabled = seats.saveAndFlush(new Seat(space.getId(), "C", "DISABLED"));
        var foreignSeat = seats.saveAndFlush(new Seat(room(true).getId(), "D", "AVAILABLE"));
        var start = future();
        var end = start.plusSeconds(3600);
        create(u, seatBooking(space, a, start, end)).andExpect(status().isCreated());
        create(u, seatBooking(space, a, start, end)).andExpect(status().isConflict());
        create(u, seatBooking(space, b, start, end)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_USING_SEAT"));
        create(user("STUDENT"), seatBooking(space, b, start, end)).andExpect(status().isCreated());
        create(u, seatBooking(space, disabled, start, end)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_DISABLED"));
        create(u, seatBooking(space, foreignSeat, start, end)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_SPACE_MISMATCH"));
        create(u, booking(space, start, end)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_REQUIRED"));
        mvc.perform(get("/api/spaces/" + space.getId() + "/seats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test void availabilitySubtractsReservationsAndBlocksWithoutExposingPeople() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var u = user("STUDENT");
        var start = future();
        reservationService.create(u.getId(), booking(space, start.plusSeconds(3600), start.plusSeconds(7200)));
        blockService.create(admin.getId(), space.getId(),
                new SpaceBlockCreateRequest(start.plusSeconds(10800), start.plusSeconds(14400), "점검"));
        mvc.perform(get("/api/spaces/" + space.getId() + "/availability")
                        .param("startTime", start.toString()).param("endTime", start.plusSeconds(18000).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.policyScope").value("OCCUPANCY_ONLY"))
                .andExpect(jsonPath("$.available.length()").value(3))
                .andExpect(jsonPath("$.available[0].startTime").value(start.toString()))
                .andExpect(jsonPath("$.available[0].endTime").value(start.plusSeconds(3600).toString()))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test void blockPreventsBookingAndReleasePreservesRow() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var u = user("STUDENT");
        var start = future();
        var input = new SpaceBlockCreateRequest(start, start.plusSeconds(3600), "점검");
        var result = mvc.perform(as(post("/api/admin/spaces/" + space.getId() + "/blocks"), admin)
                        .with(csrf().asHeader()).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn();
        create(u, booking(space, start, start.plusSeconds(3600))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPACE_BLOCKED"));
        mvc.perform(as(get("/api/admin/spaces/" + space.getId() + "/blocks"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(delete("/api/admin/space-blocks/" + id(result)), admin).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        assertThat(blocks.findById(id(result)).orElseThrow().getCancelledAt()).isNotNull();
        create(u, booking(space, start, start.plusSeconds(3600))).andExpect(status().isCreated());
    }

    @Test void blockCannotInvalidateExistingReservation() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var start = future();
        reservationService.create(user("STUDENT").getId(), booking(space, start, start.plusSeconds(3600)));
        mvc.perform(as(post("/api/admin/spaces/" + space.getId() + "/blocks"), admin).with(csrf().asHeader())
                        .contentType("application/json").content(json.writeValueAsString(
                                new SpaceBlockCreateRequest(start, start.plusSeconds(3600), "점검"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
    }

    @Test void administratorRoleAndSpaceScopeAreBothRequired() throws Exception {
        var space = room(true);
        var managed = manager(space);
        var unassigned = user("ADMIN");
        var student = user("STUDENT");
        mvc.perform(get("/api/admin/spaces")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/admin/spaces"), student)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/spaces"), unassigned)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(patch("/api/admin/spaces/" + space.getId()), unassigned).with(csrf().asHeader())
                        .contentType("application/json").content("{\"bookingEnabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(patch("/api/admin/spaces/" + space.getId()), managed).with(csrf().asHeader())
                        .contentType("application/json").content("{\"bookingEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingEnabled").value(false));
        assertThat(spaces.findById(space.getId()).orElseThrow().getName()).isEqualTo("스터디룸");
    }

    @Test void newSpaceIsScopedToItsCreatorAndPatchChecksCombinedCapacity() throws Exception {
        var admin = user("ADMIN");
        var input = new SpaceCreateRequest(unique(), "새 공간", "STUDY_ROOM", "미래관",
                2, 6, "STUDENT_CENTER", null, true);
        var result = mvc.perform(as(post("/api/admin/spaces"), admin).with(csrf().asHeader())
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn();
        assertThat(permissions.existsByUserIdAndSpaceId(admin.getId(), id(result))).isTrue();
        mvc.perform(as(patch("/api/admin/spaces/" + id(result)), admin).with(csrf().asHeader())
                        .contentType("application/json").content("{\"minCapacity\":7}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CAPACITY"));
        mvc.perform(as(patch("/api/admin/spaces/" + id(result)), admin).with(csrf().asHeader())
                        .contentType("application/json").content("{\"name\":\"   \"}")).andExpect(status().isBadRequest());
    }

    @Test void managedReservationListAndDetailNeverCrossSpaceScope() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var other = room(true);
        var start = future();
        var u = user("STUDENT");
        var mine = reservationService.create(u.getId(), booking(space, start, start.plusSeconds(3600)));
        var outside = reservationService.create(u.getId(), booking(other, start, start.plusSeconds(3600)));
        mvc.perform(as(get("/api/admin/reservations"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(mine.id()));
        mvc.perform(as(get("/api/admin/reservations/" + outside.id()), admin)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/reservations/" + mine.id()), admin)).andExpect(status().isOk());
    }

    @Test void csrfIsRequiredForStateChanges() throws Exception {
        var u = user("STUDENT");
        mvc.perform(as(post("/api/reservations"), u).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void loginUsesBcryptSessionRotationAndLogoutInvalidatesAuthentication() throws Exception {
        var u = user("STUDENT");
        String password = "Test-password-123!";
        credentials.saveAndFlush(new UserCredential(u.getId(), encoder.encode(password)));
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie tokenCookie = tokenResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(tokenCookie).isNotNull();
        var initialSession = new MockHttpSession();
        String originalSessionId = initialSession.getId();
        var login = mvc.perform(post("/api/auth/login").session(initialSession).cookie(tokenCookie)
                        .header("X-XSRF-TOKEN", tokenCookie.getValue()).contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest(u.getStudentId(), password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(u.getId()))
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(u.getStudentId()));
        var freshToken = mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(freshToken).isNotNull();
        mvc.perform(post("/api/auth/logout").session(session).cookie(freshToken)
                        .header("X-XSRF-TOKEN", freshToken.getValue())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void usersWithoutProvisionedCredentialsAndWrongPasswordsCannotLogin() throws Exception {
        var u = user("STUDENT");
        mvc.perform(post("/api/auth/login").with(csrf().asHeader()).contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest(u.getStudentId(), "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        credentials.saveAndFlush(new UserCredential(u.getId(), encoder.encode("correct-password")));
        mvc.perform(post("/api/auth/login").with(csrf().asHeader()).contentType("application/json")
                        .content(json.writeValueAsString(new LoginRequest(u.getStudentId(), "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test void concurrentOverlappingReservationsCommitExactlyOneRow() throws Exception {
        var space = room(true);
        var a = user("STUDENT");
        var b = user("STUDENT");
        var start = future();
        var input = booking(space, start, start.plusSeconds(3600));
        var outcomes = race(() -> reservationService.create(a.getId(), input),
                () -> reservationService.create(b.getId(), input));
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "RESERVATION_CONFLICT");
        assertThat(jdbc.queryForObject("select count(*) from reservations where space_id = ?", Integer.class, space.getId()))
                .isEqualTo(1);
    }

    @Test void reservationAndBlockCreationShareTheSameDatabaseLock() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var u = user("STUDENT");
        var start = future();
        var outcomes = race(
                () -> reservationService.create(u.getId(), booking(space, start, start.plusSeconds(3600))),
                () -> blockService.create(admin.getId(), space.getId(),
                        new SpaceBlockCreateRequest(start, start.plusSeconds(3600), "점검")));
        assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(outcomes).anyMatch(s -> s.equals("SPACE_BLOCKED") || s.equals("RESERVATION_CONFLICT"));
        Integer count = jdbc.queryForObject("""
                select (select count(*) from reservations where space_id = ?)
                     + (select count(*) from space_blocks where space_id = ?)
                """, Integer.class, space.getId(), space.getId());
        assertThat(count).isEqualTo(1);
    }

    @Test void swaggerAndCorsAreAvailableForDevelopment() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/reservations'].post").exists());
        mvc.perform(options("/api/reservations").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/reservations").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    private OperatingPolicyInput operatingPolicy(boolean enabled, java.time.LocalDate examDate) {
        var hours = new ArrayList<OperatingHoursInput>();
        for (var period : OperatingPeriod.values()) for (var day : java.time.DayOfWeek.values()) {
            hours.add(new OperatingHoursInput(period, day, false,
                    period == OperatingPeriod.EXAM ? "00:00" : "09:00",
                    period == OperatingPeriod.EXAM ? "24:00" : "18:00"));
        }
        return new OperatingPolicyInput(enabled, OperatingPeriod.SEMESTER, examDate, examDate, hours);
    }
    private ResultActions savePolicy(User admin, Space space, OperatingPolicyInput input) throws Exception {
        return mvc.perform(as(put("/api/admin/spaces/" + space.getId() + "/policy"), admin)
                .with(csrf().asHeader()).contentType("application/json").content(json.writeValueAsString(input)));
    }
    @Test void operatingPolicyRequiresAdminScopeAndCsrf() throws Exception {
        var space = room(true);
        var admin = manager(space);
        var input = operatingPolicy(true, null);
        mvc.perform(get("/api/spaces/" + space.getId() + "/policy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
        savePolicy(user("STUDENT"), space, input).andExpect(status().isForbidden());
        savePolicy(user("ADMIN"), space, input).andExpect(status().isForbidden());
        mvc.perform(as(put("/api/admin/spaces/" + space.getId() + "/policy"), admin)
                .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isForbidden());
        savePolicy(admin, space, input).andExpect(status().isOk()).andExpect(jsonPath("$.hours.length()").value(21));
        mvc.perform(options("/api/admin/spaces/" + space.getId() + "/policy").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT").header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk());
    }
    @Test void operatingPolicyFiltersAvailabilityAndValidatesNewReservationsWithoutChangingExistingOnes() throws Exception {
        var space = room(true); var admin = manager(space); var student = user("STUDENT");
        var date = java.time.LocalDate.now(OperatingCalendar.ZONE).plusDays(4);
        var early = date.atTime(7, 0).atZone(OperatingCalendar.ZONE).toInstant();
        var existing = create(student, booking(space, early, early.plusSeconds(3600))).andExpect(status().isCreated()).andReturn();
        savePolicy(admin, space, operatingPolicy(true, null)).andExpect(status().isOk());
        create(student, booking(space, early.plusSeconds(3600), early.plusSeconds(5400)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUTSIDE_OPERATING_HOURS"));
        var from = date.atStartOfDay(OperatingCalendar.ZONE).toInstant();
        mvc.perform(get("/api/spaces/" + space.getId() + "/availability").param("startTime", from.toString())
                .param("endTime", from.plusSeconds(86400).toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.policyScope").value("OPERATING_HOURS_AND_OCCUPANCY"))
                .andExpect(jsonPath("$.available[0].startTime").value(date.atTime(9, 0).atZone(OperatingCalendar.ZONE).toInstant().toString()));
        savePolicy(admin, space, operatingPolicy(true, date)).andExpect(status().isOk());
        create(student, booking(space, early.plusSeconds(3600), early.plusSeconds(5400))).andExpect(status().isCreated());
        assertThat(reservations.findById(id(existing)).orElseThrow().getStatus()).isEqualTo(ReservationStatus.UPCOMING);
        savePolicy(admin, space, operatingPolicy(false, null)).andExpect(status().isOk());
        create(student, booking(space, early.plusSeconds(5400), early.plusSeconds(6000))).andExpect(status().isCreated());
    }
    @Test void malformedOperatingPoliciesAreRejected() throws Exception {
        var space = room(true); var admin = manager(space);
        var input = operatingPolicy(true, null);
        var duplicate = new ArrayList<>(input.hours()); duplicate.set(1, duplicate.getFirst());
        savePolicy(admin, space, new OperatingPolicyInput(true, OperatingPeriod.SEMESTER, null, null, duplicate))
                .andExpect(status().isBadRequest());
        var invalid = new ArrayList<>(input.hours());
        invalid.set(0, new OperatingHoursInput(OperatingPeriod.SEMESTER, java.time.DayOfWeek.MONDAY, false, "25:00", "24:00"));
        savePolicy(admin, space, new OperatingPolicyInput(true, OperatingPeriod.SEMESTER, null, null, invalid))
                .andExpect(status().isBadRequest());
        savePolicy(admin, space, new OperatingPolicyInput(true, OperatingPeriod.EXAM, null, null, input.hours()))
                .andExpect(status().isBadRequest());
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
                try {
                    action.call();
                    return "OK";
                } catch (ApiException e) {
                    return e.getCode();
                }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var result = new ArrayList<String>();
            for (var future : futures) result.add(future.get(15, TimeUnit.SECONDS));
            return result;
        }
    }
}
