// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const statuses = {
  LOGIN: ["登录", "Sign in"],
  PASSWORD_CHANGE: ["修改密码", "Password changed"],
  PASS_ISSUED: ["开立会员卡", "Card issued"],
  PASS_ACTIVATED: ["足额收款生效", "Card activated"],
  PASS_FREEZE: ["冻结延期", "Freeze extended"],
  PASS_UNFREEZE: ["提前解冻", "Freeze ended early"],
  PASS_CLOSE: ["关闭会员卡", "Card closed"],
  SESSION_SAVED: ["安排课程草稿", "Class draft saved"],
  SESSION_PUBLISH: ["发布课程", "Class published"],
  SESSION_WITHDRAW: ["撤回课程", "Class withdrawn"],
  SESSION_CANCEL: ["取消课程", "Class cancelled"],
  SESSION_FINISH: ["结束课程", "Class completed"],
  BOOKING_CANCELLED: ["取消预约", "Booking cancelled"],
  ATTENDANCE_CORRECTED: ["纠正核销", "Attendance corrected"],
  ENTRY_VISIT: ["到店登记", "Venue check-in"],
  CASH_RECEIPT: ["收款登记", "Receipt recorded"],
  CASH_REFUND: ["退款登记", "Refund recorded"],
  CASH_REVERSAL: ["冲正登记", "Reversal recorded"],

  DRAFT: ["草稿", "Draft"],
  ACTIVE: ["有效", "Active"],
  UPCOMING: ["尚未开始", "Not started"],
  EXPIRED: ["已到期", "Expired"],
  FROZEN: ["冻结中", "Frozen"],
  CLOSED: ["已关闭", "Closed"],
  PUBLISHED: ["已发布", "Published"],
  CANCELLED: ["已取消", "Cancelled"],
  COMPLETED: ["已结束", "Completed"],
  BOOKED: ["已预约", "Booked"],
  ATTENDED: ["已签到", "Attended"],
  NO_SHOW: ["未到核销", "No-show"],
  CREDITS: ["次卡", "Class pack"],
  PERIOD: ["期限卡", "Period membership"],
  RECEIPT: ["收款登记", "Receipt"],
  REFUND: ["退款登记", "Refund"],
  REVERSAL: ["冲正登记", "Reversal"],
  ALL: ["全部门店", "All branches"],
  DEPARTMENT: ["本人门店", "Own branch"],
  MEMBER: ["本人会员", "Own membership"],
  TRAINER: ["本人指派课程", "Assigned classes"],
  RESERVE: ["预约预留", "Reservation hold"],
  RELEASE: ["取消返还", "Release"],
  ACTIVATE: ["开卡生效", "Activation"],
  CORRECTION: ["签到纠错", "Correction"],
};
/** 门店时区日期格式，不以浏览器本地时区替换门店日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localDay(instant, zone = "Asia/Shanghai") {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(instant));
  const read = (k) => parts.find((x) => x.type === k)?.value;
  return `${read("year")}-${read("month")}-${read("day")}`;
}
/** 日期时间输入明确表示门店墙上时间，服务端负责夏令时歧义拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(instant, zone) {
  const p = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(instant));
  const r = (k) => p.find((x) => x.type === k)?.value;
  return `${r("year")}-${r("month")}-${r("day")}T${r("hour")}:${r("minute")}`;
}
/** 预约预览使用卡快照、门店日期和完整课次覆盖；最终名额由服务端核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eligiblePass(p, s, zone) {
  if (!p || !s || p.state !== "ACTIVE" || p.departmentId !== s.departmentId)
    return false;
  const start = localDay(s.startsAt, zone);
  const end = localDay(new Date(new Date(s.endsAt).getTime() - 1), zone);
  if (start < p.startsOn || end > p.endsOn) return false;
  if (
    p.freezeStarted &&
    p.freezeUntil &&
    start >= p.freezeStarted &&
    start < p.freezeUntil
  )
    return false;
  return p.mode === "PERIOD" || p.credits - p.held - p.used > 0;
}
/** 会员取消截止时间使用服务端时间戳快照，不以设备日期拼接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function canCancel(s, now = Date.now()) {
  return new Date(s.startsAt).getTime() - s.cancelHours * 3600000 > now;
}
/** CSV解析支持引号、转义引号和换行，不通过简单逗号拆分吞掉会员备注。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parseCsv(text) {
  text = text.replace(/^\uFEFF/, "");
  const rows = [];
  let row = [],
    cell = "",
    quoted = false,
    closed = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else cell += c;
      continue;
    }
    if (closed && c !== "," && c !== "\n" && c !== "\r") {
      if (c === " " || c === "\t") continue;
      throw new Error("INVALID_CSV");
    }
    if (c === '"') {
      if (cell !== "") throw new Error("INVALID_CSV");
      quoted = true;
    } else if (c === ",") {
      row.push(cell);
      cell = "";
      closed = false;
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      if (row.some((x) => x !== "")) rows.push(row);
      row = [];
      cell = "";
      closed = false;
    } else cell += c;
  }
  if (quoted) throw new Error("INVALID_CSV");
  row.push(cell);
  if (row.some((x) => x !== "")) rows.push(row);
  return rows;
}
/** 只预览新增会员；行号与安全错误原因随失败保留，整批不提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parseMembers(text, branches) {
  const rows = parseCsv(text);
  if (
    rows.length < 2 ||
    rows[0].join(",") !== "code,name,departmentId,contactNote,enabled"
  )
    throw new Error("CSV_HEADER");
  if (rows.length > 301) throw new Error("INVALID_IMPORT");
  const seen = new Set();
  return rows.slice(1).map((row, index) => {
    const fail = (code) => {
      const e = new Error(code);
      e.row = index + 1;
      throw e;
    };
    if (row.length !== 5) fail("CSV_COLUMNS");
    const [raw, name, department, contactNote, enabled] = row.map((v) =>
      v.trim(),
    );
    const code = raw.toUpperCase();
    if (
      !/^[A-Z0-9_.-]{2,60}$/.test(code) ||
      !name ||
      name.length > 160 ||
      contactNote.length > 300
    )
      fail("INVALID_INPUT");
    if (
      !/^\d+$/.test(department) ||
      !branches.some((b) => b.id === Number(department))
    )
      fail("OUT_OF_SCOPE");
    if (enabled !== "true" && enabled !== "false") fail("CSV_BOOLEAN");
    if (seen.has(code)) fail("DUPLICATE_MEMBER");
    seen.add(code);
    return {
      code,
      name,
      departmentId: Number(department),
      contactNote,
      enabled: enabled === "true",
    };
  });
}
/** 精确金额显示由Intl呈现，计算与余额由后端小数类型提供。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value, currency = "CNY", lang = "zh") {
  return new Intl.NumberFormat(lang === "zh" ? "zh-CN" : "en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value || 0));
}
