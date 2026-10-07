// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 会员卡约定、收款生效状态及课时余额，冻结和关卡保存历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "club_pass")
public class ClubPass {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = true, length = 60)
  public String reference;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "member_id", nullable = true)
  public Long memberId;

  @Column(name = "plan_id", nullable = true)
  public Long planId;

  @Column(name = "plan_name", nullable = true, length = 160)
  public String planName;

  @Column(name = "plan_name_en", nullable = true, length = 160)
  public String planNameEn;

  @Column(name = "mode", nullable = true, length = 20)
  public String mode;

  @Column(name = "credits", nullable = true)
  public int credits;

  @Column(name = "held", nullable = true)
  public int held;

  @Column(name = "freeze_days", nullable = false)
  public int freezeDays;

  @Column(name = "used", nullable = true)
  public int used;

  @Column(name = "price", nullable = true, precision = 18, scale = 2)
  public BigDecimal price;

  @Column(name = "currency", nullable = true, length = 3)
  public String currency;

  @Column(name = "starts_on", nullable = true)
  public LocalDate startsOn;

  @Column(name = "ends_on", nullable = true)
  public LocalDate endsOn;

  @Column(name = "freeze_started", nullable = false)
  public LocalDate freezeStarted;

  @Column(name = "freeze_until", nullable = false)
  public LocalDate freezeUntil;

  @Column(name = "state", nullable = true, length = 20)
  public String state;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "revision", nullable = true)
  public long revision;
}
