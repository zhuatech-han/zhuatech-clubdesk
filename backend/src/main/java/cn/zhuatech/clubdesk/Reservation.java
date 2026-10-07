// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 会员与课次唯一预约、课时预留、核销和取消状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "reservation")
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "member_id", nullable = true)
  public Long memberId;

  @Column(name = "session_id", nullable = true)
  public Long sessionId;

  @Column(name = "pass_id", nullable = true)
  public Long passId;

  @Column(name = "state", nullable = true, length = 20)
  public String state;

  @Column(name = "note", nullable = true, length = 1000)
  public String note;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = true)
  public Instant updatedAt;

  @Column(name = "revision", nullable = true)
  public long revision;
}
