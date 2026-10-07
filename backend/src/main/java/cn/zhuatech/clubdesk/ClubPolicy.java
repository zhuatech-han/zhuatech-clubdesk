// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.math.*;
import java.time.*;
import java.util.*;

/** 独立的金额、时区、预约和卡权益规则，便于边界回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class ClubPolicy {
  private ClubPolicy() {}

  /** 金额必须严格为正且最多两位有效小数，不静默舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal v) {
    if (v == null
        || v.signum() <= 0
        || v.compareTo(new BigDecimal("999999.99")) > 0
        || v.stripTrailingZeros().scale() > 2) throw new Problem(400, "INVALID_AMOUNT");
    return v.setScale(2, RoundingMode.UNNECESSARY);
  }

  /** 检查整数边界，不接受服务端反序列化截断。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int number(Integer v, int min, int max) {
    if (v == null || v < min || v > max) throw new Problem(400, "INVALID_INPUT");
    return v;
  }

  /** 将门店本地时间转换为唯一时刻；拒绝夏令时不存在或重复的本地时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant localTime(String v, String zone) {
    var local = LocalDateTime.parse(v);
    var id = ZoneId.of(zone);
    var offsets = id.getRules().getValidOffsets(local);
    if (offsets.size() != 1) throw new Problem(400, "AMBIGUOUS_LOCAL_TIME");
    return local.toInstant(offsets.getFirst());
  }

  /** 半开时间区间相交判断，首尾相接课程不互相冲突。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlap(Instant a, Instant b, Instant c, Instant d) {
    return a.isBefore(d) && c.isBefore(b);
  }

  /** 日期范围覆盖完整课次，冻结区间也不能参与课程。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void entitlement(ClubPass p, ClubSession s, String zone) {
    var z = ZoneId.of(zone);
    var date = s.startsAt.atZone(z).toLocalDate();
    if (!p.state.equals("ACTIVE")
        || date.isBefore(p.startsOn)
        || s.endsAt.isAfter(p.endsOn.plusDays(1).atStartOfDay(z).toInstant()))
      throw new Problem(409, "PASS_NOT_VALID");
    if (p.freezeStarted != null
        && p.freezeUntil != null
        && !date.isBefore(p.freezeStarted)
        && date.isBefore(p.freezeUntil)) throw new Problem(409, "PASS_FROZEN");
  }

  /** 计算卡当前展示状态，不因时钟经过而改写卡或财务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String effective(ClubPass p, LocalDate today) {
    if (!p.state.equals("ACTIVE")) return p.state;
    if (today.isBefore(p.startsOn)) return "UPCOMING";
    if (today.isAfter(p.endsOn)) return "EXPIRED";
    if (p.freezeUntil != null
        && p.freezeStarted != null
        && !today.isBefore(p.freezeStarted)
        && today.isBefore(p.freezeUntil)) return "FROZEN";
    return "ACTIVE";
  }

  /** 课时卡剩余可预约次数；期限卡不伪装成有限次数余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Integer available(ClubPass p) {
    return p.mode.equals("PERIOD") ? null : p.credits - p.held - p.used;
  }

  /** 用户取消以服务端课次快照为准，截止时刻及开课后不能自行取消。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean canCancel(ClubSession s, Instant now) {
    return now.isBefore(s.startsAt.minusSeconds(s.cancelHours * 3600L));
  }
}
