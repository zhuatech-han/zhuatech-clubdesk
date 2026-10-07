// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 课程项目、分类与默认时长，不自动生成周期课。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "course")
public class Course {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "name_en", nullable = true, length = 160)
  public String nameEn;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "category_id", nullable = true)
  public Long categoryId;

  @Column(name = "duration_minutes", nullable = true)
  public int durationMinutes;

  @Column(name = "capacity", nullable = true)
  public int capacity;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
