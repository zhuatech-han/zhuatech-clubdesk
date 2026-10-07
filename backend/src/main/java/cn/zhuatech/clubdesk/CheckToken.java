// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 短期单次签到码的散列，明文只在生成响应中出现。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "check_token")
public class CheckToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "member_id", nullable = true)
  public Long memberId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "token_hash", nullable = true, length = 64)
  public String tokenHash;

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "expires_at", nullable = true)
  public Instant expiresAt;

  @Column(name = "used_at", nullable = false)
  public Instant usedAt;
}
