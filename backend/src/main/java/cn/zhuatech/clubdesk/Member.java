// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import jakarta.persistence.*;
import java.time.*;

/** 会员本人档案与所属门店；不收集健康、生物识别或身份证资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "member")
public class Member {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = true, length = 60)
  public String code;

  @Column(name = "name", nullable = true, length = 160)
  public String name;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "contact_note", nullable = true, length = 300)
  public String contactNote;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;

  @Column(name = "revision", nullable = true)
  public long revision;
}
