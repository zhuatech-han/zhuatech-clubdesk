// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 教室与可用容量；容量限制实际排课。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "room")
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "capacity", nullable = true)
  public int capacity;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
