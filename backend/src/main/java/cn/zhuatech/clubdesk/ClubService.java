// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 课程、会员卡、预约、课时和收退款的完整事务流程；单实例串行化写入保障名额和余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ClubService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public ClubService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 开卡快照；续开使用新卡，不覆盖已有卡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PassInput(Long memberId, Long planId, LocalDate startsOn, String note) {}

  /** 明确门店本地时间的课次输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record SessionInput(
      Long departmentId,
      Long courseId,
      Long coachId,
      Long roomId,
      String localStart,
      Integer capacity,
      Integer cancelHours,
      String note,
      Long revision) {}

  /** 员工代约与会员本人预约使用相同余额、名额与版本规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record BookingInput(
      Long memberId, Long sessionId, Long passId, Long sessionRevision, Long passRevision) {}

  /** 人工收退款凭据、原因与原单引用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record CashInput(
      String kind,
      String reference,
      BigDecimal amount,
      Long originalId,
      String note,
      Long revision) {}

  /** 状态操作必须带版本；冻结天数只用于冻结操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Action(Long revision, String note, Integer days) {}

  /** 前台或教练扫码；没有课次则为期限卡场馆到店登记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ScanInput(String token, Long sessionId, Long passId) {}

  /** 直接到店登记仍需要员工权限和本人有效期限卡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record VisitInput(Long memberId, Long passId) {}

  /** 串行化实例写入，防止跨名额和余额检查穿插。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void mutex() {
    db.lock(Department.class, 1L);
  }

  /** 核对账号门店范围与经营启停状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private Department branch(Long id) {
    access.department(id);
    var d = db.get(Department.class, id);
    if (!d.enabled) throw new Problem(409, "BRANCH_DISABLED");
    return d;
  }

  private LocalDate today(Long branch) {
    return clock.instant().atZone(ZoneId.of(db.get(Department.class, branch).zone)).toLocalDate();
  }

  private Member enabledMember(Long id) {
    var m = db.get(Member.class, id);
    if (!m.enabled) throw new Problem(409, "MEMBER_DISABLED");
    return m;
  }

  private void link(Member m, Long department) {
    if (!Objects.equals(m.departmentId, department)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 会员写入必须绑定本人档案，不能伪造会员主键。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void owner(Member m) {
    access.require("portal");
    if (!Objects.equals(access.current().memberId, m.id) || !access.role().scope.equals("MEMBER"))
      throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 业务历史与审计随同事务提交，不保存动态签到码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void event(String type, Long id, Long dep, String action, String note) {
    var e = new ClubEvent();
    e.objectType = type;
    e.objectId = id;
    e.departmentId = dep;
    e.action = action;
    e.actor = access.current().username;
    e.note = MasterService.optional(note, 1000);
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, id, dep);
  }

  /** 追加课时流水并核对预留与核销不越界。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void credit(
      ClubPass p, Reservation r, String kind, int total, int held, int used, String note) {
    var e = new CreditEntry();
    e.departmentId = p.departmentId;
    e.passId = p.id;
    e.reservationId = r == null ? null : r.id;
    e.kind = kind;
    e.totalDelta = total;
    e.heldDelta = held;
    e.usedDelta = used;
    e.actor = access.current().username;
    e.note = MasterService.optional(note, 1000);
    e.createdAt = clock.instant();
    db.save(e);
    p.held += held;
    p.used += used;
    p.revision++;
    if (p.held < 0 || p.used < 0 || p.mode.equals("CREDITS") && p.held + p.used > p.credits)
      throw new Problem(409, "CREDIT_INVARIANT");
  }

  /** 建立待收款卡，冻结套餐价格、次数、门店币种和有效期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ClubPass issue(PassInput v) {
    mutex();
    access.require("membership");
    var m = enabledMember(v.memberId);
    var d = branch(m.departmentId);
    var plan = db.get(Plan.class, v.planId);
    if (!plan.enabled || !Objects.equals(plan.departmentId, d.id))
      throw new Problem(409, "PLAN_NOT_AVAILABLE");
    var date = today(d.id);
    if (v.startsOn == null || v.startsOn.isBefore(date) || v.startsOn.isAfter(date.plusDays(730)))
      throw new Problem(400, "INVALID_START_DATE");
    var p = new ClubPass();
    p.reference = "CARD-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
    p.departmentId = d.id;
    p.memberId = m.id;
    p.planId = plan.id;
    p.planName = plan.name;
    p.planNameEn = plan.nameEn;
    p.mode = plan.mode;
    p.credits = plan.credits;
    p.price = plan.price;
    p.currency = setting("currency");
    p.startsOn = v.startsOn;
    p.endsOn = v.startsOn.plusDays(plan.validDays - 1);
    p.state = "DRAFT";
    p.note = MasterService.optional(v.note, 1000);
    p.createdAt = clock.instant();
    p.revision = 1;
    db.save(p);
    event("PASS", p.id, d.id, "PASS_ISSUED", p.note);
    return p;
  }

  /** 保存或调整尚无有效预约的课次，教室和教练按时间排他。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ClubSession saveSession(Long id, SessionInput v) {
    mutex();
    access.require("schedule");
    var d = branch(v.departmentId);
    var s = id == null ? new ClubSession() : db.get(ClubSession.class, id);
    if (id != null) {
      access.department(s.departmentId);
      if (!Objects.equals(s.departmentId, d.id)) throw new Problem(409, "BRANCH_IMMUTABLE");
      MasterService.version(s.revision, v.revision);
      if (!s.state.equals("DRAFT")) throw new Problem(409, "SESSION_NOT_DRAFT");
      if (activeReservations(s.id).size() > 0) throw new Problem(409, "ACTIVE_BOOKINGS");
    }
    var c = db.get(Course.class, v.courseId);
    var coach = db.get(Coach.class, v.coachId);
    var room = db.get(Room.class, v.roomId);
    var category = db.get(DictionaryEntry.class, c.categoryId);
    if (!c.enabled
        || !coach.enabled
        || !room.enabled
        || !category.enabled
        || !Objects.equals(c.departmentId, d.id)
        || !Objects.equals(coach.departmentId, d.id)
        || !Objects.equals(room.departmentId, d.id))
      throw new Problem(409, "RESOURCE_NOT_AVAILABLE");
    var start = ClubPolicy.localTime(v.localStart, d.zone);
    if (!start.isAfter(clock.instant()) || start.isAfter(clock.instant().plusSeconds(730L * 86400)))
      throw new Problem(400, "INVALID_SESSION_TIME");
    var end = start.plusSeconds(c.durationMinutes * 60L);
    var capacity = ClubPolicy.number(v.capacity, 1, 100);
    if (capacity > room.capacity) throw new Problem(409, "ROOM_CAPACITY");
    for (var other :
        db.query(
            ClubSession.class,
            "from ClubSession where departmentId=?1 and state in ('DRAFT','PUBLISHED')",
            d.id))
      if (!Objects.equals(other.id, id)
          && ClubPolicy.overlap(start, end, other.startsAt, other.endsAt)
          && (Objects.equals(other.coachId, coach.id) || Objects.equals(other.roomId, room.id)))
        throw new Problem(409, "SCHEDULE_CONFLICT");
    s.departmentId = d.id;
    s.courseId = c.id;
    s.coachId = coach.id;
    s.roomId = room.id;
    s.name = c.name;
    s.nameEn = c.nameEn;
    s.startsAt = start;
    s.endsAt = end;
    s.capacity = capacity;
    s.cancelHours = ClubPolicy.number(v.cancelHours, 0, 168);
    s.state = "DRAFT";
    s.note = MasterService.optional(v.note, 1000);
    s.revision++;
    if (id == null) db.save(s);
    event("SESSION", s.id, d.id, "SESSION_SAVED", s.note);
    return s;
  }

  /** 发布、撤回、取消或结束课次；取消释放预留，结束将未到预约按核对结果核销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ClubSession sessionAction(Long id, String action, Action v) {
    mutex();
    var s = db.get(ClubSession.class, id);
    if (action.equals("finish") && access.current().coachId != null) coaching(s);
    else access.require("schedule");
    branch(s.departmentId);
    MasterService.version(s.revision, v.revision);
    var note = AdminService.text(v.note, 1000);
    switch (action) {
      case "publish" -> {
        if (!s.state.equals("DRAFT") || !s.startsAt.isAfter(clock.instant()))
          throw new Problem(409, "INVALID_STATE");
        if (!db.get(Coach.class, s.coachId).enabled
            || !db.get(Room.class, s.roomId).enabled
            || !db.get(Course.class, s.courseId).enabled)
          throw new Problem(409, "RESOURCE_NOT_AVAILABLE");
        s.state = "PUBLISHED";
      }
      case "withdraw" -> {
        if (!s.state.equals("PUBLISHED") || !s.startsAt.isAfter(clock.instant()))
          throw new Problem(409, "INVALID_STATE");
        if (!activeReservations(s.id).isEmpty()) throw new Problem(409, "ACTIVE_BOOKINGS");
        s.state = "DRAFT";
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "PUBLISHED").contains(s.state))
          throw new Problem(409, "INVALID_STATE");
        for (var r : activeReservations(s.id)) {
          if (!r.state.equals("BOOKED")) throw new Problem(409, "ATTENDANCE_EXISTS");
          cancel(r, note);
        }
        s.state = "CANCELLED";
      }
      case "finish" -> {
        if (!s.state.equals("PUBLISHED") || clock.instant().isBefore(s.endsAt))
          throw new Problem(409, "SESSION_NOT_FINISHED");
        for (var r : activeReservations(s.id))
          if (r.state.equals("BOOKED")) consume(r, "NO_SHOW", note);
        s.state = "COMPLETED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    s.revision++;
    event("SESSION", s.id, s.departmentId, "SESSION_" + action.toUpperCase(Locale.ROOT), note);
    return s;
  }

  /** 预约原子预留一个名额和一次课时；同会员不能重叠、重复或透支。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Reservation book(BookingInput v, boolean portal) {
    mutex();
    if (portal) {
      access.require("portal");
      if (access.current().memberId == null) throw new Problem(403, "OUT_OF_SCOPE");
    }
    var m = enabledMember(portal ? access.current().memberId : v.memberId);
    if (portal) {
      owner(m);
      if (v.memberId != null && !Objects.equals(v.memberId, m.id))
        throw new Problem(403, "OUT_OF_SCOPE");
    } else access.require("membership");
    var s = db.get(ClubSession.class, v.sessionId);
    var d = branch(s.departmentId);
    link(m, d.id);
    MasterService.version(s.revision, v.sessionRevision);
    if (!s.state.equals("PUBLISHED") || !clock.instant().isBefore(s.startsAt))
      throw new Problem(409, "BOOKING_CLOSED");
    var p = db.get(ClubPass.class, v.passId);
    if (!Objects.equals(p.memberId, m.id) || !Objects.equals(p.departmentId, d.id))
      throw new Problem(403, "OUT_OF_SCOPE");
    MasterService.version(p.revision, v.passRevision);
    ClubPolicy.entitlement(p, s, d.zone);
    if (p.mode.equals("CREDITS") && ClubPolicy.available(p) <= 0)
      throw new Problem(409, "NO_CREDITS");
    var old =
        db.query(
            Reservation.class, "from Reservation where memberId=?1 and sessionId=?2", m.id, s.id);
    if (!old.isEmpty() && !old.getFirst().state.equals("CANCELLED"))
      throw new Problem(409, "ALREADY_BOOKED");
    if (activeReservations(s.id).size() >= s.capacity) throw new Problem(409, "CLASS_FULL");
    for (var r :
        db.query(
            Reservation.class,
            "from Reservation where memberId=?1 and state in ('BOOKED','ATTENDED','NO_SHOW')",
            m.id)) {
      var other = db.get(ClubSession.class, r.sessionId);
      if (ClubPolicy.overlap(s.startsAt, s.endsAt, other.startsAt, other.endsAt))
        throw new Problem(409, "MEMBER_TIME_CONFLICT");
    }
    var r = old.isEmpty() ? new Reservation() : old.getFirst();
    r.departmentId = d.id;
    r.memberId = m.id;
    r.sessionId = s.id;
    r.passId = p.id;
    r.state = "BOOKED";
    r.note = "";
    if (r.id == null) {
      r.createdAt = clock.instant();
      r.updatedAt = clock.instant();
      r.revision = 0;
      db.save(r);
    }
    r.updatedAt = clock.instant();
    r.revision++;
    credit(p, r, "RESERVE", 0, 1, 0, "预约 / Booking");
    event("BOOKING", r.id, d.id, "BOOKED", p.reference);
    return r;
  }

  /** 会员按截止时间取消；员工因实际情况取消必须记录原因，释放预留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Reservation cancelBooking(Long id, Action v, boolean portal) {
    mutex();
    var r = db.get(Reservation.class, id);
    if (portal) owner(db.get(Member.class, r.memberId));
    else access.require("membership");
    branch(r.departmentId);
    MasterService.version(r.revision, v.revision);
    if (!r.state.equals("BOOKED")) throw new Problem(409, "INVALID_STATE");
    var s = db.get(ClubSession.class, r.sessionId);
    if (portal && !ClubPolicy.canCancel(s, clock.instant()))
      throw new Problem(409, "CANCELLATION_DEADLINE");
    cancel(r, AdminService.text(v.note, 1000));
    return r;
  }

  /** 取消释放原预留并保留变更历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void cancel(Reservation r, String note) {
    var p = db.get(ClubPass.class, r.passId);
    credit(p, r, "RELEASE", 0, -1, 0, note);
    r.state = "CANCELLED";
    r.note = note;
    r.updatedAt = clock.instant();
    r.revision++;
    event("BOOKING", r.id, r.departmentId, "BOOKING_CANCELLED", note);
  }

  /** 仅取消的预约释放名额，已到和未到保留使用事实。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private List<Reservation> activeReservations(Long session) {
    return db.query(
        Reservation.class,
        "from Reservation where sessionId=?1 and state in ('BOOKED','ATTENDED','NO_SHOW')",
        session);
  }

  /** 核对启用教练与课次的实际指派关系。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void coaching(ClubSession s) {
    access.require("coach");
    if (access.current().coachId == null
        || !Objects.equals(access.current().coachId, s.coachId)
        || !db.get(Coach.class, s.coachId).enabled) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 前台或被指派教练才能登记实际出勤。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void attendanceAuthority(ClubSession s) {
    if (access.current().coachId != null) coaching(s);
    else access.require("checkin");
    branch(s.departmentId);
  }

  /** 实际到课或未到的核销，校验签到窗口和教练指派范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Reservation attendance(Long id, String action, Action v) {
    mutex();
    var r = db.get(Reservation.class, id);
    var s = db.get(ClubSession.class, r.sessionId);
    attendanceAuthority(s);
    MasterService.version(r.revision, v.revision);
    var note = AdminService.text(v.note, 1000);
    if (!r.state.equals("BOOKED")) throw new Problem(409, "ATTENDANCE_ALREADY_RECORDED");
    if (!s.state.equals("PUBLISHED")) throw new Problem(409, "INVALID_STATE");
    if (action.equals("attend")) {
      if (clock.instant().isBefore(s.startsAt.minusSeconds(1800))
          || clock.instant().isAfter(s.endsAt.plusSeconds(1800)))
        throw new Problem(409, "CHECKIN_WINDOW");
      enabledMember(r.memberId);
      ClubPolicy.entitlement(
          db.get(ClubPass.class, r.passId), s, db.get(Department.class, s.departmentId).zone);
      consume(r, "ATTENDED", note);
    } else if (action.equals("no-show")) {
      if (clock.instant().isBefore(s.endsAt)) throw new Problem(409, "SESSION_NOT_FINISHED");
      consume(r, "NO_SHOW", note);
    } else throw new Problem(404, "NOT_FOUND");
    return r;
  }

  /** 一次核销将预留转换为已使用，不再扣第二次余额。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void consume(Reservation r, String state, String note) {
    var p = db.get(ClubPass.class, r.passId);
    credit(p, r, state, 0, -1, 1, note);
    r.state = state;
    r.note = note;
    r.updatedAt = clock.instant();
    r.revision++;
    event("BOOKING", r.id, r.departmentId, state, note);
  }

  /** 独立纠错保留原签到历史，返还已用课时并取消预约；不能抹除记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Reservation correctAttendance(Long id, Action v) {
    mutex();
    access.require("membership");
    var r = db.get(Reservation.class, id);
    branch(r.departmentId);
    MasterService.version(r.revision, v.revision);
    if (!Set.of("ATTENDED", "NO_SHOW").contains(r.state)) throw new Problem(409, "INVALID_STATE");
    var note = AdminService.text(v.note, 1000);
    credit(db.get(ClubPass.class, r.passId), r, "CORRECTION", 0, 0, -1, note);
    r.state = "CANCELLED";
    r.note = note;
    r.revision++;
    r.updatedAt = clock.instant();
    event("BOOKING", r.id, r.departmentId, "ATTENDANCE_CORRECTED", note);
    return r;
  }

  /** 冻结延期或提前解冻；已有预约不能被静默改为失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ClubPass passAction(Long id, String action, Action v) {
    mutex();
    access.require("membership");
    var p = db.get(ClubPass.class, id);
    branch(p.departmentId);
    MasterService.version(p.revision, v.revision);
    var note = AdminService.text(v.note, 1000);
    var date = today(p.departmentId);
    var booked =
        db.query(Reservation.class, "from Reservation where passId=?1 and state='BOOKED'", id);
    switch (action) {
      case "freeze" -> {
        if (!ClubPolicy.effective(p, date).equals("ACTIVE"))
          throw new Problem(409, "PASS_NOT_VALID");
        int days = ClubPolicy.number(v.days, 1, 90);
        if (p.freezeDays + days > 365) throw new Problem(409, "FREEZE_LIMIT");
        var until = date.plusDays(days);
        for (var r : booked) {
          var s = db.get(ClubSession.class, r.sessionId);
          var sd =
              s.startsAt
                  .atZone(ZoneId.of(db.get(Department.class, p.departmentId).zone))
                  .toLocalDate();
          if (!sd.isBefore(date) && sd.isBefore(until))
            throw new Problem(409, "FREEZE_BOOKING_CONFLICT");
        }
        p.freezeStarted = date;
        p.freezeUntil = until;
        p.endsOn = p.endsOn.plusDays(days);
        p.freezeDays += days;
      }
      case "unfreeze" -> {
        if (!ClubPolicy.effective(p, date).equals("FROZEN"))
          throw new Problem(409, "PASS_NOT_FROZEN");
        int unused = (int) java.time.temporal.ChronoUnit.DAYS.between(date, p.freezeUntil);
        var end = p.endsOn.minusDays(unused);
        var z = ZoneId.of(db.get(Department.class, p.departmentId).zone);
        for (var r : booked)
          if (db.get(ClubSession.class, r.sessionId)
              .endsAt
              .isAfter(end.plusDays(1).atStartOfDay(z).toInstant()))
            throw new Problem(409, "FREEZE_BOOKING_CONFLICT");
        p.endsOn = end;
        p.freezeUntil = date;
        p.freezeDays -= unused;
      }
      case "close" -> {
        if (p.state.equals("CLOSED")) throw new Problem(409, "INVALID_STATE");
        for (var r : booked) cancel(r, note);
        p.state = "CLOSED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    p.revision++;
    event("PASS", p.id, p.departmentId, "PASS_" + action.toUpperCase(Locale.ROOT), note);
    return p;
  }

  /** 记录已经核实的实际外部收款、原收款退款或单次冲正，不发起资金交易。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CashEntry cash(Long id, CashInput v) {
    mutex();
    access.require("finance");
    var p = db.get(ClubPass.class, id);
    branch(p.departmentId);
    MasterService.version(p.revision, v.revision);
    var amount = ClubPolicy.money(v.amount);
    var ref = AdminService.text(v.reference, 160).toUpperCase(Locale.ROOT);
    if (!db.query(CashEntry.class, "from CashEntry where reference=?1", ref).isEmpty())
      throw new Problem(409, "DUPLICATE_REFERENCE");
    var note = AdminService.text(v.note, 1000);
    Long original = null;
    switch (v.kind == null ? "" : v.kind) {
      case "RECEIPT" -> {
        if (!p.state.equals("DRAFT") || v.originalId != null)
          throw new Problem(409, "INVALID_STATE");
        enabledMember(p.memberId);
        if (netCash(p.id).add(amount).compareTo(p.price) > 0)
          throw new Problem(409, "PAYMENT_EXCEEDS_DUE");
      }
      case "REFUND" -> {
        if (!p.state.equals("CLOSED")) throw new Problem(409, "CLOSE_BEFORE_REFUND");
        var origin = db.get(CashEntry.class, v.originalId);
        if (!Objects.equals(origin.passId, p.id) || !origin.kind.equals("RECEIPT"))
          throw new Problem(409, "INVALID_ORIGINAL");
        if (reversed(origin.id) || amount.compareTo(refundable(origin)) > 0)
          throw new Problem(409, "REFUND_EXCEEDS_RECEIPT");
        original = origin.id;
      }
      case "REVERSAL" -> {
        var origin = db.get(CashEntry.class, v.originalId);
        if (!Objects.equals(origin.passId, p.id)
            || origin.kind.equals("REVERSAL")
            || reversed(origin.id)
            || amount.compareTo(origin.amount) != 0) throw new Problem(409, "INVALID_ORIGINAL");
        if (p.state.equals("ACTIVE")) throw new Problem(409, "CLOSE_BEFORE_REVERSAL");
        if (origin.kind.equals("RECEIPT") && refundable(origin).compareTo(origin.amount) != 0)
          throw new Problem(409, "REFUND_DEPENDENCY");
        original = origin.id;
      }
      default -> throw new Problem(400, "INVALID_CASH_KIND");
    }
    var e = new CashEntry();
    e.departmentId = p.departmentId;
    e.passId = p.id;
    e.originalId = original;
    e.kind = v.kind;
    e.reference = ref;
    e.amount = amount;
    e.note = note;
    e.actor = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    db.flush();
    p.revision++;
    if (p.state.equals("DRAFT") && netCash(p.id).compareTo(p.price) == 0) {
      p.state = "ACTIVE";
      credit(p, null, "ACTIVATE", p.credits, 0, 0, "足额收款生效 / Paid in full");
      event("PASS", p.id, p.departmentId, "PASS_ACTIVATED", p.reference);
    }
    event("PASS", p.id, p.departmentId, "CASH_" + e.kind, e.reference + " · " + note);
    return e;
  }

  /** 同一原资金记录最多出现一次冲正。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private boolean reversed(Long id) {
    return !db.query(CashEntry.class, "from CashEntry where originalId=?1 and kind='REVERSAL'", id)
        .isEmpty();
  }

  /** 可退金额按原收款及其未冲正退款计算，不能退超过实收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal refundable(CashEntry origin) {
    if (reversed(origin.id)) return BigDecimal.ZERO;
    var refunds =
        db.query(
            CashEntry.class, "from CashEntry where originalId=?1 and kind='REFUND'", origin.id);
    return origin.amount.subtract(
        refunds.stream()
            .filter(x -> !reversed(x.id))
            .map(x -> x.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  /** 净实收包含收款、退款和其一次反向冲正，来源不可变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal netCash(Long id) {
    var total = BigDecimal.ZERO;
    for (var e : db.query(CashEntry.class, "from CashEntry where passId=?1", id)) {
      var sign = e.kind.equals("RECEIPT") ? 1 : -1;
      if (e.kind.equals("REVERSAL"))
        sign = db.get(CashEntry.class, e.originalId).kind.equals("RECEIPT") ? -1 : 1;
      total = total.add(e.amount.multiply(BigDecimal.valueOf(sign)));
    }
    return total.setScale(2);
  }

  /** 会员申请两分钟单次动态码，散列保存，刷新使旧码失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> mintToken() {
    mutex();
    access.require("portal");
    if (access.current().memberId == null) throw new Problem(403, "OUT_OF_SCOPE");
    var m = enabledMember(access.current().memberId);
    owner(m);
    branch(m.departmentId);
    var now = clock.instant();
    var recent =
        db.query(
            CheckToken.class,
            "from CheckToken where memberId=?1 and createdAt>?2",
            m.id,
            now.minusSeconds(5));
    if (!recent.isEmpty()) throw new Problem(429, "QR_REFRESH_LIMIT");
    db.cleanExpiredTokens(now.minusSeconds(86400));
    for (var old :
        db.query(CheckToken.class, "from CheckToken where memberId=?1 and usedAt is null", m.id))
      old.usedAt = now;
    var bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    var value = "CLUB1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var token = new CheckToken();
    token.memberId = m.id;
    token.departmentId = m.departmentId;
    token.tokenHash = hash(value);
    token.createdAt = now;
    token.expiresAt = now.plusSeconds(120);
    db.save(token);
    return Map.of("token", value, "expiresAt", token.expiresAt, "memberName", m.name);
  }

  /** 用SHA-256保存短期码散列，不保存明文。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("HASH_UNAVAILABLE");
    }
  }

  /** 前台扫码后校验本人预约或期限卡，成功后才消费短期码，重复扫描不重复扣课时。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> scan(ScanInput v) {
    mutex();
    if (v.token == null || !v.token.matches("CLUB1:[A-Za-z0-9_-]{32}"))
      throw new Problem(400, "INVALID_CHECK_CODE");
    var tokens = db.query(CheckToken.class, "from CheckToken where tokenHash=?1", hash(v.token));
    if (tokens.isEmpty()) throw new Problem(409, "CHECK_CODE_EXPIRED");
    var t = tokens.getFirst();
    if (t.usedAt != null || !clock.instant().isBefore(t.expiresAt))
      throw new Problem(409, "CHECK_CODE_EXPIRED");
    branch(t.departmentId);
    var m = enabledMember(t.memberId);
    Object result;
    if (v.sessionId != null) {
      var s = db.get(ClubSession.class, v.sessionId);
      attendanceAuthority(s);
      link(m, s.departmentId);
      var rows =
          db.query(
              Reservation.class, "from Reservation where memberId=?1 and sessionId=?2", m.id, s.id);
      if (rows.isEmpty()) throw new Problem(409, "NO_BOOKING");
      var r = rows.getFirst();
      result =
          attendance(
              r.id, "attend", new Action(r.revision, "现场扫码核验 / Reception QR verification", null));
    } else {
      Long passId = v.passId;
      if (passId == null)
        passId =
            db
                .query(
                    ClubPass.class,
                    "from ClubPass where memberId=?1 and mode='PERIOD' order by endsOn,id",
                    m.id)
                .stream()
                .filter(p -> ClubPolicy.effective(p, today(p.departmentId)).equals("ACTIVE"))
                .map(p -> p.id)
                .findFirst()
                .orElseThrow(() -> new Problem(409, "PERIOD_PASS_REQUIRED"));
      result = visit(new VisitInput(m.id, passId));
    }
    t.usedAt = clock.instant();
    return Map.of("memberName", m.name, "record", result);
  }

  /** 期限卡入馆核对，不涉及门禁硬件；重复到店不增加每日次数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public EntryVisit visit(VisitInput v) {
    mutex();
    access.require("checkin");
    var m = enabledMember(v.memberId);
    var d = branch(m.departmentId);
    var p = db.get(ClubPass.class, v.passId);
    if (!Objects.equals(p.memberId, m.id) || !Objects.equals(p.departmentId, d.id))
      throw new Problem(403, "OUT_OF_SCOPE");
    if (!p.mode.equals("PERIOD") || !ClubPolicy.effective(p, today(d.id)).equals("ACTIVE"))
      throw new Problem(409, "PERIOD_PASS_REQUIRED");
    var date = today(d.id);
    if (!db.query(
            EntryVisit.class, "from EntryVisit where memberId=?1 and visitDate=?2", m.id, date)
        .isEmpty()) throw new Problem(409, "ALREADY_VISITED");
    var e = new EntryVisit();
    e.departmentId = d.id;
    e.memberId = m.id;
    e.passId = p.id;
    e.visitDate = date;
    e.createdAt = clock.instant();
    e.actor = access.current().username;
    db.save(e);
    event("PASS", p.id, d.id, "ENTRY_VISIT", "到店核验 / Reception entry verification");
    return e;
  }

  /** 当前实例参数只读查询；未知参数不传入动态查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }
}
