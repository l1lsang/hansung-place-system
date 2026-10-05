package com.hansung.hsp;

import com.hansung.hsp.admin.*;
import com.hansung.hsp.auth.*;
import com.hansung.hsp.common.ApiException;
import com.hansung.hsp.reservation.*;
import com.hansung.hsp.space.*;
import com.hansung.hsp.user.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
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
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select type from flyway_schema_history where version='1'", String.class))
                .isEqualTo("SQL");
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
        create(u, seatBooking(space, b, start, end)).andExpect(status().isCreated());
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
