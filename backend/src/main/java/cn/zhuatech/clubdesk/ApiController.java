// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 真实会员课程与后台接口，所有业务权限定于服务端重新核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final MasterService master;
  final ClubService club;
  final ReadService read;
  final AdminService admin;

  public ApiController(
      MasterService master, ClubService club, ReadService read, AdminService admin) {
    this.master = master;
    this.club = club;
    this.read = read;
    this.admin = admin;
  }

  /** 员工当前门店业务数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workspace")
  public Object workspace() {
    return read.workspace();
  }

  /** 首页数字和近期课程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return read.dashboard();
  }

  /** 会员本人卡、课程和预约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/portal")
  public Object portal() {
    return read.portal();
  }

  /** 已指派教练课程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/coaching")
  public Object coaching() {
    return read.coaching();
  }

  /** 门店基础资料写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody MasterService.Input v) {
    return master.save(type, null, v);
  }

  /** 版本保护的资料编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object editMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody MasterService.Input v) {
    return master.save(type, id, v);
  }

  /** 无引用资料删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(
      @PathVariable String type, @PathVariable Long id, @RequestParam Long revision) {
    master.delete(type, id, revision);
    return Map.of("ok", true);
  }

  /** 会员批量新增整批事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/members/import")
  public Object importMembers(@RequestBody List<MasterService.Input> v) {
    return master.importMembers(v);
  }

  /** 建立待付款会员卡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/passes")
  public Object issue(@RequestBody ClubService.PassInput v) {
    return club.issue(v);
  }

  /** 本人或员工卡详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/passes/{id}")
  public Object pass(@PathVariable Long id) {
    return read.passDetail(id);
  }

  /** 冻结解冻及关卡。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/passes/{id}/{action:freeze|unfreeze|close}")
  public Object passAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody ClubService.Action v) {
    return club.passAction(id, action, v);
  }

  /** 收退款或冲正记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/passes/{id}/cash")
  public Object cash(@PathVariable Long id, @RequestBody ClubService.CashInput v) {
    return club.cash(id, v);
  }

  /** 建立课程草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/sessions")
  public Object createSession(@RequestBody ClubService.SessionInput v) {
    return club.saveSession(null, v);
  }

  /** 修改没有有效预约的课程草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/sessions/{id}")
  public Object editSession(@PathVariable Long id, @RequestBody ClubService.SessionInput v) {
    return club.saveSession(id, v);
  }

  /** 指派或员工课程名单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/sessions/{id}")
  public Object session(@PathVariable Long id) {
    return read.sessionDetail(id);
  }

  /** 课程发布撤回取消结束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/sessions/{id}/{action:publish|withdraw|cancel|finish}")
  public Object sessionAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody ClubService.Action v) {
    return club.sessionAction(id, action, v);
  }

  /** 员工代会员预约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/reservations")
  public Object book(@RequestBody ClubService.BookingInput v) {
    return club.book(v, false);
  }

  /** 会员本人预约。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/portal/reservations")
  public Object bookOwn(@RequestBody ClubService.BookingInput v) {
    return club.book(v, true);
  }

  /** 当前账号授权范围的预约历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reservations/{id}")
  public Object booking(@PathVariable Long id) {
    return read.bookingDetail(id);
  }

  /** 员工有原因的取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/reservations/{id}/cancel")
  public Object cancel(@PathVariable Long id, @RequestBody ClubService.Action v) {
    return club.cancelBooking(id, v, false);
  }

  /** 会员本人按截止时间取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/portal/reservations/{id}/cancel")
  public Object cancelOwn(@PathVariable Long id, @RequestBody ClubService.Action v) {
    return club.cancelBooking(id, v, true);
  }

  /** 现场核验或结束后的未到核销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/reservations/{id}/{action:attend|no-show}")
  public Object attend(
      @PathVariable Long id, @PathVariable String action, @RequestBody ClubService.Action v) {
    return club.attendance(id, action, v);
  }

  /** 可审计签到纠错。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/reservations/{id}/correct")
  public Object correct(@PathVariable Long id, @RequestBody ClubService.Action v) {
    return club.correctAttendance(id, v);
  }

  /** 本人申请短期签到码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/portal/check-token")
  public Object token() {
    return club.mintToken();
  }

  /** 前台或指派教练扫码核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/checkin")
  public Object scan(@RequestBody ClubService.ScanInput v) {
    return club.scan(v);
  }

  /** 期限卡人工到店登记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/visits")
  public Object visit(@RequestBody ClubService.VisitInput v) {
    return club.visit(v);
  }

  /** 门店本地日期收退款与出勤汇总。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return read.reports(from, to);
  }

  /** 按范围导出标准CSV，错误仍返回JSON。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/exports/{type}.csv")
  public ResponseEntity<String> export(@PathVariable String type) {
    var csv = read.csv(type);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=clubdesk-" + type + ".csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv);
  }

  /** 脱敏操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return read.audit();
  }

  /** 后台管理列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 后台管理新建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 后台管理编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 仅无引用且非内建资源可删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
