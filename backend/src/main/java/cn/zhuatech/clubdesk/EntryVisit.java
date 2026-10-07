// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 期限卡到店记录；同会员每门店本地日期只登记一次，非门禁开锁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "entry_visit")
public class EntryVisit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "member_id", nullable = true)
  public Long memberId;

  @Column(name = "pass_id", nullable = true)
  public Long passId;

  @Column(name = "visit_date", nullable = true)
  public LocalDate visitDate;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;
}
