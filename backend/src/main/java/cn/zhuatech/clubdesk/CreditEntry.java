// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可变课时流水，记录授权、预留、使用与返还。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "credit_entry")
public class CreditEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "pass_id", nullable = true)
  public Long passId;

  @Column(name = "reservation_id", nullable = false)
  public Long reservationId;

  @Column(name = "kind", nullable = true, length = 40)
  public String kind;

  @Column(name = "total_delta", nullable = true)
  public int totalDelta;

  @Column(name = "held_delta", nullable = true)
  public int heldDelta;

  @Column(name = "used_delta", nullable = true)
  public int usedDelta;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
