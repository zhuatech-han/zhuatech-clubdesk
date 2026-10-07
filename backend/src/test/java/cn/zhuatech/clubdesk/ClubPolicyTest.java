// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;

/** 金额、夏令时、完整课次有效期、冻结及取消边界的独立业务回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class ClubPolicyTest {
  @Test
  void moneyDoesNotRoundOrAcceptZero() {
    assertEquals(new BigDecimal("0.10"), ClubPolicy.money(new BigDecimal("0.1")));
    assertEquals(new BigDecimal("1.00"), ClubPolicy.money(new BigDecimal("1.000")));
    assertThrows(Problem.class, () -> ClubPolicy.money(new BigDecimal("1.001")));
    assertThrows(Problem.class, () -> ClubPolicy.money(BigDecimal.ZERO));
    assertThrows(Problem.class, () -> ClubPolicy.money(new BigDecimal("1000000")));
  }

  @Test
  void localTimeRejectsMissingAndRepeatedDst() {
    assertEquals(
        Instant.parse("2026-10-06T04:00:00Z"),
        ClubPolicy.localTime("2026-10-06T12:00", "Asia/Shanghai"));
    assertThrows(Problem.class, () -> ClubPolicy.localTime("2026-03-08T02:30", "America/New_York"));
    assertThrows(Problem.class, () -> ClubPolicy.localTime("2026-11-01T01:30", "America/New_York"));
  }

  @Test
  void validityIncludesFullClassAndExclusiveFreezeEnd() {
    var p = pass();
    var s = new ClubSession();
    s.startsAt = Instant.parse("2026-10-06T15:30:00Z");
    s.endsAt = Instant.parse("2026-10-06T16:00:00Z");
    ClubPolicy.entitlement(p, s, "Asia/Shanghai");
    s.endsAt = s.endsAt.plusSeconds(1);
    assertThrows(Problem.class, () -> ClubPolicy.entitlement(p, s, "Asia/Shanghai"));
    s.endsAt = Instant.parse("2026-10-06T16:00:00Z");
    p.freezeStarted = LocalDate.parse("2026-10-05");
    p.freezeUntil = LocalDate.parse("2026-10-06");
    ClubPolicy.entitlement(p, s, "Asia/Shanghai");
    p.freezeUntil = LocalDate.parse("2026-10-07");
    assertThrows(Problem.class, () -> ClubPolicy.entitlement(p, s, "Asia/Shanghai"));
  }

  @Test
  void periodsHaveNoFakeFiniteCreditBalance() {
    var p = pass();
    p.mode = "PERIOD";
    assertNull(ClubPolicy.available(p));
    p.mode = "CREDITS";
    p.credits = 5;
    p.held = 2;
    p.used = 1;
    assertEquals(2, ClubPolicy.available(p));
    assertEquals("EXPIRED", ClubPolicy.effective(p, LocalDate.parse("2026-10-07")));
    assertEquals("UPCOMING", ClubPolicy.effective(p, LocalDate.parse("2026-10-04")));
    p.state = "CLOSED";
    assertEquals("CLOSED", ClubPolicy.effective(p, LocalDate.parse("2026-10-06")));
  }

  @Test
  void cancellationBoundaryAndNonOverlappingTouchingIntervals() {
    var s = new ClubSession();
    s.startsAt = Instant.parse("2026-10-06T04:00:00Z");
    s.cancelHours = 1;
    assertTrue(ClubPolicy.canCancel(s, s.startsAt.minusSeconds(3601)));
    assertFalse(ClubPolicy.canCancel(s, s.startsAt.minusSeconds(3600)));
    assertFalse(
        ClubPolicy.overlap(
            s.startsAt,
            s.startsAt.plusSeconds(60),
            s.startsAt.plusSeconds(60),
            s.startsAt.plusSeconds(120)));
    assertTrue(
        ClubPolicy.overlap(
            s.startsAt,
            s.startsAt.plusSeconds(60),
            s.startsAt.plusSeconds(59),
            s.startsAt.plusSeconds(120)));
  }

  private ClubPass pass() {
    var p = new ClubPass();
    p.state = "ACTIVE";
    p.mode = "CREDITS";
    p.startsOn = LocalDate.parse("2026-10-05");
    p.endsOn = LocalDate.parse("2026-10-06");
    return p;
  }
}
