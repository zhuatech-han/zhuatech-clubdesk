// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 业务操作历史，记录状态原因，不含口令或动态签到码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "club_event")
public class ClubEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "object_type", nullable = true, length = 30)
  public String objectType;

  @Column(name = "object_id", nullable = true)
  public Long objectId;

  @Column(name = "action", nullable = true, length = 50)
  public String action;

  @Column(name = "actor", nullable = true, length = 60)
  public String actor;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;
}
