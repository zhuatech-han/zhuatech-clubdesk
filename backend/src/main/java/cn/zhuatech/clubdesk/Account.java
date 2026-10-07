// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 登录账号及不可转换的会员或教练绑定，密码散列不返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "account")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "username", nullable = true, length = 60)
  public String username;

  @Column(name = "display_name", nullable = true, length = 120)
  public String displayName;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "password_hash", nullable = true, length = 100)
  public String passwordHash;

  @Column(name = "role_id", nullable = true)
  public Long roleId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "member_id", nullable = false)
  public Long memberId;

  @Column(name = "coach_id", nullable = false)
  public Long coachId;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;
}
