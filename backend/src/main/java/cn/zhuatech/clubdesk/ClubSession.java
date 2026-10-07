// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 具体课次与课程快照；教练、教室及名额不能并发冲突。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "club_session")
public class ClubSession {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "course_id", nullable = true)
  public Long courseId;

  @Column(name = "coach_id", nullable = true)
  public Long coachId;

  @Column(name = "room_id", nullable = true)
  public Long roomId;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "name_en", nullable = true, length = 160)
  public String nameEn;

  @Column(name = "starts_at", nullable = true)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = true)
  public Instant endsAt;

  @Column(name = "capacity", nullable = true)
  public int capacity;

  @Column(name = "cancel_hours", nullable = true)
  public int cancelHours;

  @Column(name = "state", nullable = true, length = 20)
  public String state;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "revision", nullable = true)
  public long revision;
}
