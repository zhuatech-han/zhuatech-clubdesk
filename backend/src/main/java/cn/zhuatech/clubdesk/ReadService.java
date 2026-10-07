// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按员工、教练与会员范围组织只读页面及可对账报表，不返回签到散列或其他会员资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(readOnly = true)
public class ReadService {
  final Store db;
  final AccessService access;
  final ClubService club;
  final Clock clock;

  public ReadService(Store db, AccessService access, ClubService club, Clock clock) {
    this.db = db;
    this.access = access;
    this.club = club;
    this.clock = clock;
  }

  private LocalDate today(Long id) {
    return clock.instant().atZone(ZoneId.of(db.get(Department.class, id).zone)).toLocalDate();
  }

  private boolean visible(Long id) {
    return access.visible(id);
  }

  private List<Department> branches() {
    return db.all(Department.class).stream().filter(d -> visible(d.id)).toList();
  }

  private List<ClubPass> passes() {
    return db.all(ClubPass.class).stream().filter(p -> visible(p.departmentId)).toList();
  }

  private List<ClubSession> sessions() {
    return db.all(ClubSession.class).stream().filter(s -> visible(s.departmentId)).toList();
  }

  /** 课次人数包含预约、已到和未到；取消释放名额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> sessionView(ClubSession s) {
    var count =
        db.query(
                Reservation.class,
                "from Reservation where sessionId=?1 and state<>'CANCELLED'",
                s.id)
            .size();
    return Map.of(
        "session",
        s,
        "booked",
        count,
        "openSeats",
        Math.max(0, s.capacity - count),
        "coachName",
        db.get(Coach.class, s.coachId).name,
        "roomName",
        db.get(Room.class, s.roomId).name,
        "zone",
        db.get(Department.class, s.departmentId).zone,
        "cancelDeadline",
        s.startsAt.minusSeconds(s.cancelHours * 3600L));
  }

  /** 卡余额派生自不可变资金与课时流水，期限卡次数显示为空值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> passView(ClubPass p) {
    var r = new LinkedHashMap<String, Object>();
    r.put("pass", p);
    r.put("effective", ClubPolicy.effective(p, today(p.departmentId)));
    r.put("available", ClubPolicy.available(p));
    r.put("netPaid", club.netCash(p.id));
    r.put("due", p.state.equals("DRAFT") ? p.price.subtract(club.netCash(p.id)) : BigDecimal.ZERO);
    r.put("memberName", db.get(Member.class, p.memberId).name);
    return r;
  }

  /** 员工有明确业务读取权后取得所属门店资料，后台账号不从该接口暴露。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> workspace() {
    access.require("read");
    var r = new LinkedHashMap<String, Object>();
    r.put("branches", branches());
    r.put("members", db.all(Member.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put("coaches", db.all(Coach.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put("rooms", db.all(Room.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put("courses", db.all(Course.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put("plans", db.all(Plan.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put("passes", passes().stream().map(this::passView).toList());
    r.put("sessions", sessions().stream().map(this::sessionView).toList());
    r.put(
        "reservations",
        db.all(Reservation.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put(
        "visits", db.all(EntryVisit.class).stream().filter(x -> visible(x.departmentId)).toList());
    r.put(
        "categories",
        db.all(DictionaryEntry.class).stream()
            .filter(x -> x.type.equals("courseCategory"))
            .toList());
    r.put("currency", club.setting("currency"));
    r.put("companyName", club.setting("companyName"));
    return r;
  }

  private Member ownMember() {
    access.require("portal");
    if (access.current().memberId == null || !access.role().scope.equals("MEMBER"))
      throw new Problem(403, "OUT_OF_SCOPE");
    return db.get(Member.class, access.current().memberId);
  }

  /** 会员看本人卡、预约、到店和所属门店可约课，不取得同门店其他会员名单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> portal() {
    var m = ownMember();
    var d = db.get(Department.class, m.departmentId);
    var bookings = db.query(Reservation.class, "from Reservation where memberId=?1", m.id);
    var ownSessions = new HashSet<>(bookings.stream().map(x -> x.sessionId).toList());
    var r = new LinkedHashMap<String, Object>();
    r.put("member", m);
    r.put("branches", List.of(d));
    r.put(
        "passes",
        db.query(ClubPass.class, "from ClubPass where memberId=?1", m.id).stream()
            .map(this::passView)
            .toList());
    r.put("reservations", bookings);
    r.put(
        "sessions",
        db.query(ClubSession.class, "from ClubSession where departmentId=?1", d.id).stream()
            .filter(
                s ->
                    ownSessions.contains(s.id)
                        || s.state.equals("PUBLISHED") && s.startsAt.isAfter(clock.instant()))
            .map(this::sessionView)
            .toList());
    r.put("visits", db.query(EntryVisit.class, "from EntryVisit where memberId=?1", m.id));
    r.put("currency", club.setting("currency"));
    r.put("bookingNotice", club.setting("bookingNotice"));
    return r;
  }

  /** 教练只看已指派且发布过的课次与名单，不看会员联系方式、卡价格或其他教练课程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> coaching() {
    access.require("coach");
    if (access.current().coachId == null) throw new Problem(403, "OUT_OF_SCOPE");
    var coach = db.get(Coach.class, access.current().coachId);
    if (!coach.enabled) throw new Problem(409, "COACH_DISABLED");
    return Map.of(
        "coach",
        coach,
        "branches",
        List.of(db.get(Department.class, coach.departmentId)),
        "sessions",
        db
            .query(
                ClubSession.class, "from ClubSession where coachId=?1 and state<>'DRAFT'", coach.id)
            .stream()
            .map(this::sessionView)
            .toList());
  }

  /** 按指派教练或员工门店范围保护学员名单。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void permitSession(ClubSession s) {
    if (access.current().coachId != null) {
      access.require("coach");
      if (!Objects.equals(access.current().coachId, s.coachId) || s.state.equals("DRAFT"))
        throw new Problem(403, "OUT_OF_SCOPE");
    } else {
      access.require("read");
      access.department(s.departmentId);
    }
  }

  /** 课次详情含按授权缩减的名单；教练不能查未指派课程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> sessionDetail(Long id) {
    var s = db.get(ClubSession.class, id);
    permitSession(s);
    var roster = new ArrayList<Map<String, Object>>();
    for (var x : db.query(Reservation.class, "from Reservation where sessionId=?1", id)) {
      var m = db.get(Member.class, x.memberId);
      roster.add(
          Map.of(
              "booking",
              x,
              "memberName",
              m.name,
              "memberCode",
              m.code,
              "passReference",
              db.get(ClubPass.class, x.passId).reference));
    }
    return Map.of("summary", sessionView(s), "roster", roster, "events", events("SESSION", id));
  }

  /** 本人或门店员工读取卡、原收款可退余额与不可改写课时历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> passDetail(Long id) {
    var p = db.get(ClubPass.class, id);
    if (access.current().memberId != null) {
      if (!Objects.equals(ownMember().id, p.memberId)) throw new Problem(403, "OUT_OF_SCOPE");
    } else {
      access.require("read");
      access.department(p.departmentId);
    }
    var cash = db.query(CashEntry.class, "from CashEntry where passId=?1", id);
    return Map.of(
        "summary",
        passView(p),
        "cash",
        cash.stream()
            .map(
                e ->
                    Map.of(
                        "entry",
                        e,
                        "refundable",
                        e.kind.equals("RECEIPT") ? club.refundable(e) : BigDecimal.ZERO,
                        "reversed",
                        !db.query(
                                CashEntry.class,
                                "from CashEntry where originalId=?1 and kind='REVERSAL'",
                                e.id)
                            .isEmpty()))
            .toList(),
        "credits",
        db.query(CreditEntry.class, "from CreditEntry where passId=?1", id),
        "events",
        events("PASS", id));
  }

  /** 本人、指派教练或门店员工可查预约变更历史，不外泄其他会员资金数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> bookingDetail(Long id) {
    var r = db.get(Reservation.class, id);
    var s = db.get(ClubSession.class, r.sessionId);
    if (access.current().memberId != null) {
      if (!Objects.equals(ownMember().id, r.memberId)) throw new Problem(403, "OUT_OF_SCOPE");
    } else permitSession(s);
    return Map.of("booking", r, "session", sessionView(s), "events", events("BOOKING", id));
  }

  /** 按已授权业务对象读取不可改写历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private List<ClubEvent> events(String type, Long id) {
    return db.query(
        ClubEvent.class,
        "from ClubEvent where objectType=?1 and objectId=?2 order by id",
        type,
        id);
  }

  /** 单实例仪表盘按当前范围统计，不把待收款卡算作生效卡或实收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> dashboard() {
    access.require("dashboard");
    var all = passes();
    var now = clock.instant();
    var cls = sessions();
    return Map.of(
        "activePasses",
        all.stream()
            .filter(p -> ClubPolicy.effective(p, today(p.departmentId)).equals("ACTIVE"))
            .count(),
        "draftPasses",
        all.stream().filter(p -> p.state.equals("DRAFT")).count(),
        "members",
        db.all(Member.class).stream().filter(m -> visible(m.departmentId) && m.enabled).count(),
        "todaySessions",
        cls.stream()
            .filter(
                s ->
                    s.state.equals("PUBLISHED")
                        && s.startsAt
                            .atZone(ZoneId.of(db.get(Department.class, s.departmentId).zone))
                            .toLocalDate()
                            .equals(today(s.departmentId)))
            .map(this::sessionView)
            .toList(),
        "upcomingSessions",
        cls.stream()
            .filter(s -> s.state.equals("PUBLISHED") && s.startsAt.isAfter(now))
            .sorted(Comparator.comparing(s -> s.startsAt))
            .limit(8)
            .map(this::sessionView)
            .toList(),
        "currency",
        club.setting("currency"));
  }

  /** 门店本地日期报表，总计由同一组门店金额相加，退款与冲正单独保留口径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> reports(LocalDate from, LocalDate to) {
    access.require("report");
    if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(366)))
      throw new Problem(400, "INVALID_REPORT_DATES");
    var rows = new ArrayList<Map<String, Object>>();
    for (var d : branches()) {
      var receipt = BigDecimal.ZERO;
      var refund = BigDecimal.ZERO;
      for (var e : db.query(CashEntry.class, "from CashEntry where departmentId=?1", d.id)) {
        if (!inDate(e.createdAt, d.zone, from, to)) continue;
        if (e.kind.equals("RECEIPT")) receipt = receipt.add(e.amount);
        else if (e.kind.equals("REFUND")) refund = refund.add(e.amount);
        else if (db.get(CashEntry.class, e.originalId).kind.equals("RECEIPT"))
          receipt = receipt.subtract(e.amount);
        else refund = refund.subtract(e.amount);
      }
      long attended = 0, missed = 0;
      for (var r : db.query(Reservation.class, "from Reservation where departmentId=?1", d.id)) {
        var s = db.get(ClubSession.class, r.sessionId);
        if (inDate(s.startsAt, d.zone, from, to)) {
          if (r.state.equals("ATTENDED")) attended++;
          if (r.state.equals("NO_SHOW")) missed++;
        }
      }
      rows.add(
          Map.of(
              "id",
              d.id,
              "name",
              d.name,
              "receipts",
              receipt.setScale(2),
              "refunds",
              refund.setScale(2),
              "netReceived",
              receipt.subtract(refund).setScale(2),
              "cardsIssued",
              db.query(ClubPass.class, "from ClubPass where departmentId=?1", d.id).stream()
                  .filter(p -> inDate(p.createdAt, d.zone, from, to))
                  .count(),
              "attended",
              attended,
              "noShows",
              missed,
              "visits",
              db.query(EntryVisit.class, "from EntryVisit where departmentId=?1", d.id).stream()
                  .filter(v -> !v.visitDate.isBefore(from) && !v.visitDate.isAfter(to))
                  .count()));
    }
    var total = new LinkedHashMap<String, Object>();
    for (var key : List.of("receipts", "refunds", "netReceived"))
      total.put(
          key,
          rows.stream()
              .map(r -> (BigDecimal) r.get(key))
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .setScale(2));
    for (var key : List.of("cardsIssued", "attended", "noShows", "visits"))
      total.put(key, rows.stream().mapToLong(r -> (Long) r.get(key)).sum());
    return Map.of(
        "from",
        from,
        "to",
        to,
        "currency",
        club.setting("currency"),
        "branches",
        rows,
        "total",
        total);
  }

  /** 按各门店时区核对报表日期，避免浏览器时区漂移。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private boolean inDate(Instant value, String zone, LocalDate from, LocalDate to) {
    var date = value.atZone(ZoneId.of(zone)).toLocalDate();
    return !date.isBefore(from) && !date.isAfter(to);
  }

  /** 按范围读取不含敏感载荷的系统审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<AuditEvent> audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream().filter(e -> visible(e.departmentId)).toList();
  }

  /** 标准CSV导出，不插入宣传内容，并防止文本被表格软件当作公式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String csv(String type) {
    access.require("report");
    var rows = new ArrayList<List<Object>>();
    switch (type) {
      case "members" -> {
        rows.add(List.of("code", "name", "departmentId", "contactNote", "enabled"));
        for (var m : db.all(Member.class))
          if (visible(m.departmentId))
            rows.add(List.of(m.code, m.name, m.departmentId, m.contactNote, m.enabled));
      }
      case "passes" -> {
        rows.add(
            List.of(
                "reference",
                "memberCode",
                "planName",
                "mode",
                "startsOn",
                "endsOn",
                "status",
                "credits",
                "held",
                "used",
                "price",
                "currency",
                "netReceived"));
        for (var p : passes())
          rows.add(
              List.of(
                  p.reference,
                  db.get(Member.class, p.memberId).code,
                  p.planName,
                  p.mode,
                  p.startsOn,
                  p.endsOn,
                  ClubPolicy.effective(p, today(p.departmentId)),
                  p.credits,
                  p.held,
                  p.used,
                  p.price,
                  p.currency,
                  club.netCash(p.id)));
      }
      case "attendance" -> {
        rows.add(List.of("memberCode", "class", "startsAt", "state", "passReference", "updatedAt"));
        for (var r : db.all(Reservation.class))
          if (visible(r.departmentId)) {
            var s = db.get(ClubSession.class, r.sessionId);
            rows.add(
                List.of(
                    db.get(Member.class, r.memberId).code,
                    s.name,
                    s.startsAt,
                    r.state,
                    db.get(ClubPass.class, r.passId).reference,
                    r.updatedAt));
          }
      }
      case "cash" -> {
        rows.add(
            List.of(
                "reference",
                "cardReference",
                "kind",
                "amount",
                "currency",
                "originalId",
                "createdAt",
                "note"));
        for (var e : db.all(CashEntry.class))
          if (visible(e.departmentId))
            rows.add(
                Arrays.asList(
                    e.reference,
                    db.get(ClubPass.class, e.passId).reference,
                    e.kind,
                    e.amount,
                    db.get(ClubPass.class, e.passId).currency,
                    e.originalId,
                    e.createdAt,
                    e.note));
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return "\ufeff"
        + rows.stream()
            .map(row -> row.stream().map(this::cell).reduce((a, b) -> a + "," + b).orElse(""))
            .reduce((a, b) -> a + "\r\n" + b)
            .orElse("")
        + "\r\n";
  }

  /** CSV仅对文本公式前缀加转义，不改数据库原值。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String cell(Object value) {
    var text = value == null ? "" : value.toString();
    var leading = text.stripLeading();
    if (value instanceof String && !leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0)
      text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
