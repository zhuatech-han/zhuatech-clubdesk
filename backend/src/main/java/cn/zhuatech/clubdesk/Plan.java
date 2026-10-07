// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 次卡或期限卡套餐，开卡后约定快照不受套餐编辑影响。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "membership_plan")
public class Plan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "name_en", nullable = true, length = 160)
  public String nameEn;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "mode", nullable = true, length = 20)
  public String mode;

  @Column(name = "credits", nullable = true)
  public int credits;

  @Column(name = "valid_days", nullable = true)
  public int validDays;

  @Column(name = "price", nullable = true, precision = 18, scale = 2)
  public BigDecimal price;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
