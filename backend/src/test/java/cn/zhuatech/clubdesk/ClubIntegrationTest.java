// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 真HTTP/JPA开卡收款、预留核销、扫码、退费、门店与本人隔离及竞争写入测试。所有档案均为TEST。官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClubIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();
  static final Instant NOW = Instant.parse("2026-10-06T04:00:00Z");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:club;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("clubdesk.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, member, other, trainer;
  String suffix;
  long memberId, otherId, coachId, roomId, courseId, planId, categoryId;

  @BeforeEach
  void setup() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    categoryId =
        call(
                admin,
                "/admin/dictionaries",
                "POST",
                m(
                    "type",
                    "courseCategory",
                    "code",
                    "CAT" + suffix,
                    "name",
                    "TEST category",
                    "nameEn",
                    "TEST category",
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    memberId = createMember("M" + suffix, 1);
    otherId = createMember("O" + suffix, 1);
    coachId = createCoach("C" + suffix);
    roomId = createRoom("TEST room " + suffix);
    courseId =
        call(
                admin,
                "/master/courses",
                "POST",
                m(
                    "departmentId",
                    1,
                    "name",
                    "TEST class " + suffix,
                    "nameEn",
                    "TEST class",
                    "categoryId",
                    categoryId,
                    "durationMinutes",
                    15,
                    "capacity",
                    4,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    planId = createPlan("CREDITS", 3, 30, "100.01");
    user("member" + suffix, "Member", 1, memberId, null);
    user("other" + suffix, "Member", 1, otherId, null);
    user("coach" + suffix, "Coach", 1, null, coachId);
    member = login("member" + suffix);
    other = login("other" + suffix);
    trainer = login("coach" + suffix);
  }

  Map<String, Object> m(Object... values) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) map.put(values[i].toString(), values[i + 1]);
    return map;
  }

  JsonNode call(MockHttpSession session, String path, String method, Object body, int expected)
      throws Exception {
    var request =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (session != null) request.session(session);
    if (!method.equals("GET")) request.with(csrf());
    if (body != null)
      request.contentType("application/json").content(json.writeValueAsString(body));
    var response = mvc.perform(request).andReturn().getResponse();
    assertEquals(
        expected, response.getStatus(), method + path + " " + response.getContentAsString());
    return json.readTree(
        response.getContentAsString().isBlank() ? "{}" : response.getContentAsString());
  }

  MockHttpSession login(String name) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", name, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  long role(String name) throws Exception {
    for (var row : call(admin, "/admin/roles", "GET", null, 200))
      if (row.get("name").asString().contains(name)) return row.get("id").asLong();
    throw new AssertionError(name);
  }

  long user(String name, String role, long branch, Long member, Long coach) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "password",
                PASSWORD,
                "roleId",
                role(role),
                "departmentId",
                branch,
                "memberId",
                member,
                "coachId",
                coach,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  long createMember(String code, long branch) throws Exception {
    return call(
            admin,
            "/master/members",
            "POST",
            m(
                "code",
                code,
                "name",
                "TEST " + code,
                "departmentId",
                branch,
                "contactNote",
                "TEST private note",
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  long createCoach(String code) throws Exception {
    return call(
            admin,
            "/master/coaches",
            "POST",
            m(
                "code",
                code,
                "name",
                "TEST " + code,
                "departmentId",
                1,
                "specialty",
                "TEST strength",
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  long createRoom(String name) throws Exception {
    return call(
            admin,
            "/master/rooms",
            "POST",
            m("name", name, "departmentId", 1, "capacity", 4, "enabled", true),
            200)
        .get("id")
        .asLong();
  }

  long createPlan(String mode, int credits, int days, String price) throws Exception {
    return call(
            admin,
            "/master/plans",
            "POST",
            m(
                "name",
                "TEST plan " + suffix,
                "nameEn",
                "TEST plan",
                "departmentId",
                1,
                "mode",
                mode,
                "credits",
                credits,
                "validDays",
                days,
                "price",
                price,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  JsonNode issue(long member, long plan, LocalDate date) throws Exception {
    return call(
        admin,
        "/passes",
        "POST",
        m(
            "memberId",
            member,
            "planId",
            plan,
            "startsOn",
            date.toString(),
            "note",
            "TEST agreed terms"),
        200);
  }

  JsonNode pass(long id) throws Exception {
    return call(admin, "/passes/" + id, "GET", null, 200).get("summary").get("pass");
  }

  JsonNode passDetail(long id) throws Exception {
    return call(admin, "/passes/" + id, "GET", null, 200);
  }

  JsonNode cash(long id, String kind, String amount, Long original, int code) throws Exception {
    return call(
        admin,
        "/passes/" + id + "/cash",
        "POST",
        m(
            "kind",
            kind,
            "reference",
            "TEST-" + UUID.randomUUID(),
            "amount",
            amount,
            "originalId",
            original,
            "note",
            "TEST external fact",
            "revision",
            pass(id).get("revision").asLong()),
        code);
  }

  JsonNode paid(long member) throws Exception {
    var p = issue(member, planId, LocalDate.parse("2026-10-06"));
    cash(p.get("id").asLong(), "RECEIPT", "100.01", null, 200);
    return pass(p.get("id").asLong());
  }

  JsonNode createSession(int minutes, int capacity, long coach, long room, int expected)
      throws Exception {
    var local =
        NOW.plusSeconds(minutes * 60L)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDateTime()
            .toString();
    return call(
        admin,
        "/sessions",
        "POST",
        m(
            "departmentId",
            1,
            "courseId",
            courseId,
            "coachId",
            coach,
            "roomId",
            room,
            "localStart",
            local,
            "capacity",
            capacity,
            "cancelHours",
            0,
            "note",
            "TEST class"),
        expected);
  }

  JsonNode session(long id) throws Exception {
    return call(admin, "/sessions/" + id, "GET", null, 200).get("summary").get("session");
  }

  JsonNode sessionAction(long id, String action, int code) throws Exception {
    return call(
        admin,
        "/sessions/" + id + "/" + action,
        "POST",
        m("revision", session(id).get("revision").asLong(), "note", "TEST verified class action"),
        code);
  }

  JsonNode published(int minutes, int seats) throws Exception {
    var s = createSession(minutes, seats, coachId, roomId, 200);
    return sessionAction(s.get("id").asLong(), "publish", 200);
  }

  JsonNode book(MockHttpSession client, long member, long card, long cls, int expected)
      throws Exception {
    return call(
        client,
        client == admin ? "/reservations" : "/portal/reservations",
        "POST",
        m(
            "memberId",
            member,
            "passId",
            card,
            "sessionId",
            cls,
            "passRevision",
            pass(card).get("revision").asLong(),
            "sessionRevision",
            session(cls).get("revision").asLong()),
        expected);
  }

  JsonNode booking(long id) throws Exception {
    return call(admin, "/reservations/" + id, "GET", null, 200).get("booking");
  }

  JsonNode bookingAction(MockHttpSession client, long id, String action, int code)
      throws Exception {
    return call(
        client,
        (client == member || client == other ? "/portal" : "")
            + "/reservations/"
            + id
            + "/"
            + action,
        "POST",
        m("revision", booking(id).get("revision").asLong(), "note", "TEST physically verified"),
        code);
  }

  JsonNode passAction(long id, String action, Integer days, int code) throws Exception {
    return call(
        admin,
        "/passes/" + id + "/" + action,
        "POST",
        m(
            "revision",
            pass(id).get("revision").asLong(),
            "note",
            "TEST agreed action",
            "days",
            days),
        code);
  }

  @Test
  void fullMembershipPartialReceiptBookingQrAttendanceAndFinish() throws Exception {
    var p = issue(memberId, planId, LocalDate.parse("2026-10-06"));
    long pid = p.get("id").asLong();
    var s = published(10, 2);
    long sid = s.get("id").asLong();
    assertEquals("PASS_NOT_VALID", book(member, memberId, pid, sid, 409).get("code").asString());
    cash(pid, "RECEIPT", "40.00", null, 200);
    assertEquals("DRAFT", pass(pid).get("state").asString());
    cash(pid, "RECEIPT", "60.01", null, 200);
    assertEquals("ACTIVE", pass(pid).get("state").asString());
    var b = book(member, memberId, pid, sid, 200);
    assertEquals(1, pass(pid).get("held").asInt());
    var token = call(member, "/portal/check-token", "POST", m(), 200);
    assertTrue(token.get("token").asString().startsWith("CLUB1:"));
    call(
        admin,
        "/checkin",
        "POST",
        m("token", token.get("token").asString(), "sessionId", sid),
        200);
    assertEquals("ATTENDED", booking(b.get("id").asLong()).get("state").asString());
    assertEquals(0, pass(pid).get("held").asInt());
    assertEquals(1, pass(pid).get("used").asInt());
    call(
        admin,
        "/checkin",
        "POST",
        m("token", token.get("token").asString(), "sessionId", sid),
        409);
    assertEquals(1, pass(pid).get("used").asInt());
    when(clock.instant()).thenReturn(NOW.plusSeconds(26 * 60));
    sessionAction(sid, "finish", 200);
    assertEquals(3, passDetail(pid).get("credits").size());
  }

  @Test
  void cancellationReleasesHoldAndRebookingHasImmutableHistory() throws Exception {
    long p = paid(memberId).get("id").asLong(), s = published(24 * 60, 2).get("id").asLong();
    var b = book(member, memberId, p, s, 200);
    long id = b.get("id").asLong();
    book(member, memberId, p, s, 409);
    bookingAction(member, id, "cancel", 200);
    assertEquals(0, pass(p).get("held").asInt());
    var again = book(member, memberId, p, s, 200);
    assertEquals(id, again.get("id").asLong());
    assertEquals(1, pass(p).get("held").asInt());
    assertEquals(3, call(member, "/reservations/" + id, "GET", null, 200).get("events").size());
  }

  @Test
  void competingLastSeatOnlyOneBookingCommits() throws Exception {
    long p1 = paid(memberId).get("id").asLong(),
        p2 = paid(otherId).get("id").asLong(),
        s = published(10, 1).get("id").asLong();
    var payloads =
        List.of(
            m(
                "memberId",
                memberId,
                "passId",
                p1,
                "sessionId",
                s,
                "passRevision",
                pass(p1).get("revision").asLong(),
                "sessionRevision",
                session(s).get("revision").asLong()),
            m(
                "memberId",
                otherId,
                "passId",
                p2,
                "sessionId",
                s,
                "passRevision",
                pass(p2).get("revision").asLong(),
                "sessionRevision",
                session(s).get("revision").asLong()));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var results =
          pool.invokeAll(
              List.of(
                  () -> rawStatus(member, "/portal/reservations", payloads.get(0)),
                  () -> rawStatus(other, "/portal/reservations", payloads.get(1))));
      var codes = new ArrayList<Integer>();
      for (var result : results) codes.add((Integer) result.get());
      Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
      assertEquals(1, pass(p1).get("held").asInt() + pass(p2).get("held").asInt());
      assertEquals(1, call(admin, "/sessions/" + s, "GET", null, 200).get("roster").size());
    } finally {
      pool.shutdownNow();
    }
  }

  int rawStatus(MockHttpSession client, String path, Object body) throws Exception {
    return mvc.perform(
            post("/api" + path)
                .session(client)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void receiptsRejectOverpaymentRoundingAndDuplicateReference() throws Exception {
    long p = issue(memberId, planId, LocalDate.parse("2026-10-06")).get("id").asLong();
    cash(p, "RECEIPT", "100.02", null, 409);
    cash(p, "RECEIPT", "1.001", null, 400);
    assertEquals(0, passDetail(p).get("cash").size());
    var v =
        m(
            "kind",
            "RECEIPT",
            "reference",
            "Test-Unique-" + suffix,
            "amount",
            "10.00",
            "note",
            "TEST real receipt",
            "revision",
            pass(p).get("revision").asLong());
    call(admin, "/passes/" + p + "/cash", "POST", v, 200);
    v.put("reference", v.get("reference").toString().toLowerCase());
    v.put("revision", pass(p).get("revision").asLong());
    assertEquals(
        "DUPLICATE_REFERENCE",
        call(admin, "/passes/" + p + "/cash", "POST", v, 409).get("code").asString());
    assertEquals("90.01", passDetail(p).get("summary").get("due").asString());
  }

  @Test
  void closeCancelsBookingsAndRefundCannotExceedOriginalReceipt() throws Exception {
    long p = paid(memberId).get("id").asLong(), s = published(10, 2).get("id").asLong();
    var b = book(member, memberId, p, s, 200);
    long receipt = passDetail(p).get("cash").get(0).get("entry").get("id").asLong();
    assertEquals(
        "CLOSE_BEFORE_REFUND", cash(p, "REFUND", "1.00", receipt, 409).get("code").asString());
    passAction(p, "close", null, 200);
    assertEquals("CANCELLED", booking(b.get("id").asLong()).get("state").asString());
    assertEquals(0, pass(p).get("held").asInt());
    cash(p, "REFUND", "30.01", receipt, 200);
    cash(p, "REFUND", "70.00", receipt, 200);
    cash(p, "REFUND", "0.01", receipt, 409);
    assertEquals(
        0,
        new java.math.BigDecimal(passDetail(p).get("summary").get("netPaid").asString())
            .compareTo(java.math.BigDecimal.ZERO));
  }

  @Test
  void reversalsRespectRefundDependencyAndNeverDeleteOriginals() throws Exception {
    long p = paid(memberId).get("id").asLong();
    long receipt = passDetail(p).get("cash").get(0).get("entry").get("id").asLong();
    cash(p, "REVERSAL", "100.01", receipt, 409);
    passAction(p, "close", null, 200);
    long refund = cash(p, "REFUND", "20.00", receipt, 200).get("id").asLong();
    cash(p, "REVERSAL", "100.01", receipt, 409);
    cash(p, "REVERSAL", "20.00", refund, 200);
    cash(p, "REVERSAL", "20.00", refund, 409);
    cash(p, "REVERSAL", "100.01", receipt, 200);
    assertEquals(4, passDetail(p).get("cash").size());
    assertEquals(
        0,
        new java.math.BigDecimal(passDetail(p).get("summary").get("netPaid").asString())
            .compareTo(java.math.BigDecimal.ZERO));
  }

  @Test
  void partiallyPaidDraftCanCloseAndRefundActualReceipt() throws Exception {
    long p = issue(memberId, planId, LocalDate.parse("2026-10-06")).get("id").asLong();
    long receipt = cash(p, "RECEIPT", "12.35", null, 200).get("id").asLong();
    passAction(p, "close", null, 200);
    cash(p, "REFUND", "12.35", receipt, 200);
    assertEquals("CLOSED", pass(p).get("state").asString());
    assertEquals(
        0,
        new java.math.BigDecimal(passDetail(p).get("summary").get("netPaid").asString())
            .compareTo(java.math.BigDecimal.ZERO));
    assertEquals(0, passDetail(p).get("credits").size());
  }

  @Test
  void freezesRejectPendingBookingsAndExtendOnlyOncePerVersion() throws Exception {
    long p = paid(memberId).get("id").asLong(), s = published(24 * 60, 2).get("id").asLong();
    var b = book(member, memberId, p, s, 200);
    passAction(p, "freeze", 7, 409);
    bookingAction(member, b.get("id").asLong(), "cancel", 200);
    var old = pass(p);
    var frozen = passAction(p, "freeze", 7, 200);
    assertEquals(
        LocalDate.parse(old.get("endsOn").asString()).plusDays(7).toString(),
        frozen.get("endsOn").asString());
    call(
        admin,
        "/passes/" + p + "/freeze",
        "POST",
        m("revision", old.get("revision").asLong(), "days", 7, "note", "TEST duplicate"),
        409);
    assertEquals("FROZEN", passDetail(p).get("summary").get("effective").asString());
    book(member, memberId, p, s, 409);
    var future = published(8 * 24 * 60, 2);
    book(member, memberId, p, future.get("id").asLong(), 200);
    when(clock.instant()).thenReturn(NOW.plusSeconds(8L * 86400));
    assertEquals("ACTIVE", passDetail(p).get("summary").get("effective").asString());
  }

  @Test
  void earlyUnfreezeCannotInvalidateFutureBooking() throws Exception {
    long p = paid(memberId).get("id").asLong();
    passAction(p, "freeze", 7, 200);
    long s = published(33 * 24 * 60, 2).get("id").asLong();
    var b = book(member, memberId, p, s, 200);
    passAction(p, "unfreeze", null, 409);
    bookingAction(member, b.get("id").asLong(), "cancel", 200);
    passAction(p, "unfreeze", null, 200);
    assertEquals("2026-11-04", pass(p).get("endsOn").asString());
    assertEquals(0, pass(p).get("freezeDays").asInt());
  }

  @Test
  void periodEntryChoosesValidCardOncePerBranchDay() throws Exception {
    long plan = createPlan("PERIOD", 0, 30, "59.99"),
        p = issue(memberId, plan, LocalDate.parse("2026-10-06")).get("id").asLong();
    cash(p, "RECEIPT", "59.99", null, 200);
    var token = call(member, "/portal/check-token", "POST", m(), 200);
    call(admin, "/checkin", "POST", m("token", token.get("token").asString()), 200);
    assertEquals(1, call(member, "/portal", "GET", null, 200).get("visits").size());
    assertTrue(passDetail(p).get("summary").get("available").isNull());
    assertEquals(0, pass(p).get("used").asInt());
    call(admin, "/visits", "POST", m("memberId", memberId, "passId", p), 409);
    when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
    call(admin, "/visits", "POST", m("memberId", memberId, "passId", p), 200);
    assertEquals(2, call(member, "/portal", "GET", null, 200).get("visits").size());
  }

  @Test
  void classPackDoesNotImplyUnlimitedVenueEntry() throws Exception {
    long p = paid(memberId).get("id").asLong();
    assertEquals(
        "PERIOD_PASS_REQUIRED",
        call(admin, "/visits", "POST", m("memberId", memberId, "passId", p), 409)
            .get("code")
            .asString());
  }

  @Test
  void qrExpiryRefreshAndWrongClassDoNotConsumeCredits() throws Exception {
    long p = paid(memberId).get("id").asLong(), s = published(10, 2).get("id").asLong();
    var old = call(member, "/portal/check-token", "POST", m(), 200);
    call(member, "/portal/check-token", "POST", m(), 429);
    call(admin, "/checkin", "POST", m("token", old.get("token").asString(), "sessionId", s), 409);
    book(member, memberId, p, s, 200);
    when(clock.instant()).thenReturn(NOW.plusSeconds(6));
    var fresh = call(member, "/portal/check-token", "POST", m(), 200);
    call(admin, "/checkin", "POST", m("token", old.get("token").asString(), "sessionId", s), 409);
    when(clock.instant()).thenReturn(NOW.plusSeconds(127));
    call(admin, "/checkin", "POST", m("token", fresh.get("token").asString(), "sessionId", s), 409);
    assertEquals(0, pass(p).get("used").asInt());
    assertEquals(1, pass(p).get("held").asInt());
  }

  @Test
  void memberPortalNeverExposesOtherMemberOrEmployeeData() throws Exception {
    long own = paid(memberId).get("id").asLong(),
        theirs = paid(otherId).get("id").asLong(),
        s = published(10, 2).get("id").asLong();
    var b = book(other, otherId, theirs, s, 200);
    var portal = call(member, "/portal", "GET", null, 200);
    assertEquals(memberId, portal.get("member").get("id").asLong());
    assertEquals(1, portal.get("passes").size());
    assertEquals(own, portal.get("passes").get(0).get("pass").get("id").asLong());
    assertEquals(0, portal.get("reservations").size());
    for (var path :
        List.of(
            "/workspace",
            "/admin/users",
            "/reports?from=2026-10-01&to=2026-10-31",
            "/audit",
            "/sessions/" + s,
            "/passes/" + theirs,
            "/reservations/" + b.get("id").asLong())) call(member, path, "GET", null, 403);
    book(member, otherId, own, s, 403);
  }

  @Test
  void coachesOnlySeeAssignedRosterWithoutContactOrCash() throws Exception {
    long otherCoach = createCoach("D" + suffix),
        otherRoom = createRoom("TEST other room " + suffix);
    var unassigned = createSession(10, 2, otherCoach, otherRoom, 200);
    long sid = sessionAction(unassigned.get("id").asLong(), "publish", 200).get("id").asLong();
    long assigned = published(10, 2).get("id").asLong(), p = paid(memberId).get("id").asLong();
    var b = book(member, memberId, p, assigned, 200);
    var roster = call(trainer, "/sessions/" + assigned, "GET", null, 200);
    assertFalse(roster.toString().contains("contactNote"));
    assertFalse(roster.toString().contains("100.01"));
    assertEquals(1, call(trainer, "/coaching", "GET", null, 200).get("sessions").size());
    call(trainer, "/sessions/" + sid, "GET", null, 403);
    call(trainer, "/passes/" + p, "GET", null, 403);
    bookingAction(trainer, b.get("id").asLong(), "attend", 200);
  }

  @Test
  void branchesAndLiveRolesEnforceServerSideScope() throws Exception {
    long d =
        call(
                admin,
                "/admin/departments",
                "POST",
                m("name", "TEST branch " + suffix, "zone", "Europe/London", "enabled", true),
                200)
            .get("id")
            .asLong();
    createMember("B" + suffix, d);
    user("branch" + suffix, "Branch operations", d, null, null);
    var client = login("branch" + suffix);
    var workspace = call(client, "/workspace", "GET", null, 200);
    assertEquals(1, workspace.get("members").size());
    assertEquals(0, workspace.get("passes").size());
    long p = paid(memberId).get("id").asLong();
    call(client, "/passes/" + p, "GET", null, 403);
    call(
        client,
        "/master/members/" + memberId,
        "PUT",
        m("code", "M" + suffix, "name", "BAD", "departmentId", d, "enabled", true, "revision", 1),
        403);
  }

  @Test
  void scheduleConflictsAndMemberConflictsAreDifferentChecks() throws Exception {
    long first = published(10, 2).get("id").asLong();
    assertEquals(
        "SCHEDULE_CONFLICT", createSession(11, 2, coachId, roomId, 409).get("code").asString());
    long coach2 = createCoach("E" + suffix), room2 = createRoom("TEST room2 " + suffix);
    long second = createSession(11, 2, coach2, room2, 200).get("id").asLong();
    sessionAction(second, "publish", 200);
    long p = paid(memberId).get("id").asLong();
    book(member, memberId, p, first, 200);
    assertEquals(
        "MEMBER_TIME_CONFLICT", book(member, memberId, p, second, 409).get("code").asString());
  }

  @Test
  void sessionLifecyclePreventsEditingBookedOrPublishedTimes() throws Exception {
    long s = published(10, 2).get("id").asLong(), p = paid(memberId).get("id").asLong();
    call(
        admin,
        "/sessions/" + s,
        "PUT",
        m(
            "departmentId",
            1,
            "courseId",
            courseId,
            "coachId",
            coachId,
            "roomId",
            roomId,
            "localStart",
            "2026-10-07T12:00",
            "capacity",
            2,
            "cancelHours",
            0,
            "revision",
            session(s).get("revision").asLong()),
        409);
    book(member, memberId, p, s, 200);
    sessionAction(s, "withdraw", 409);
    sessionAction(s, "cancel", 200);
    assertEquals(0, pass(p).get("held").asInt());
    book(member, memberId, p, s, 409);
  }

  @Test
  void finishConsumesPendingNoShowsAndCorrectionReturnsCredit() throws Exception {
    long s = published(10, 2).get("id").asLong(), p = paid(memberId).get("id").asLong();
    long b = book(member, memberId, p, s, 200).get("id").asLong();
    sessionAction(s, "finish", 409);
    bookingAction(trainer, b, "no-show", 409);
    when(clock.instant()).thenReturn(NOW.plusSeconds(26 * 60));
    call(
        trainer,
        "/sessions/" + s + "/finish",
        "POST",
        m("revision", session(s).get("revision").asLong(), "note", "TEST attendance reconciled"),
        200);
    assertEquals("NO_SHOW", booking(b).get("state").asString());
    assertEquals(1, pass(p).get("used").asInt());
    bookingAction(admin, b, "correct", 200);
    assertEquals(0, pass(p).get("used").asInt());
    assertEquals("CANCELLED", booking(b).get("state").asString());
    assertEquals(4, passDetail(p).get("credits").size());
  }

  @Test
  void cancellationDeadlineIsServerEnforced() throws Exception {
    var raw = createSession(24 * 60, 2, coachId, roomId, 200);
    long s = raw.get("id").asLong();
    call(
        admin,
        "/sessions/" + s,
        "PUT",
        m(
            "departmentId",
            1,
            "courseId",
            courseId,
            "coachId",
            coachId,
            "roomId",
            roomId,
            "localStart",
            "2026-10-07T12:00",
            "capacity",
            2,
            "cancelHours",
            12,
            "revision",
            raw.get("revision").asLong()),
        200);
    sessionAction(s, "publish", 200);
    long p = paid(memberId).get("id").asLong(),
        b = book(member, memberId, p, s, 200).get("id").asLong();
    when(clock.instant()).thenReturn(NOW.plusSeconds(12 * 3600));
    bookingAction(member, b, "cancel", 409);
    bookingAction(admin, b, "cancel", 200);
    assertEquals(0, pass(p).get("held").asInt());
  }

  @Test
  void fractionalIntegerAndPriceRequestsDoNotWrite() throws Exception {
    var body =
        m(
            "departmentId",
            1,
            "name",
            "TEST invalid",
            "nameEn",
            "TEST invalid",
            "mode",
            "CREDITS",
            "credits",
            1.5,
            "validDays",
            30,
            "price",
            "100.00",
            "enabled",
            true);
    call(admin, "/master/plans", "POST", body, 400);
    body.put("credits", 1);
    body.put("validDays", 30.2);
    call(admin, "/master/plans", "POST", body, 400);
    var s = createSession(10, 2, coachId, roomId, 200);
    long p = paid(memberId).get("id").asLong();
    call(
        member,
        "/portal/reservations",
        "POST",
        m(
            "sessionId",
            s.get("id").asDouble() + 0.1,
            "passId",
            p,
            "passRevision",
            pass(p).get("revision").asLong(),
            "sessionRevision",
            s.get("revision").asLong()),
        400);
    assertEquals(0, pass(p).get("held").asInt());
  }

  @Test
  void atomicImportRollsBackMembersAndAuditWithRowCause() throws Exception {
    int before = call(admin, "/workspace", "GET", null, 200).get("members").size(),
        events = call(admin, "/audit", "GET", null, 200).size();
    var rows =
        List.of(
            m("code", "IMP" + suffix, "name", "TEST row1", "departmentId", 1, "enabled", true),
            m("code", "IMP" + suffix, "name", "TEST row2", "departmentId", 1, "enabled", true));
    var e = call(admin, "/members/import", "POST", rows, 400);
    assertEquals("IMPORT_INVALID", e.get("code").asString());
    assertEquals(2, e.get("row").asInt());
    assertEquals(before, call(admin, "/workspace", "GET", null, 200).get("members").size());
    assertEquals(events, call(admin, "/audit", "GET", null, 200).size());
  }

  @Test
  void snapshotsAndRenewalDoNotRewriteSoldCard() throws Exception {
    long p = paid(memberId).get("id").asLong();
    call(
        admin,
        "/master/plans/" + planId,
        "PUT",
        m(
            "name",
            "TEST revised plan",
            "nameEn",
            "TEST revised plan",
            "departmentId",
            1,
            "mode",
            "CREDITS",
            "credits",
            8,
            "validDays",
            90,
            "price",
            "120.01",
            "enabled",
            true,
            "revision",
            1),
        200);
    assertEquals(3, pass(p).get("credits").asInt());
    assertEquals("100.01", pass(p).get("price").asString());
    var renewal = issue(memberId, planId, LocalDate.parse("2026-11-05"));
    assertNotEquals(p, renewal.get("id").asLong());
    assertEquals(8, renewal.get("credits").asInt());
    assertEquals("120.01", renewal.get("price").asString());
    assertEquals("2026-11-04", pass(p).get("endsOn").asString());
  }

  @Test
  void accountBindingsCannotBeConvertedAndPasswordsAreNeverRead() throws Exception {
    var users = call(admin, "/admin/users", "GET", null, 200);
    long id = 0;
    for (var u : users) {
      assertFalse(u.has("passwordHash"));
      if (u.get("username").asString().equals("member" + suffix)) id = u.get("id").asLong();
    }
    call(
        admin,
        "/admin/users/" + id,
        "PUT",
        m(
            "username",
            "member" + suffix,
            "displayName",
            "TEST conversion",
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "enabled",
            true,
            "memberId",
            null),
        409);
    call(
        admin,
        "/admin/users",
        "POST",
        m(
            "username",
            "bad" + suffix,
            "displayName",
            "TEST bad",
            "password",
            PASSWORD,
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "enabled",
            true,
            "memberId",
            memberId),
        409);
  }

  @Test
  void revokedPermissionAndResetPasswordAffectExistingSessions() throws Exception {
    long r =
        call(
                admin,
                "/admin/roles",
                "POST",
                m(
                    "name",
                    "TEST reader " + suffix,
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    List.of("read")),
                200)
            .get("id")
            .asLong();
    long u =
        call(
                admin,
                "/admin/users",
                "POST",
                m(
                    "username",
                    "reader" + suffix,
                    "displayName",
                    "TEST reader",
                    "password",
                    PASSWORD,
                    "roleId",
                    r,
                    "departmentId",
                    1,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    var client = login("reader" + suffix);
    call(client, "/workspace", "GET", null, 200);
    call(
        admin,
        "/admin/roles/" + r,
        "PUT",
        m("name", "TEST reader " + suffix, "scope", "DEPARTMENT", "permissions", List.of()),
        200);
    call(client, "/workspace", "GET", null, 403);
    call(
        admin,
        "/admin/users/" + u,
        "PUT",
        m(
            "username",
            "reader" + suffix,
            "displayName",
            "TEST reader",
            "password",
            "Bb8" + UUID.randomUUID(),
            "roleId",
            r,
            "departmentId",
            1,
            "enabled",
            true),
        200);
    call(client, "/auth/me", "GET", null, 401);
  }

  @Test
  void enabledFlagsReferencesAndImmutableTimeZoneAreEnforced() throws Exception {
    long s = published(10, 2).get("id").asLong(), p = paid(memberId).get("id").asLong();
    call(
        admin,
        "/master/rooms/" + roomId,
        "PUT",
        m(
            "name",
            "TEST room " + suffix,
            "departmentId",
            1,
            "capacity",
            1,
            "enabled",
            true,
            "revision",
            1),
        409);
    call(admin, "/master/coaches/" + coachId, "DELETE", null, 400);
    call(admin, "/master/coaches/" + coachId + "?revision=1", "DELETE", null, 409);
    call(
        admin,
        "/admin/departments/1",
        "PUT",
        m("name", "Main studio", "zone", "Europe/London", "enabled", true),
        409);
    call(
        admin,
        "/master/members/" + memberId,
        "PUT",
        m(
            "code",
            "M" + suffix,
            "name",
            "TEST disabled",
            "departmentId",
            1,
            "contactNote",
            "TEST",
            "enabled",
            false,
            "revision",
            1),
        200);
    book(member, memberId, p, s, 409);
    call(member, "/portal/check-token", "POST", m(), 409);
    call(member, "/portal", "GET", null, 200);
  }

  @Test
  void reportTotalsEqualBranchSumsAndCsvOpensOutsideApplication() throws Exception {
    long p = paid(memberId).get("id").asLong();
    var r = call(admin, "/reports?from=2026-10-01&to=2026-10-31", "GET", null, 200);
    var sum = java.math.BigDecimal.ZERO;
    for (var b : r.get("branches"))
      sum = sum.add(new java.math.BigDecimal(b.get("netReceived").asString()));
    assertEquals(
        0, sum.compareTo(new java.math.BigDecimal(r.get("total").get("netReceived").asString())));
    call(
        admin,
        "/master/members/" + memberId,
        "PUT",
        m(
            "code",
            "M" + suffix,
            "name",
            "=TEST formula",
            "departmentId",
            1,
            "contactNote",
            "=TEST multiline\nTEST, quoted note",
            "enabled",
            true,
            "revision",
            1),
        200);
    var res = mvc.perform(get("/api/exports/members.csv").session(admin)).andReturn().getResponse();
    assertEquals(200, res.getStatus());
    assertTrue(res.getContentType().startsWith("text/csv"));
    assertTrue(res.getContentAsString().contains("'=TEST formula"));
    assertTrue(res.getContentAsString().contains("\"'=TEST multiline\nTEST, quoted note\""));
    assertTrue(pass(p).get("reference").asString().startsWith("CARD-"));
  }

  @Test
  void anonymousCsrfBuiltinAndLastAdministratorProtected() throws Exception {
    call(null, "/workspace", "GET", null, 401);
    assertEquals(
        403,
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    var profile = call(admin, "/auth/me", "GET", null, 200);
    call(
        admin,
        "/admin/users/" + profile.get("id").asLong(),
        "PUT",
        m(
            "username",
            "admin",
            "displayName",
            "TEST admin",
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "enabled",
            false),
        409);
    call(admin, "/admin/departments/1", "DELETE", null, 409);
    call(admin, "/admin/settings", "POST", m("code", "fake", "value", "fake"), 400);
    call(
        admin,
        "/admin/users",
        "POST",
        m(
            "username",
            "weak" + suffix,
            "displayName",
            "TEST weak",
            "roleId",
            role("Administrator"),
            "departmentId",
            1,
            "enabled",
            true,
            "password",
            "weak"),
        400);
  }

  @Test
  void menuCannotExposeAnUnrelatedPrivilegedRoute() throws Exception {
    var menus = call(admin, "/admin/menus", "GET", null, 200);
    JsonNode menu = null;
    for (var x : menus) if (x.get("code").asString().equals("admin")) menu = x;
    assertNotNull(menu);
    var body =
        m(
            "name",
            "TEST menu",
            "nameEn",
            "TEST menu",
            "permissionCode",
            "portal",
            "position",
            0,
            "enabled",
            true);
    var error = call(admin, "/admin/menus/" + menu.get("id").asLong(), "PUT", body, 409);
    assertEquals("MENU_PERMISSION_IMMUTABLE", error.get("code").asString());
    assertTrue(
        call(member, "/auth/me", "GET", null, 200).get("menus").toString().contains("portal"));
    assertFalse(
        call(member, "/auth/me", "GET", null, 200).get("menus").toString().contains("admin"));
  }
}
