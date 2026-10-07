// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化门店、角色和管理账号，不生成会员、卡、预约或收款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;

  @Value("${clubdesk.admin-username}")
  String username;

  @Value("${clubdesk.admin-password}")
  String password;

  public Bootstrap(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  /** 初次启动创建独立强口令账号；重启不覆盖已有业务或账号。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("INVALID_ADMIN_USERNAME");
    var d = new Department();
    d.name = "主场馆 / Main studio";
    d.zone = "Asia/Shanghai";
    d.enabled = true;
    db.save(d);
    var all = new HashSet<String>();
    for (var v :
        new String[][] {
          {"dashboard", "工作台 / Workspace"},
          {"read", "门店业务查看 / Branch records"},
          {"master", "会员与基础资料 / Members & catalogue"},
          {"membership", "开卡冻结与预约 / Membership operations"},
          {"schedule", "排课与发布 / Scheduling"},
          {"checkin", "前台签到核销 / Reception attendance"},
          {"finance", "收退款及冲正登记 / Receipt & refund records"},
          {"report", "统计与业务导出 / Reports & export"},
          {"admin", "账号与配置 / Administration"},
          {"audit", "审计记录 / Audit"},
          {"portal", "会员本人入口 / Member portal"},
          {"coach", "教练已指派课程 / Assigned coaching"}
        }) {
      var p = new Permission();
      p.code = v[0];
      p.name = v[1];
      db.save(p);
      if (!Set.of("portal", "coach").contains(v[0])) all.add(v[0]);
    }
    var r = role("系统管理员 / Administrator", "ALL", all);
    role(
        "门店运营 / Branch operations",
        "DEPARTMENT",
        Set.of("dashboard", "read", "master", "membership", "schedule", "checkin", "report"));
    role(
        "前台收银 / Reception",
        "DEPARTMENT",
        Set.of("dashboard", "read", "membership", "checkin", "finance"));
    role("财务 / Finance", "DEPARTMENT", Set.of("dashboard", "read", "finance", "report"));
    role("教练 / Coach", "TRAINER", Set.of("coach"));
    role("会员 / Member", "MEMBER", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = r.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    int pos = 0;
    for (var v :
        new String[][] {
          {"dashboard", "工作台", "Workspace", "dashboard"},
          {"sessions", "课程日程", "Schedule", "read"},
          {"members", "会员", "Members", "read"},
          {"passes", "会员卡", "Memberships", "read"},
          {"checkin", "到店签到", "Check-in", "checkin"},
          {"catalog", "课程与套餐", "Catalogue", "read"},
          {"portal", "我的课程", "My classes", "portal"},
          {"coach", "我的教学", "My coaching", "coach"},
          {"reports", "经营对账", "Reports", "report"},
          {"admin", "账号与设置", "Administration", "admin"},
          {"audit", "操作记录", "Audit", "audit"}
        }) {
      var m = new NavMenu();
      m.code = v[0];
      m.name = v[1];
      m.nameEn = v[2];
      m.permissionCode = v[3];
      m.position = pos++;
      m.enabled = true;
      db.save(m);
    }
    for (var v :
        new String[][] {
          {"companyName", "运动场馆 / Fitness studio"},
          {"currency", "CNY"},
          {"bookingNotice", "取消期限以每节课程为准。 / Cancellation deadlines are shown on each class."}
        }) {
      var s = new SystemSetting();
      s.code = v[0];
      s.value = v[1];
      db.save(s);
    }
    for (var v :
        new String[][] {
          {"FITNESS", "健身", "Fitness"},
          {"YOGA", "瑜伽", "Yoga"},
          {"PILATES", "普拉提", "Pilates"},
          {"PERSONAL", "私教", "Personal training"}
        }) {
      var x = new DictionaryEntry();
      x.type = "courseCategory";
      x.code = v[0];
      x.name = v[1];
      x.nameEn = v[2];
      x.enabled = true;
      db.save(x);
    }
  }

  private AccessRole role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
