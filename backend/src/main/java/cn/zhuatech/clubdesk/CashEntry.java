// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 外部收退款或冲正的人工记录，不连接银行或发起资金交易。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cash_entry")
public class CashEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "pass_id", nullable = true)
  public Long passId;

  @Column(name = "original_id", nullable = false)
  public Long originalId;

  @Column(name = "kind", nullable = true, length = 20)
  public String kind;

  @Column(name = "reference", nullable = true, length = 160)
  public String reference;

  @Column(name = "amount", nullable = true, precision = 18, scale = 2)
  public BigDecimal amount;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
