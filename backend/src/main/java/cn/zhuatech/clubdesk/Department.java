// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 经营门店与IANA时区；门店停用不删除历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "department")
public class Department {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = true, length = 120)
  public String name;

  @Column(name = "zone", nullable = true, length = 80)
  public String zone;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;
}
