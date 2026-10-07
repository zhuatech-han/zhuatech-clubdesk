// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 教练主数据和门店范围；已安排课程保留历史关联。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "coach")
public class Coach {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = true, length = 60)
  public String code;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "specialty", nullable = true, length = 300)
  public String specialty;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
