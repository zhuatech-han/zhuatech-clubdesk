<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import {
  ref,
  reactive,
  computed,
  onMounted,
  onBeforeUnmount,
  watch,
  nextTick,
} from "vue";
import QRCode from "qrcode";
import DataTable from "./DataTable.vue";
import { api, signIn, resetCsrf } from "./api.js";
import { commitChange, profileKey } from "./workflow.js";
import { fieldsFor } from "./schema.js";
import {
  statuses,
  localDay,
  localInput,
  eligiblePass,
  canCancel,
  money,
  parseMembers,
} from "./club.js";
import { errorMessage } from "./errors.js";
import { scannerSession } from "./scanner.js";
const lang = ref(localStorage.getItem("clubdesk.language") || "zh");
const me = ref(null),
  data = ref({}),
  admin = reactive({}),
  dashboard = ref({}),
  report = ref(null),
  audit = ref([]);
const route = ref("dashboard"),
  catalogTab = ref("courses"),
  adminTab = ref("users"),
  portalTab = ref("available");
const busy = ref(false),
  error = ref(""),
  notice = ref(""),
  menuOpen = ref(false),
  about = ref(false);
const exportReady = ref(null);
const login = reactive({ username: "", password: "" });
const modal = ref(null),
  form = reactive({}),
  formUnknown = ref(false),
  detail = ref(null),
  detailData = ref(null);
const branchFilter = ref(""),
  memberFilter = ref(""),
  sessionDay = ref(""),
  sessionState = ref("");
const period = reactive({
  from: new Date().toISOString().slice(0, 7) + "-01",
  to: new Date().toISOString().slice(0, 10),
});
const qr = ref(null),
  qrImage = ref(""),
  now = ref(Date.now());
const scanMode = ref("class"),
  scanSessionId = ref(null),
  scanCode = ref(""),
  scanMemberId = ref(null),
  scanPassId = ref(null);
const video = ref(null),
  cameraOn = ref(false);
let camera = null;
const qrFile = ref(null),
  csvFile = ref(null);
const csvText = ref(""),
  csvRows = ref([]),
  csvError = ref("");
const t = (zh, en) => (lang.value === "zh" ? zh : en);
const can = (permission) => me.value?.permissions?.includes(permission);
const label = (row) => {
  if (!row) return "";
  const v = row.name ?? row.displayName ?? row.code ?? "";
  if (lang.value === "en" && row.nameEn) return row.nameEn;
  if (String(v).includes(" / "))
    return String(v).split(" / ")[lang.value === "zh" ? 0 : 1] || v;
  return v;
};
const status = (value) => {
  if (statuses[value]) return statuses[value][lang.value === "zh" ? 0 : 1];
  if (typeof value === "string") {
    const descriptions = {
      members: t("会员", "Member"),
      coaches: t("教练", "Coach"),
      rooms: t("教室", "Room"),
      courses: t("课程", "Course"),
      plans: t("套餐", "Plan"),
      users: t("账号", "Account"),
      roles: t("角色", "Role"),
      departments: t("门店", "Branch"),
      dictionaries: t("分类", "Category"),
      settings: t("参数", "Setting"),
      menus: t("菜单", "Menu"),
      permissions: t("权限", "Permission"),
    };
    for (const [prefix, action] of [
      ["MASTER_SAVE_", t("保存", "Save ")],
      ["MASTER_DELETE_", t("删除", "Delete ")],
      ["ADMIN_UPDATE_", t("管理修改", "Admin update: ")],
      ["ADMIN_DELETE_", t("管理删除", "Admin delete: ")],
    ])
      if (value.startsWith(prefix))
        return (
          action +
          (descriptions[value.slice(prefix.length)] || t("资料", "record"))
        );
  }
  return value || "";
};
const currency = computed(
  () => data.value.currency || dashboard.value.currency || "CNY",
);
const cashLabel = (v, c) => money(v, c || currency.value, lang.value);
const branches = computed(() => data.value.branches || admin.departments || []);
const zoneFor = (id) =>
  branches.value.find((x) => x.id === id)?.zone || "Asia/Shanghai";
const dateTime = (value, department) =>
  value
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: zoneFor(department),
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).format(new Date(value))
    : "";
const permissionsName = (code) =>
  label((admin.permissions || []).find((x) => x.code === code)) ||
  {
    read: t("查看门店业务", "Branch records"),
    master: t("维护基础资料", "Catalogue"),
    membership: t("会员卡与预约", "Memberships"),
    schedule: t("排课", "Scheduling"),
    checkin: t("前台签到", "Reception"),
    finance: t("资金登记", "Cash records"),
    report: t("对账导出", "Reports"),
    dashboard: t("工作台", "Workspace"),
    admin: t("系统管理", "Administration"),
    audit: t("审计", "Audit"),
    portal: t("会员入口", "Member portal"),
    coach: t("教练入口", "Coach portal"),
  }[code] ||
  code;
const nav = computed(() => me.value?.menus || []);
const title = computed(() => {
  const m = nav.value.find((x) => x.code === route.value);
  return m
    ? lang.value === "zh"
      ? m.name
      : m.nameEn
    : t("会员与课程经营", "Memberships & classes");
});
const allPassRows = computed(() =>
  (data.value.passes || []).map((x) => ({
    ...x.pass,
    ...Object.fromEntries(Object.entries(x).filter(([k]) => k !== "pass")),
    displayState:
      x.effective === "DRAFT"
        ? t("待收款", "Awaiting payment")
        : status(x.effective),
    planLabel: lang.value === "zh" ? x.pass.planName : x.pass.planNameEn,
    priceLabel: cashLabel(x.pass.price, x.pass.currency),
    balanceLabel:
      x.available === null ? t("不限课次", "Unlimited") : String(x.available),
    branchName: label(branches.value.find((b) => b.id === x.pass.departmentId)),
  })),
);
const passRows = computed(() =>
  allPassRows.value.filter(
    (x) =>
      (!branchFilter.value || x.departmentId === branchFilter.value) &&
      (!memberFilter.value || x.memberId === memberFilter.value),
  ),
);
const sessionStateLabel = (x) => {
  const s = x.session || x;
  if (s.state !== "PUBLISHED") return status(s.state);
  if (now.value >= new Date(s.endsAt).getTime())
    return t("待结课", "Awaiting completion");
  if (now.value >= new Date(s.startsAt).getTime())
    return t("进行中", "In progress");
  return x.openSeats === 0
    ? t("名额已满", "Full")
    : t("可预约", "Open for booking");
};
const sessionRows = computed(() =>
  (data.value.sessions || [])
    .map((x) => ({
      ...x.session,
      zone: x.zone,
      booked: x.booked,
      openSeats: x.openSeats,
      coachName: x.coachName,
      roomName: x.roomName,
      cancelDeadline: x.cancelDeadline,
      nameLabel: label(x.session),
      displayState: sessionStateLabel(x),
      timeLabel: dateTime(x.session.startsAt, x.session.departmentId),
      capacityLabel: `${x.booked} / ${x.session.capacity}`,
    }))
    .filter(
      (x) =>
        (!branchFilter.value || x.departmentId === branchFilter.value) &&
        (!sessionDay.value ||
          localDay(x.startsAt, x.zone) === sessionDay.value) &&
        (!sessionState.value || x.state === sessionState.value),
    )
    .sort((a, b) => new Date(a.startsAt) - new Date(b.startsAt)),
);
const allSessions = computed(() =>
  (data.value.sessions || []).map((x) => ({
    ...x.session,
    ...x,
    session: undefined,
    nameLabel: label(x.session),
    timeLabel: dateTime(x.session.startsAt, x.session.departmentId),
    displayState: sessionStateLabel(x),
    capacityLabel: `${x.booked}/${x.session.capacity}`,
  })),
);
const bookingRows = computed(() =>
  (data.value.reservations || [])
    .map((r) => {
      const s = allSessions.value.find((x) => x.id === r.sessionId);
      return {
        ...r,
        memberName: label(
          (data.value.members || [data.value.member]).find(
            (x) => x?.id === r.memberId,
          ),
        ),
        className: s?.nameLabel || "",
        timeLabel: s?.timeLabel || "",
        displayState: status(r.state),
        session: s,
      };
    })
    .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt)),
);
const catalogueLabels = computed(() => ({
  courses: t("课程项目", "Courses"),
  plans: t("卡套餐", "Plans"),
  coaches: t("教练", "Coaches"),
  rooms: t("教室", "Rooms"),
}));
const adminLabels = computed(() => ({
  users: t("登录账号", "Accounts"),
  roles: t("角色", "Roles"),
  permissions: t("权限目录", "Permissions"),
  menus: t("菜单", "Menus"),
  departments: t("门店", "Branches"),
  dictionaries: t("课程分类", "Course categories"),
  settings: t("参数", "Settings"),
}));
const catalogueRows = computed(() =>
  (data.value[catalogTab.value] || [])
    .filter((x) => !branchFilter.value || x.departmentId === branchFilter.value)
    .map((x) => ({
      ...x,
      label: label(x),
      branchName: label(branches.value.find((b) => b.id === x.departmentId)),
      enabledLabel: x.enabled ? t("启用", "Enabled") : t("停用", "Disabled"),
      categoryLabel: label(
        (data.value.categories || []).find((c) => c.id === x.categoryId),
      ),
      modeLabel: status(x.mode),
      priceLabel: cashLabel(x.price),
      creditsLabel:
        x.mode === "PERIOD" ? t("不限课次", "Unlimited") : x.credits,
    })),
);
const adminRows = computed(() =>
  (admin[adminTab.value] || [])
    .filter(
      (x) => adminTab.value !== "dictionaries" || x.type === "courseCategory",
    )
    .map((x) => ({
      ...x,
      label: label(x),
      roleName: label((admin.roles || []).find((r) => r.id === x.roleId)),
      branchName: label(
        (admin.departments || []).find((d) => d.id === x.departmentId),
      ),
      scopeLabel: status(x.scope),
      permissionsLabel: (x.permissions || []).map(permissionsName).join("、"),
      enabledLabel: x.enabled ? t("启用", "Enabled") : t("停用", "Disabled"),
    })),
);
const columns = (items) =>
  items.map(([key, zh, en]) => ({ key, label: t(zh, en) }));
const memberColumns = computed(() =>
  columns([
    ["code", "会员编号", "Member code"],
    ["name", "姓名", "Name"],
    ["branchName", "门店", "Branch"],
    ["contactNote", "联系备注", "Contact note"],
    ["enabledLabel", "状态", "State"],
  ]),
);
const passColumns = computed(() =>
  columns([
    ["reference", "卡号", "Card"],
    ["memberName", "会员", "Member"],
    ["planLabel", "套餐", "Plan"],
    ["displayState", "状态", "State"],
    ["balanceLabel", "未预留课时", "Unheld credits"],
    ["held", "已预留", "Reserved"],
    ["used", "已核销", "Used"],
    ["endsOn", "到期日期", "Expires"],
  ]),
);
const sessionColumns = computed(() =>
  columns([
    ["timeLabel", "开课时间", "Start time"],
    ["nameLabel", "课程", "Class"],
    ["coachName", "教练", "Coach"],
    ["roomName", "教室", "Room"],
    ["capacityLabel", "人数／容量", "Booked / seats"],
    ["displayState", "状态", "State"],
  ]),
);
const bookingColumns = computed(() =>
  columns([
    ["className", "课程", "Class"],
    ["timeLabel", "开课时间", "Start time"],
    ["displayState", "状态", "State"],
  ]),
);
const catalogColumns = computed(() => {
  const cols = [
    ["label", "名称", "Name"],
    ["branchName", "门店", "Branch"],
  ];
  if (catalogTab.value === "courses")
    cols.push(
      ["categoryLabel", "分类", "Category"],
      ["durationMinutes", "分钟", "Minutes"],
      ["capacity", "默认名额", "Seats"],
    );
  if (catalogTab.value === "plans")
    cols.push(
      ["modeLabel", "类型", "Type"],
      ["creditsLabel", "课时", "Credits"],
      ["validDays", "有效天数", "Days"],
      ["priceLabel", "价格", "Price"],
    );
  if (catalogTab.value === "rooms") cols.push(["capacity", "容量", "Capacity"]);
  if (catalogTab.value === "coaches")
    cols.push(["specialty", "专长", "Specialty"]);
  cols.push(["enabledLabel", "状态", "State"]);
  return columns(cols);
});
const adminColumns = computed(() => {
  const type = adminTab.value;
  if (type === "users")
    return columns([
      ["username", "账号", "Username"],
      ["displayName", "名称", "Name"],
      ["roleName", "角色", "Role"],
      ["branchName", "门店", "Branch"],
      ["enabledLabel", "状态", "State"],
    ]);
  if (type === "roles")
    return columns([
      ["label", "角色", "Role"],
      ["scopeLabel", "范围", "Scope"],
      ["permissionsLabel", "权限", "Permissions"],
    ]);
  if (type === "settings")
    return columns([
      ["code", "参数", "Setting"],
      ["value", "值", "Value"],
    ]);
  if (type === "departments")
    return columns([
      ["label", "门店", "Branch"],
      ["zone", "时区", "Timezone"],
      ["enabledLabel", "状态", "State"],
    ]);
  if (type === "menus")
    return columns([
      ["name", "中文名称", "Chinese name"],
      ["nameEn", "英文名称", "English name"],
      ["permissionCode", "权限", "Permission"],
      ["position", "顺序", "Order"],
      ["enabledLabel", "状态", "State"],
    ]);
  return columns([
    ["code", "代码", "Code"],
    ["label", "名称", "Name"],
    ...(type === "dictionaries" ? [["enabledLabel", "状态", "State"]] : []),
  ]);
});
const fields = computed(() =>
  fieldsFor(modal.value?.kind, {
    t,
    data: data.value,
    admin,
    form,
    editing: Boolean(modal.value?.id),
    label,
  }),
);
const currentSession = computed(() =>
  allSessions.value.find((s) => s.id === modal.value?.sessionId),
);
const modalTitle = computed(() => modal.value?.title || "");
const selectedPass = computed(() =>
  detail.value?.kind === "pass" ? detailData.value?.summary?.pass : null,
);
const selectedSession = computed(() =>
  detail.value?.kind === "session" ? detailData.value?.summary?.session : null,
);
const scanSessions = computed(() =>
  allSessions.value
    .filter((x) => x.state === "PUBLISHED")
    .sort((a, b) => new Date(a.startsAt) - new Date(b.startsAt)),
);
const visitPasses = computed(() =>
  allPassRows.value.filter(
    (x) =>
      x.memberId === scanMemberId.value &&
      x.mode === "PERIOD" &&
      x.effective === "ACTIVE",
  ),
);
const portalAvailable = computed(() =>
  sessionRows.value.filter(
    (x) =>
      x.state === "PUBLISHED" && new Date(x.startsAt).getTime() > now.value,
  ),
);
const ownReservation = (sessionId) =>
  (data.value.reservations || []).some(
    (x) => x.sessionId === sessionId && x.state !== "CANCELLED",
  );
let timer;
function stopCamera() {
  camera?.stop();
  camera = null;
  cameraOn.value = false;
}
function clearPrivate() {
  stopCamera();
  error.value = "";
  notice.value = "";
  exportReady.value = null;
  me.value = null;
  data.value = {};
  dashboard.value = {};
  report.value = null;
  audit.value = [];
  for (const key of Object.keys(admin)) delete admin[key];
  modal.value = null;
  detail.value = null;
  detailData.value = null;
  qr.value = null;
  qrImage.value = "";
  csvText.value = "";
  csvRows.value = [];
  csvError.value = "";
  scanCode.value = "";
  scanSessionId.value = null;
  scanMemberId.value = null;
  formUnknown.value = false;
  memberFilter.value = "";
  branchFilter.value = "";
  menuOpen.value = false;
  login.password = "";
  login.username = "";
  for (const key of Object.keys(form)) delete form[key];
  resetCsrf();
}
function showError(e) {
  if (e.profile && profileKey(e.profile) !== profileKey(me.value)) {
    notice.value = "";
    exportReady.value = null;
    csvText.value = "";
    csvRows.value = [];
    csvError.value = "";
    scanCode.value = "";
    data.value = {};
    dashboard.value = {};
    report.value = null;
    audit.value = [];
    for (const key of Object.keys(admin)) delete admin[key];
    me.value = e.profile;
    modal.value = null;
    detail.value = null;
    detailData.value = null;
    qr.value = null;
    qrImage.value = "";
    stopCamera();
    for (const key of Object.keys(form)) delete form[key];
    route.value = e.profile.menus?.[0]?.code || "none";
    error.value = t(
      "账号权限或门店已更新，旧资料已清空。请刷新后继续。",
      "Account access or branch changed. Old records were cleared. Refresh to continue.",
    );
    return;
  }

  if (e.message === "UNAUTHENTICATED") clearPrivate();
  error.value = errorMessage(e, lang.value);
  if (e.message === "RESULT_UNKNOWN") formUnknown.value = true;
}
async function refresh() {
  const profile = await api("/auth/me");
  if (profileKey(profile) !== profileKey(me.value)) {
    notice.value = "";
    exportReady.value = null;
    csvText.value = "";
    csvRows.value = [];
    csvError.value = "";
    scanCode.value = "";
    data.value = {};
    detail.value = null;
    detailData.value = null;
    modal.value = null;
    qr.value = null;
    qrImage.value = "";
    stopCamera();
    for (const k of Object.keys(admin)) delete admin[k];
  }
  me.value = profile;
  if (!nav.value.some((m) => m.code === route.value))
    route.value = nav.value[0]?.code || "none";
  let result = {};
  if (profile.memberId != null) {
    result = await api("/portal");
    result.members = [result.member];
  } else if (profile.coachId != null) result = await api("/coaching");
  else if (can("read")) result = await api("/workspace");
  const tasks = [];
  if (can("admin"))
    for (const type of [
      "users",
      "roles",
      "permissions",
      "menus",
      "departments",
      "dictionaries",
      "settings",
    ])
      tasks.push(
        api("/admin/" + type).then((x) => {
          admin[type] = x;
        }),
      );
  if (can("dashboard"))
    tasks.push(
      api("/dashboard").then((x) => {
        dashboard.value = x;
      }),
    );
  if (can("report"))
    tasks.push(
      api(`/reports?from=${period.from}&to=${period.to}`).then((x) => {
        report.value = x;
      }),
    );
  if (can("audit"))
    tasks.push(
      api("/audit").then((x) => {
        audit.value = x;
      }),
    );
  await Promise.all(tasks);
  data.value = result;
  if (detail.value) {
    const path =
      detail.value.kind === "pass"
        ? "/passes/"
        : detail.value.kind === "session"
          ? "/sessions/"
          : "/reservations/";
    detailData.value = await api(path + detail.value.id);
  }
}
async function run(job) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    return await job();
  } catch (e) {
    showError(e);
  } finally {
    busy.value = false;
  }
}
async function sign() {
  formUnknown.value = false;
  await run(async () => {
    me.value = await signIn({ ...login });
    login.password = "";
    await refresh();
  });
}
async function checkLogin() {
  formUnknown.value = false;
  await run(refresh);
}
async function logout() {
  await run(async () => {
    try {
      await api("/auth/logout", "POST", {});
    } finally {
      clearPrivate();
    }
  });
}
function navigate(code) {
  exportReady.value = null;
  route.value = code;
  detail.value = null;
  detailData.value = null;
  modal.value = null;
  error.value = "";
  notice.value = "";
  menuOpen.value = false;
  stopCamera();
  scanCode.value = "";
}
function openForm(kind, row = null, extra = {}) {
  error.value = "";
  notice.value = "";
  formUnknown.value = false;
  for (const k of Object.keys(form)) delete form[k];
  Object.assign(
    form,
    {
      enabled: true,
      departmentId: branches.value.find((b) => b.enabled)?.id || 1,
      nameEn: "",
      mode: "CREDITS",
      credits: 10,
      validDays: 30,
      price: "100.00",
      durationMinutes: 60,
      capacity: 10,
      cancelHours: 12,
      scope: "DEPARTMENT",
      permissions: [],
      zone: "Asia/Shanghai",
      type: "courseCategory",
    },
    row ? JSON.parse(JSON.stringify(row)) : {},
    extra,
  );
  const name =
    {
      members: t("会员", "member"),
      coaches: t("教练", "coach"),
      rooms: t("教室", "room"),
      courses: t("课程项目", "course"),
      plans: t("卡套餐", "plan"),
      users: t("账号", "account"),
      roles: t("角色", "role"),
      departments: t("门店", "branch"),
      dictionaries: t("课程分类", "course category"),
      menus: t("菜单", "menu"),
      permissions: t("权限名称", "permission label"),
      settings: t("系统参数", "setting"),
    }[kind] || "";
  modal.value = {
    kind,
    id: row?.id,
    title: (row ? t("编辑", "Edit ") : t("新增", "New ")) + name,
  };
}
function issueCard(member = null) {
  openForm("issue", null, {
    memberId:
      member?.id ?? (data.value.members || []).find((x) => x.enabled)?.id,
    fixedMember: Boolean(member),
    planId: null,
    startsOn: localDay(
      new Date(),
      zoneFor(member?.departmentId || branches.value[0]?.id),
    ),
    note: "",
  });
  modal.value.title = t("开立会员卡", "Issue membership card");
}
function sessionForm(row = null) {
  openForm("session", row, {
    localStart: row
      ? localInput(row.startsAt, zoneFor(row.departmentId))
      : localInput(Date.now() + 86400000, zoneFor(branches.value[0]?.id)),
    courseId: row?.courseId || null,
    coachId: row?.coachId || null,
    roomId: row?.roomId || null,
    note: row?.note || "",
  });
  modal.value.title = row
    ? t("编辑课程草稿", "Edit class draft")
    : t("安排课次", "Schedule a class");
}
function openBooking(session) {
  const member =
    data.value.member || (data.value.members || []).find((x) => x.enabled);
  openForm("booking", null, {
    memberId: member?.id,
    passId: null,
    fixedMember: Boolean(data.value.member),
  });
  modal.value.sessionId = session.id;
  modal.value.title = t("预约课程", "Book a class");
  updatePassOptions();
}
function updatePassOptions() {
  if (modal.value?.kind !== "booking") return;
  const s = currentSession.value;
  form.passOptions = allPassRows.value
    .filter(
      (x) =>
        x.memberId === form.memberId &&
        eligiblePass(x, s, zoneFor(s?.departmentId)),
    )
    .map((x) => ({
      value: x.id,
      label: `${x.reference} · ${x.planLabel} · ${x.balanceLabel} ${t("可用", "available")}`,
    }));
  if (!form.passOptions.some((x) => x.value === form.passId))
    form.passId = form.passOptions[0]?.value || null;
}
function action(path, row, title, kind = "action", note = "") {
  openForm(kind, null, { revision: row.revision, note, days: 7 });
  modal.value.path = path;
  modal.value.title = title;
}
function passAction(actionName) {
  const p = selectedPass.value;
  action(
    `/passes/${p.id}/${actionName}`,
    p,
    {
      freeze: t("冻结会员卡", "Freeze membership"),
      unfreeze: t("提前解冻", "End freeze early"),
      close: t("关闭会员卡", "Close membership"),
    }[actionName],
    actionName === "freeze" ? "freeze" : "action",
  );
  if (actionName === "close")
    modal.value.warning = t(
      "该卡未完成的预约会取消并返还预留课时。收款历史保留；退款须另行登记。",
      "Pending bookings on this card will be cancelled and holds released. Cash history remains; record actual refunds separately.",
    );
}
function sessionAction(actionName) {
  const s = selectedSession.value;
  action(
    `/sessions/${s.id}/${actionName}`,
    s,
    {
      publish: t("发布课程", "Publish class"),
      withdraw: t("撤回为草稿", "Withdraw to draft"),
      cancel: t("取消课程", "Cancel class"),
      finish: t("结束课程", "Complete class"),
    }[actionName],
  );
  if (actionName === "finish")
    modal.value.warning = t(
      "请核对实际出勤。仍未签到的预约会记为未到并核销一次课时。",
      "Review actual attendance. Remaining bookings will be marked no-show and consume one credit.",
    );
}
function bookingAction(row, kind) {
  const portal = Boolean(me.value.memberId);
  const path = (portal ? "/portal" : "") + `/reservations/${row.id}/${kind}`;
  action(
    path,
    row,
    {
      cancel: t("取消预约", "Cancel booking"),
      attend: t("现场签到", "Record attendance"),
      "no-show": t("登记未到", "Record no-show"),
      correct: t("纠正核销记录", "Correct attendance"),
    }[kind],
    "action",
    kind === "attend"
      ? t("现场核验到课", "Attendance verified at reception")
      : portal
        ? t("本人取消", "Member cancellation")
        : "",
  );
}
async function showDetail(kind, id) {
  await run(async () => {
    const path =
      kind === "pass"
        ? "/passes/"
        : kind === "session"
          ? "/sessions/"
          : "/reservations/";
    const result = await api(path + id);
    detail.value = { kind, id };
    detailData.value = result;
  });
}
function accountFor(member, coach) {
  const bound = (admin.users || []).find((x) =>
    member ? x.memberId === member.id : x.coachId === coach.id,
  );
  if (bound) {
    openForm("users", bound);
    return;
  }
  const role = (admin.roles || []).find(
    (x) => x.scope === (member ? "MEMBER" : "TRAINER"),
  );
  openForm("users", null, {
    username: (member ? "member." : "coach.") + (member?.id || coach?.id),
    displayName: member?.name || coach?.name,
    departmentId: member?.departmentId || coach?.departmentId,
    roleId: role?.id,
    memberId: member?.id || null,
    coachId: coach?.id || null,
    password: "",
  });
}
function openCash() {
  openForm("cash", null, {
    kind: selectedPass.value.state === "DRAFT" ? "RECEIPT" : "REFUND",
    reference: "",
    amount: detailData.value.summary.due,
    originalId: null,
    note: "",
    revision: selectedPass.value.revision,
  });
  modal.value.id = selectedPass.value.id;
  modal.value.title = t("登记实际资金交接", "Record actual cash movement");
  setCashOptions();
}
function setCashOptions() {
  if (modal.value?.kind !== "cash") return;
  const p = selectedPass.value;
  form.cashOptions =
    p.state === "DRAFT"
      ? [
          { value: "RECEIPT", label: status("RECEIPT") },
          { value: "REVERSAL", label: status("REVERSAL") },
        ]
      : p.state === "CLOSED"
        ? [
            { value: "REFUND", label: status("REFUND") },
            { value: "REVERSAL", label: status("REVERSAL") },
          ]
        : [];
  const original = (detailData.value.cash || []).filter(
    (x) =>
      !x.reversed &&
      (form.kind === "REFUND"
        ? x.entry.kind === "RECEIPT" && Number(x.refundable) > 0
        : x.entry.kind !== "REVERSAL"),
  );
  form.originalOptions = original.map((x) => ({
    value: x.entry.id,
    label: `${x.entry.reference} · ${status(x.entry.kind)} · ${cashLabel(form.kind === "REFUND" ? x.refundable : x.entry.amount, p.currency)}`,
  }));
  if (form.kind === "RECEIPT") {
    form.originalId = null;
    form.amount = detailData.value.summary.due;
  } else if (!original.some((x) => x.entry.id === form.originalId)) {
    form.originalId = original[0]?.entry.id || null;
    setOriginalAmount();
  }
}
function setOriginalAmount() {
  if (modal.value?.kind !== "cash" || form.kind === "RECEIPT") return;
  const e = detailData.value.cash.find((x) => x.entry.id === form.originalId);
  form.amount = e
    ? form.kind === "REFUND"
      ? e.refundable
      : e.entry.amount
    : "";
}
async function saveForm() {
  if (formUnknown.value) return;
  await run(async () => {
    const m = { ...modal.value };
    let path,
      method = "POST",
      body = {};
    if (m.kind === "import") {
      if (!csvRows.value.length || csvError.value) return;
      path = "/members/import";
      body = csvRows.value;
    } else if (m.path) {
      path = m.path;
      body = {
        revision: form.revision,
        note: form.note,
        ...(m.kind === "freeze" ? { days: form.days } : {}),
      };
    } else if (m.kind === "booking") {
      const s = currentSession.value;
      const p = allPassRows.value.find((x) => x.id === form.passId);
      if (!p) throw new Error("NO_CREDITS");
      path = me.value.memberId ? "/portal/reservations" : "/reservations";
      body = {
        memberId: form.memberId,
        sessionId: s.id,
        passId: p.id,
        sessionRevision: s.revision,
        passRevision: p.revision,
      };
    } else if (m.kind === "cash") {
      path = `/passes/${m.id}/cash`;
      body = {
        kind: form.kind,
        reference: form.reference,
        amount: String(form.amount),
        originalId: form.originalId,
        note: form.note,
        revision: form.revision,
      };
    } else if (m.kind === "password") {
      await api("/auth/password", "POST", {
        oldPassword: form.oldPassword,
        newPassword: form.newPassword,
      });
      clearPrivate();
      notice.value = t(
        "密码已修改，请使用新密码登录。",
        "Password changed. Sign in with the new password.",
      );
      return;
    } else {
      for (const f of fields.value)
        if (f.type !== "hidden" && form[f.key] !== undefined)
          body[f.key] =
            form[f.key] === "" && f.type === "select" ? null : form[f.key];
      if (
        ["members", "coaches", "rooms", "courses", "plans"].includes(m.kind)
      ) {
        path = "/master/" + m.kind + (m.id ? "/" + m.id : "");
        method = m.id ? "PUT" : "POST";
        if (m.id) body.revision = form.revision;
        if (m.kind === "plans" && form.mode === "PERIOD") body.credits = 0;
      } else if (m.kind === "session") {
        path = "/sessions" + (m.id ? "/" + m.id : "");
        method = m.id ? "PUT" : "POST";
        if (m.id) body.revision = form.revision;
      } else if (m.kind === "issue") path = "/passes";
      else {
        path = "/admin/" + m.kind + (m.id ? "/" + m.id : "");
        method = m.id ? "PUT" : "POST";
      }
    }
    const refreshError = await commitChange(
      () => api(path, method, body),
      () => {
        modal.value = null;
        for (const k of Object.keys(form)) delete form[k];
        csvRows.value = [];
        csvText.value = "";
        notice.value = t("已保存。", "Saved.");
      },
      refresh,
    );
    if (refreshError) {
      if (refreshError.message === "UNAUTHENTICATED") clearPrivate();
      error.value = t(
        "已保存，但暂时无法重载资料。请刷新核对，不要重复登记。",
        "Saved, but records could not be reloaded. Refresh to check; do not submit again.",
      );
    }
  });
}
function askDelete(kind, row, management = false) {
  openForm("action", null, {});
  modal.value.title = t("删除无引用记录", "Delete an unreferenced record");
  modal.value.deletePath =
    (management ? "/admin/" : "/master/") +
    kind +
    "/" +
    row.id +
    (management ? "" : "?revision=" + row.revision);
  modal.value.warning = t(
    "已有引用的记录不能删除；可改为停用。",
    "Referenced records cannot be deleted; disable them instead.",
  );
}
async function remove() {
  await run(async () => {
    await api(modal.value.deletePath, "DELETE");
    modal.value = null;
    await refresh();
    notice.value = t("已删除。", "Deleted.");
  });
}
function closeForm() {
  modal.value = null;
  error.value = "";
  formUnknown.value = false;
  for (const k of Object.keys(form)) delete form[k];
  csvText.value = "";
  csvRows.value = [];
  csvError.value = "";
}
function formChange(key) {
  if (key === "mode" && form.mode === "PERIOD") form.credits = 0;
  if (key === "memberId") {
    if (modal.value.kind === "booking") updatePassOptions();
    if (modal.value.kind === "issue") {
      form.planId = null;
      const m = (data.value.members || []).find((x) => x.id === form.memberId);
      form.startsOn = localDay(Date.now(), zoneFor(m?.departmentId));
    }
  }
  if (key === "departmentId" && modal.value.kind === "session") {
    form.courseId = null;
    form.coachId = null;
    form.roomId = null;
    form.localStart = localInput(
      Date.now() + 86400000,
      zoneFor(form.departmentId),
    );
  }
  if (key === "courseId" && modal.value.kind === "session") {
    const c = (data.value.courses || []).find((x) => x.id === form.courseId);
    if (c) form.capacity = c.capacity;
  }
  if (key === "kind") setCashOptions();
  if (key === "originalId") setOriginalAmount();
  if (key === "scope" && modal.value.kind === "roles") {
    if (form.scope === "MEMBER") form.permissions = ["portal"];
    if (form.scope === "TRAINER") form.permissions = ["coach"];
  }
}
async function downloadText(text, name) {
  const url = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
async function download(type) {
  await run(async () => {
    const text = await api("/exports/" + type + ".csv", "GET", undefined, true);
    await downloadText(text, "clubdesk-" + type + ".csv");
    exportReady.value = {
      url: "/api/exports/" + type + ".csv",
      name: "clubdesk-" + type + ".csv",
    };
    notice.value = t(
      "导出已准备，可查看下载列表或使用下方下载链接。",
      "Export ready. Check your downloads or use the link below.",
    );
  });
}
function importForm() {
  openForm("import");
  modal.value.title = t("批量新增会员", "Import new members");
  csvText.value = "";
  csvRows.value = [];
  csvError.value = "";
}
function previewCsv() {
  try {
    csvRows.value = parseMembers(csvText.value, branches.value);
    csvError.value = "";
  } catch (e) {
    csvRows.value = [];
    csvError.value = errorMessage(e, lang.value);
  }
}
async function importFile(e) {
  const file = e.target.files[0];
  if (!file) return;
  if (file.size > 1024 * 1024) {
    csvError.value = errorMessage(new Error("UPLOAD_TOO_LARGE"), lang.value);
    csvRows.value = [];
    return;
  }
  csvText.value = await file.text();
  previewCsv();
  e.target.value = "";
}
async function mintQr() {
  await run(async () => {
    qr.value = await api("/portal/check-token", "POST", {});
    qrImage.value = await QRCode.toDataURL(qr.value.token, {
      width: 256,
      margin: 3,
      errorCorrectionLevel: "M",
    });
  });
}
async function submitScan() {
  const token = scanCode.value.trim();
  if (!token) return;
  await run(async () => {
    const result = await api("/checkin", "POST", {
      token,
      sessionId: scanMode.value === "class" ? scanSessionId.value : null,
      passId: null,
    });
    scanCode.value = "";
    notice.value =
      result.memberName + " · " + t("签到已登记", "Check-in recorded");
    await refresh();
  });
}
async function startCamera() {
  error.value = "";
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    showError(new Error("CAMERA_UNAVAILABLE"));
    return;
  }
  stopCamera();
  cameraOn.value = true;
  await nextTick();
  let active;
  try {
    const { BrowserQRCodeReader } = await import("@zxing/browser");
    if (!cameraOn.value || !video.value) return;
    active = scannerSession(new BrowserQRCodeReader(), video.value, (value) => {
      scanCode.value = value;
      cameraOn.value = false;
      submitScan();
    });
    camera = active;
    await active.ready;
  } catch {
    if (active && camera !== active) return;
    stopCamera();
    showError(new Error("CAMERA_UNAVAILABLE"));
  }
}
async function readQrFile(e) {
  const file = e.target.files[0];
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) {
    showError(new Error("UPLOAD_TOO_LARGE"));
    return;
  }
  let url;
  try {
    const { BrowserQRCodeReader } = await import("@zxing/browser");
    url = URL.createObjectURL(file);
    const result = await new BrowserQRCodeReader().decodeFromImageUrl(url);
    scanCode.value = result.getText();
    await submitScan();
  } catch {
    showError(new Error("NO_QR_FOUND"));
  } finally {
    if (url) URL.revokeObjectURL(url);
    e.target.value = "";
  }
}
async function visit() {
  await run(async () => {
    await api("/visits", "POST", {
      memberId: scanMemberId.value,
      passId: scanPassId.value,
    });
    notice.value = t("到店已登记。", "Venue entry recorded.");
    await refresh();
  });
}
const visitCols = computed(() =>
  columns([
    ["visitDate", "到店日期", "Date"],
    ["createdLabel", "登记时间", "Recorded at"],
  ]),
);
const memberRows = computed(() =>
  (data.value.members || [])
    .filter((x) => !branchFilter.value || x.departmentId === branchFilter.value)
    .map((x) => ({
      ...x,
      branchName: label(branches.value.find((b) => b.id === x.departmentId)),
      enabledLabel: x.enabled ? t("启用", "Enabled") : t("停用", "Disabled"),
    })),
);
const settingLabel = (code) =>
  ({
    companyName: t("经营主体名称", "Business name"),
    currency: t("实例币种", "Currency"),
    bookingNotice: t("会员预约说明", "Booking notice"),
  })[code] || code;
const atWindow = (s) =>
  s.state === "PUBLISHED" &&
  now.value >= new Date(s.startsAt).getTime() - 1800000 &&
  now.value <= new Date(s.endsAt).getTime() + 1800000;
const endReached = (s) => now.value >= new Date(s.endsAt).getTime();
watch(lang, (value) => localStorage.setItem("clubdesk.language", value));
// 新课次用课程建议名额且不超过教室容量，编辑草稿不覆盖原约定。知华科技：https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
watch([() => form.courseId, () => form.roomId], () => {
  if (modal.value?.kind !== "session" || modal.value.id) return;
  const course = (data.value.courses || []).find((x) => x.id === form.courseId);
  const room = (data.value.rooms || []).find((x) => x.id === form.roomId);
  const limit = Math.min(course?.capacity || 100, room?.capacity || 100);
  if (form.capacity > limit) form.capacity = limit;
});
watch(scanMode, () => {
  stopCamera();
  scanCode.value = "";
});
watch(scanMemberId, () => {
  scanPassId.value = visitPasses.value[0]?.id || null;
});
onMounted(async () => {
  timer = setInterval(() => {
    now.value = Date.now();
    if (qr.value && new Date(qr.value.expiresAt).getTime() <= now.value) {
      qr.value = null;
      qrImage.value = "";
    }
  }, 1000);
  await run(async () => {
    try {
      await refresh();
    } catch (e) {
      if (e.message !== "UNAUTHENTICATED") throw e;
      clearPrivate();
    }
  });
});
onBeforeUnmount(() => {
  clearInterval(timer);
  stopCamera();
});
</script>
<template>
  <div v-if="!me" class="login-page">
    <header class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" width="44" height="44" /><span
        >知华科技 · ClubDesk</span
      ><button
        type="button"
        class="text-button"
        @click="lang = lang === 'zh' ? 'en' : 'zh'"
      >
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
    </header>
    <main class="login-card">
      <div class="eyebrow">ClubDesk</div>
      <h1>{{ t("会员与课程经营", "Memberships & classes") }}</h1>
      <p class="muted">
        {{
          t("门店、教练与会员登录", "Sign in for staff, coaches and members")
        }}
      </p>
      <div v-if="error" role="alert" class="feedback error">{{ error }}</div>
      <div v-if="notice" role="status" class="feedback success">
        {{ notice }}
      </div>
      <form @submit.prevent="sign">
        <label
          >{{ t("登录账号", "Username")
          }}<input
            v-model="login.username"
            name="username"
            required
            maxlength="60"
            autocomplete="username"
            autofocus /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            name="password"
            required
            maxlength="128"
            autocomplete="current-password" /></label
        ><button class="primary login-submit" :disabled="busy || formUnknown">
          {{ busy ? t("正在核对…", "Checking…") : t("登录", "Sign in") }}
        </button>
      </form>
      <button
        v-if="formUnknown"
        type="button"
        class="text-button"
        @click="checkLogin"
      >
        {{ t("核对登录状态", "Check sign-in status") }}
      </button>
      <div class="login-help">
        {{
          t(
            "账号由管理员建立。首次安装密码见本机私有配置。",
            "Accounts are created by the administrator. Find the initial password in your private installation configuration.",
          )
        }}
      </div>
    </main>
    <footer class="login-footer">
      <span>{{
        t(
          "公开源码学习版 · 未经书面授权不得商用",
          "Non-commercial source edition · Commercial use requires written authorization",
        )
      }}</span
      ><button class="text-button" type="button" @click="about = true">
        {{ t("联系知华", "Contact ZhuaTech") }}
      </button>
    </footer>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="app-brand">
        <img src="/brand/logo.jpg" alt="知华科技" width="34" height="34" />
        <div>
          <strong>ClubDesk</strong
          ><small>{{ t("知华会员课程", "ZhuaTech memberships") }}</small>
        </div>
      </div>
      <nav :aria-label="t('主菜单', 'Main navigation')">
        <button
          v-for="m in nav"
          :key="m.code"
          type="button"
          :class="{ active: route === m.code }"
          @click="navigate(m.code)"
        >
          <span class="nav-mark"></span>{{ lang === "zh" ? m.name : m.nameEn }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ t("知华科技官网", "ZhuaTech website") }} ↗</a
        ><button type="button" @click="about = true">
          {{ t("商业授权与咨询", "Licensing & contact") }}
        </button>
      </div>
    </aside>
    <button
      v-if="menuOpen"
      type="button"
      class="sidebar-backdrop"
      :aria-label="t('关闭菜单', 'Close navigation')"
      @click="menuOpen = false"
    ></button>
    <div class="main-shell">
      <header class="topbar">
        <button
          type="button"
          class="mobile-menu"
          :aria-label="t('打开菜单', 'Open navigation')"
          @click="menuOpen = !menuOpen"
        >
          ☰</button
        ><span class="business-name">{{
          data.companyName || t("会员与课程经营", "Memberships & classes")
        }}</span>
        <div class="topbar-actions">
          <button
            class="text-button"
            type="button"
            @click="lang = lang === 'zh' ? 'en' : 'zh'"
          >
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><span class="user-name">{{ me.displayName }}</span
          ><button
            class="text-button"
            type="button"
            @click="
              openForm('password');
              modal.title = t('修改密码', 'Change password');
            "
          >
            {{ t("改密", "Password") }}</button
          ><button
            class="text-button"
            type="button"
            :disabled="busy"
            @click="logout"
          >
            {{ t("退出", "Sign out") }}
          </button>
        </div>
      </header>
      <main class="content">
        <div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <div
              v-if="
                route === 'sessions' || route === 'coach' || route === 'portal'
              "
              class="muted"
            >
              {{ t("时间按所属门店时区显示", "Times use the branch timezone")
              }}<span v-if="branches.length === 1">
                · {{ branches[0].zone }}</span
              >
            </div>
          </div>
          <button
            type="button"
            class="refresh-button"
            :disabled="busy"
            :aria-label="t('刷新资料与权限', 'Refresh records and access')"
            @click="run(refresh)"
          >
            ↻ {{ t("刷新", "Refresh") }}
          </button>
        </div>
        <div v-if="error && !modal" role="alert" class="feedback error">
          {{ error }}
        </div>
        <div v-if="notice" role="status" class="feedback success">
          {{ notice
          }}<button
            type="button"
            :aria-label="t('关闭提示', 'Dismiss message')"
            @click="notice = ''"
          >
            ×
          </button>
        </div>
        <div v-if="!nav.length" class="empty-panel">
          {{
            t(
              "此账号没有可用菜单，请联系管理员核对角色及菜单权限。",
              "No menu is available. Contact the administrator to check your role and menu permissions.",
            )
          }}
        </div>
        <p v-if="exportReady">
          <a :href="exportReady.url" :download="exportReady.name">{{
            t("下载 CSV", "Download CSV")
          }}</a>
          · {{ exportReady.name }}
        </p>
        <section v-if="route === 'dashboard'">
          <div class="metrics">
            <div>
              <span>{{ t("启用会员", "Enabled members") }}</span
              ><strong>{{ dashboard.members ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t("当前有效卡", "Currently active cards") }}</span
              ><strong>{{ dashboard.activePasses ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t("待收款卡", "Unpaid cards") }}</span
              ><strong>{{ dashboard.draftPasses ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t("今天课程", "Today’s classes") }}</span
              ><strong>{{ dashboard.todaySessions?.length ?? 0 }}</strong>
            </div>
          </div>
          <div class="section-title">
            <h2>{{ t("近期课程", "Upcoming classes") }}</h2>
            <button
              v-if="can('schedule')"
              type="button"
              class="primary"
              @click="sessionForm()"
            >
              ＋ {{ t("安排课次", "Schedule class") }}
            </button>
          </div>
          <DataTable
            :lang="lang"
            :rows="
              (dashboard.upcomingSessions || []).map((x) => ({
                ...x.session,
                nameLabel: label(x.session),
                timeLabel: dateTime(x.session.startsAt, x.session.departmentId),
                coachName: x.coachName,
                roomName: x.roomName,
                capacityLabel: x.booked + ' / ' + x.session.capacity,
                displayState: sessionStateLabel(x),
              }))
            "
            :columns="sessionColumns"
            :empty="
              t(
                '尚未发布课程。先维护教练、教室与课程项目，再安排课次。',
                'No published classes. Add coaches, rooms and courses, then schedule a class.',
              )
            "
            ><template #actions="{ row }"
              ><button
                v-if="can('read')"
                type="button"
                @click="showDetail('session', row.id)"
              >
                {{ t("名单", "Roster") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'sessions'">
          <div class="toolbar">
            <label
              >{{ t("日期", "Date")
              }}<input v-model="sessionDay" type="date" /></label
            ><label
              >{{ t("状态", "State")
              }}<select v-model="sessionState">
                <option value="">{{ t("全部", "All") }}</option>
                <option
                  v-for="s in ['DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELLED']"
                  :key="s"
                  :value="s"
                >
                  {{ status(s) }}
                </option>
              </select></label
            ><label v-if="branches.length > 1"
              >{{ t("门店", "Branch")
              }}<select v-model="branchFilter">
                <option value="">{{ t("全部门店", "All branches") }}</option>
                <option v-for="b in branches" :key="b.id" :value="b.id">
                  {{ label(b) }}
                </option>
              </select></label
            ><button
              type="button"
              class="text-button"
              @click="
                sessionDay = '';
                sessionState = '';
              "
            >
              {{ t("清除筛选", "Clear filters") }}</button
            ><button
              v-if="can('schedule')"
              type="button"
              class="primary push-right"
              @click="sessionForm()"
            >
              ＋ {{ t("安排课次", "Schedule class") }}
            </button>
          </div>
          <DataTable
            :lang="lang"
            :rows="sessionRows"
            :columns="sessionColumns"
            :empty="
              t('没有符合筛选的课程。', 'No classes match these filters.')
            "
            ><template #cell="{ row, col }"
              ><span
                v-if="col.key === 'displayState'"
                class="status"
                :class="row.state.toLowerCase()"
                >{{ row.displayState }}</span
              ><span v-else>{{ row[col.key] }}</span></template
            ><template #actions="{ row }"
              ><button type="button" @click="showDetail('session', row.id)">
                {{ t("名单与详情", "Roster & details") }}</button
              ><button
                v-if="
                  can('membership') &&
                  row.state === 'PUBLISHED' &&
                  row.openSeats > 0 &&
                  new Date(row.startsAt).getTime() > now
                "
                type="button"
                @click="openBooking(row)"
              >
                {{ t("代约", "Book") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'members'">
          <div class="toolbar">
            <label v-if="branches.length > 1"
              >{{ t("门店", "Branch")
              }}<select v-model="branchFilter">
                <option value="">{{ t("全部", "All") }}</option>
                <option v-for="b in branches" :key="b.id" :value="b.id">
                  {{ label(b) }}
                </option>
              </select></label
            ><button v-if="can('master')" type="button" @click="importForm">
              {{ t("批量导入", "Import members") }}</button
            ><button
              v-if="can('report')"
              type="button"
              @click="download('members')"
            >
              {{ t("导出", "Export") }}</button
            ><button
              v-if="can('master')"
              type="button"
              class="primary push-right"
              @click="openForm('members')"
            >
              ＋ {{ t("新增会员", "New member") }}
            </button>
          </div>
          <DataTable
            :lang="lang"
            :rows="memberRows"
            :columns="memberColumns"
            :empty="
              t(
                '还没有会员。新增或导入会员后，再开卡及建立登录账号。',
                'No members yet. Add or import members, then issue a card and create their login.',
              )
            "
            ><template #actions="{ row }"
              ><button
                v-if="can('membership') && row.enabled"
                type="button"
                @click="issueCard(row)"
              >
                {{ t("开卡", "Issue card") }}</button
              ><button
                type="button"
                @click="
                  navigate('passes');
                  memberFilter = row.id;
                "
              >
                {{ t("会员卡", "Cards") }}</button
              ><button
                v-if="can('master')"
                type="button"
                @click="openForm('members', row)"
              >
                {{ t("编辑", "Edit") }}</button
              ><button
                v-if="can('admin')"
                type="button"
                @click="accountFor(row, null)"
              >
                {{ t("登录账号", "Login") }}</button
              ><button
                v-if="can('master')"
                type="button"
                class="danger-link"
                @click="askDelete('members', row)"
              >
                {{ t("删除", "Delete") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'passes'">
          <div class="toolbar">
            <label
              >{{ t("会员", "Member")
              }}<select v-model="memberFilter">
                <option value="">{{ t("全部会员", "All members") }}</option>
                <option
                  v-for="m in data.members || []"
                  :key="m.id"
                  :value="m.id"
                >
                  {{ m.name }} · {{ m.code }}
                </option>
              </select></label
            ><button
              v-if="can('report')"
              type="button"
              @click="download('passes')"
            >
              {{ t("导出", "Export") }}</button
            ><button
              v-if="can('membership')"
              type="button"
              class="primary push-right"
              @click="issueCard()"
            >
              ＋ {{ t("开立会员卡", "Issue card") }}
            </button>
          </div>
          <DataTable
            :lang="lang"
            :rows="passRows"
            :columns="passColumns"
            :empty="
              t(
                '暂无会员卡。开卡后须核对并登记足额收款，卡才生效。',
                'No cards yet. A card becomes active only after full receipt is recorded.',
              )
            "
            ><template #cell="{ row, col }"
              ><span
                v-if="col.key === 'displayState'"
                class="status"
                :class="row.effective.toLowerCase()"
                >{{ row.displayState }}</span
              ><span v-else>{{ row[col.key] }}</span></template
            ><template #actions="{ row }"
              ><button type="button" @click="showDetail('pass', row.id)">
                {{ t("详情与台账", "Details & ledger") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'catalog'">
          <div class="tabs">
            <button
              v-for="(name, key) in catalogueLabels"
              :key="key"
              type="button"
              :class="{ active: catalogTab === key }"
              @click="catalogTab = key"
            >
              {{ name }}
            </button>
          </div>
          <div class="toolbar">
            <label v-if="branches.length > 1"
              >{{ t("门店", "Branch")
              }}<select v-model="branchFilter">
                <option value="">{{ t("全部", "All") }}</option>
                <option v-for="b in branches" :key="b.id" :value="b.id">
                  {{ label(b) }}
                </option>
              </select></label
            ><button
              v-if="can('master')"
              type="button"
              class="primary push-right"
              @click="openForm(catalogTab)"
            >
              ＋ {{ t("新增", "New") }} {{ catalogueLabels[catalogTab] }}
            </button>
          </div>
          <DataTable
            :lang="lang"
            :rows="catalogueRows"
            :columns="catalogColumns"
            :empty="
              t(
                '暂无资料，先建立启用的课程、套餐、教练及教室。',
                'No records. Add enabled courses, plans, coaches and rooms.',
              )
            "
            ><template #actions="{ row }"
              ><button
                v-if="can('master')"
                type="button"
                @click="openForm(catalogTab, row)"
              >
                {{ t("编辑", "Edit") }}</button
              ><button
                v-if="can('admin') && catalogTab === 'coaches'"
                type="button"
                @click="accountFor(null, row)"
              >
                {{ t("登录账号", "Login") }}</button
              ><button
                v-if="can('master')"
                type="button"
                class="danger-link"
                @click="askDelete(catalogTab, row)"
              >
                {{ t("删除", "Delete") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'portal'">
          <div
            v-if="
              (data.member && !data.member.enabled) ||
              (branches[0] && !branches[0].enabled)
            "
            class="feedback error"
          >
            {{
              t(
                "档案或门店已停用，暂不能新预约或签到。历史记录仍可查看。",
                "Your profile or branch is disabled. New bookings and check-in are blocked; history remains visible.",
              )
            }}
          </div>
          <div class="tabs">
            <button
              v-for="tab in [
                ['available', '可约课程', 'Available classes'],
                ['bookings', '我的预约', 'My bookings'],
                ['cards', '我的会员卡', 'My cards'],
                ['qr', '签到码', 'Check-in code'],
              ]"
              :key="tab[0]"
              type="button"
              :class="{ active: portalTab === tab[0] }"
              @click="
                portalTab = tab[0];
                error = '';
              "
            >
              {{ t(tab[1], tab[2]) }}
            </button>
          </div>
          <div v-if="portalTab === 'available'">
            <p v-if="data.bookingNotice" class="muted booking-notice">
              {{ label({ name: data.bookingNotice }) }}
            </p>
            <DataTable
              :lang="lang"
              :rows="portalAvailable"
              :columns="sessionColumns"
              :empty="
                t(
                  '暂时没有可预约的已发布课程，请联系前台。',
                  'No published upcoming classes. Contact reception.',
                )
              "
              ><template #actions="{ row }"
                ><button
                  v-if="ownReservation(row.id)"
                  type="button"
                  @click="portalTab = 'bookings'"
                >
                  {{ t("查看预约", "View booking") }}</button
                ><button
                  v-else
                  type="button"
                  class="primary"
                  :disabled="
                    row.openSeats <= 0 ||
                    !data.member?.enabled ||
                    !branches[0]?.enabled
                  "
                  @click="openBooking(row)"
                >
                  {{
                    row.openSeats > 0 ? t("约课", "Book") : t("已满", "Full")
                  }}
                </button></template
              ></DataTable
            >
          </div>
          <DataTable
            v-if="portalTab === 'bookings'"
            :lang="lang"
            :rows="bookingRows"
            :columns="bookingColumns"
            :empty="
              t(
                '还没有预约。到可约课程选择课次。',
                'No bookings. Choose an available class.',
              )
            "
            ><template #cell="{ row, col }"
              ><span
                v-if="col.key === 'displayState'"
                class="status"
                :class="row.state.toLowerCase()"
                >{{ row.displayState }}</span
              ><span v-else>{{ row[col.key] }}</span></template
            ><template #actions="{ row }"
              ><button type="button" @click="showDetail('booking', row.id)">
                {{ t("记录", "History") }}</button
              ><button
                v-if="
                  row.state === 'BOOKED' &&
                  row.session &&
                  canCancel(row.session, now)
                "
                type="button"
                @click="bookingAction(row, 'cancel')"
              >
                {{ t("取消", "Cancel") }}</button
              ><span v-else-if="row.state === 'BOOKED'" class="muted">{{
                t("取消请联系前台", "Contact reception to cancel")
              }}</span></template
            ></DataTable
          >
          <DataTable
            v-if="portalTab === 'cards'"
            :lang="lang"
            :rows="passRows"
            :columns="passColumns"
            :empty="
              t(
                '还没有会员卡，请联系前台开卡。',
                'No membership cards. Contact reception.',
              )
            "
            ><template #cell="{ row, col }"
              ><span
                v-if="col.key === 'displayState'"
                class="status"
                :class="row.effective.toLowerCase()"
                >{{ row.displayState }}</span
              ><span v-else>{{ row[col.key] }}</span></template
            ><template #actions="{ row }"
              ><button type="button" @click="showDetail('pass', row.id)">
                {{ t("查看明细", "Details") }}
              </button></template
            ></DataTable
          >
          <div v-if="portalTab === 'qr'" class="qr-panel">
            <h2>{{ t("到店时出示此码", "Show this code at reception") }}</h2>
            <p class="muted">
              {{
                t(
                  "前台或教练扫描核对后登记签到。每个码两分钟有效，仅可使用一次。",
                  "Reception or your coach scans and verifies attendance. Each code lasts two minutes and is single-use.",
                )
              }}
            </p>
            <img
              v-if="qrImage"
              :src="qrImage"
              :alt="t('会员动态签到二维码', 'Dynamic member check-in QR code')"
              width="256"
              height="256"
            />
            <div v-else class="qr-placeholder">
              {{ t("生成到店签到码", "Generate a check-in code") }}
            </div>
            <p v-if="qr">
              {{
                Math.max(
                  0,
                  Math.ceil((new Date(qr.expiresAt).getTime() - now) / 1000),
                )
              }}
              {{ t("秒后失效", "seconds remaining") }}
            </p>
            <button
              type="button"
              class="primary"
              :disabled="busy || !data.member?.enabled || !branches[0]?.enabled"
              @click="mintQr"
            >
              {{
                qr
                  ? t("刷新签到码", "Refresh code")
                  : t("生成签到码", "Generate code")
              }}
            </button>
            <details v-if="qr">
              <summary>
                {{
                  t("扫码枪无法读取时", "If the scanner cannot read the code")
                }}
              </summary>
              <code class="break-code">{{ qr.token }}</code>
            </details>
            <h3>{{ t("本人到店记录", "My venue visits") }}</h3>
            <DataTable
              :lang="lang"
              :rows="
                (data.visits || []).map((v) => ({
                  ...v,
                  createdLabel: dateTime(v.createdAt, v.departmentId),
                }))
              "
              :columns="visitCols"
              :search="false"
            ></DataTable>
          </div>
        </section>
        <section v-if="route === 'coach'">
          <DataTable
            :lang="lang"
            :rows="sessionRows"
            :columns="sessionColumns"
            :empty="
              t(
                '暂无已发布的指派课程，请联系排课人员。',
                'No published assigned classes. Contact scheduling staff.',
              )
            "
            ><template #actions="{ row }"
              ><button type="button" @click="showDetail('session', row.id)">
                {{ t("学员名单与签到", "Roster & attendance") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'checkin'">
          <div class="tabs">
            <button
              type="button"
              :class="{ active: scanMode === 'class' }"
              @click="scanMode = 'class'"
            >
              {{ t("课程签到", "Class check-in") }}</button
            ><button
              type="button"
              :class="{ active: scanMode === 'venue' }"
              @click="scanMode = 'venue'"
            >
              {{ t("期限卡到店", "Venue entry") }}
            </button>
          </div>
          <div class="scan-panel">
            <label v-if="scanMode === 'class'"
              >{{ t("核对课次", "Choose class")
              }}<select v-model="scanSessionId">
                <option :value="null">
                  {{ t("请选择已发布课次", "Select a published class") }}
                </option>
                <option v-for="s in scanSessions" :key="s.id" :value="s.id">
                  {{ s.timeLabel }} · {{ s.nameLabel }} · {{ s.coachName }}
                </option>
              </select></label
            >
            <form class="scan-line" @submit.prevent="submitScan">
              <label
                >{{ t("扫描会员动态码", "Scan member code")
                }}<input
                  v-model="scanCode"
                  :placeholder="
                    t(
                      '扫码枪输入，或粘贴会员动态码',
                      'Scan or paste the member code',
                    )
                  "
                  maxlength="80"
                  autocomplete="off"
                  required /></label
              ><button
                class="primary"
                :disabled="busy || (scanMode === 'class' && !scanSessionId)"
              >
                {{ t("核对并签到", "Verify & check in") }}
              </button>
            </form>
            <div class="inline-actions">
              <button
                type="button"
                :disabled="scanMode === 'class' && !scanSessionId"
                @click="startCamera"
              >
                {{ t("摄像头扫码", "Scan with camera") }}</button
              ><button
                type="button"
                :disabled="scanMode === 'class' && !scanSessionId"
                @click="qrFile.click()"
              >
                {{ t("从二维码图片识别", "Read QR image") }}</button
              ><input
                ref="qrFile"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                hidden
                @change="readQrFile"
              /><button v-if="cameraOn" type="button" @click="stopCamera">
                {{ t("停止摄像头", "Stop camera") }}</button
              ><button
                v-if="scanMode === 'class' && scanSessionId"
                type="button"
                @click="showDetail('session', scanSessionId)"
              >
                {{ t("打开名单手工签到", "Open roster for manual check-in") }}
              </button>
            </div>
            <video
              v-if="cameraOn"
              ref="video"
              class="camera-preview"
              autoplay
              playsinline
              muted
            ></video>
          </div>
          <div v-if="scanMode === 'venue'" class="manual-entry">
            <h2>{{ t("手工核验到店", "Manual venue verification") }}</h2>
            <form class="toolbar" @submit.prevent="visit">
              <label
                >{{ t("会员", "Member")
                }}<select v-model="scanMemberId" required>
                  <option :value="null">
                    {{ t("请选择会员", "Select member") }}
                  </option>
                  <option
                    v-for="m in data.members || []"
                    :key="m.id"
                    :value="m.id"
                  >
                    {{ m.name }} · {{ m.code }}
                  </option>
                </select></label
              ><label
                >{{ t("当前有效期限卡", "Valid period membership")
                }}<select v-model="scanPassId" required>
                  <option :value="null">
                    {{ t("请选择有效卡", "Select a valid card") }}
                  </option>
                  <option v-for="p in visitPasses" :key="p.id" :value="p.id">
                    {{ p.reference }} · {{ p.endsOn }}
                  </option>
                </select></label
              ><button class="primary" :disabled="busy || !scanPassId">
                {{
                  t(
                    "已核对身份与到店，登记",
                    "Identity & arrival verified — record",
                  )
                }}
              </button>
            </form>
          </div>
        </section>
        <section v-if="route === 'reports'">
          <form class="toolbar" @submit.prevent="run(refresh)">
            <label
              >{{ t("开始日期", "From")
              }}<input v-model="period.from" type="date" required /></label
            ><label
              >{{ t("结束日期", "To")
              }}<input v-model="period.to" type="date" required /></label
            ><button class="primary" :disabled="busy">
              {{ t("查询", "Apply") }}
            </button>
          </form>
          <div v-if="report" class="metrics">
            <div>
              <span>{{ t("净收款登记", "Net receipt records") }}</span
              ><strong>{{
                cashLabel(report.total.netReceived, report.currency)
              }}</strong>
            </div>
            <div>
              <span>{{
                t("退款登记（含冲正）", "Refunds net of reversals")
              }}</span
              ><strong>{{
                cashLabel(report.total.refunds, report.currency)
              }}</strong>
            </div>
            <div>
              <span>{{ t("实际到课", "Attended") }}</span
              ><strong>{{ report.total.attended }}</strong>
            </div>
            <div>
              <span>{{ t("未到核销", "No-show") }}</span
              ><strong>{{ report.total.noShows }}</strong>
            </div>
          </div>
          <DataTable
            v-if="report"
            :lang="lang"
            :rows="
              report.branches.map((x) => ({
                ...x,
                netLabel: cashLabel(x.netReceived, report.currency),
                receiptLabel: cashLabel(x.receipts, report.currency),
                refundLabel: cashLabel(x.refunds, report.currency),
              }))
            "
            :columns="
              columns([
                ['name', '门店', 'Branch'],
                ['receiptLabel', '收款（含冲正）', 'Receipts net of reversals'],
                ['refundLabel', '退款（含冲正）', 'Refunds net of reversals'],
                ['netLabel', '净收款', 'Net receipts'],
                ['cardsIssued', '开卡数', 'Cards issued'],
                ['attended', '到课', 'Attended'],
                ['noShows', '未到', 'No-show'],
                ['visits', '入馆天数', 'Venue visit days'],
              ])
            "
          ></DataTable>
          <div class="inline-actions exports">
            <button
              v-for="item in [
                ['members', '会员', 'Members'],
                ['passes', '会员卡', 'Cards'],
                ['attendance', '出勤', 'Attendance'],
                ['cash', '收退款', 'Cash records'],
              ]"
              :key="item[0]"
              type="button"
              @click="download(item[0])"
            >
              {{ t("导出", "Export") }} {{ t(item[1], item[2]) }} CSV
            </button>
          </div>
        </section>
        <section v-if="route === 'admin'">
          <div class="tabs wrap">
            <button
              v-for="(name, key) in adminLabels"
              :key="key"
              type="button"
              :class="{ active: adminTab === key }"
              @click="
                adminTab = key;
                error = '';
              "
            >
              {{ name }}
            </button>
          </div>
          <div class="toolbar">
            <button
              v-if="
                ['users', 'roles', 'departments', 'dictionaries'].includes(
                  adminTab,
                )
              "
              type="button"
              class="primary push-right"
              @click="openForm(adminTab)"
            >
              ＋ {{ t("新增", "New") }} {{ adminLabels[adminTab] }}
            </button>
          </div>
          <DataTable :lang="lang" :rows="adminRows" :columns="adminColumns"
            ><template #cell="{ row, col }"
              ><span v-if="adminTab === 'settings' && col.key === 'code'">{{
                settingLabel(row.code)
              }}</span
              ><span v-else-if="col.key === 'permissionCode'">{{
                permissionsName(row.permissionCode)
              }}</span
              ><span v-else>{{ row[col.key] }}</span></template
            ><template #actions="{ row }"
              ><button type="button" @click="openForm(adminTab, row)">
                {{ t("编辑", "Edit") }}</button
              ><button
                v-if="
                  ['users', 'roles', 'departments', 'dictionaries'].includes(
                    adminTab,
                  ) && !(adminTab === 'departments' && row.id === 1)
                "
                type="button"
                class="danger-link"
                @click="askDelete(adminTab, row, true)"
              >
                {{ t("删除", "Delete") }}
              </button></template
            ></DataTable
          >
        </section>
        <section v-if="route === 'audit'">
          <DataTable
            :lang="lang"
            :rows="
              audit.map((x) => ({
                ...x,
                timeLabel: dateTime(x.createdAt, x.departmentId),
                actionLabel: status(x.action),
              }))
            "
            :columns="
              columns([
                ['timeLabel', '时间', 'Time'],
                ['actor', '账号', 'Account'],
                ['actionLabel', '操作', 'Action'],
                ['objectId', '记录', 'Record'],
              ])
            "
          ></DataTable>
        </section>
      </main>
    </div>
  </div>
  <!-- 详情和确认只解释影响当前决策的业务规则；完整手册在仓库。知华科技 https://www.zhuatech.cn/ 微信 zhuatech / zhuatech2。 -->
  <div
    v-if="detail && detailData"
    class="drawer-backdrop"
    @click.self="
      detail = null;
      detailData = null;
    "
  >
    <section
      class="drawer"
      role="dialog"
      aria-modal="true"
      :aria-label="
        detail.kind === 'pass'
          ? t('会员卡详情', 'Membership details')
          : t('课程与预约详情', 'Class and booking details')
      "
    >
      <div class="drawer-header">
        <h2>
          {{
            detail.kind === "pass"
              ? t("会员卡详情", "Membership details")
              : detail.kind === "session"
                ? t("课程名单", "Class roster")
                : t("预约记录", "Booking history")
          }}
        </h2>
        <button
          type="button"
          :aria-label="t('关闭详情', 'Close details')"
          @click="
            detail = null;
            detailData = null;
          "
        >
          ×
        </button>
      </div>
      <template v-if="selectedPass"
        ><h3>
          {{ lang === "zh" ? selectedPass.planName : selectedPass.planNameEn }}
        </h3>
        <p class="reference">
          {{ selectedPass.reference }} · {{ detailData.summary.memberName }}
        </p>
        <span
          class="status"
          :class="detailData.summary.effective.toLowerCase()"
          >{{
            detailData.summary.effective === "DRAFT"
              ? t("待收款", "Awaiting payment")
              : status(detailData.summary.effective)
          }}</span
        >
        <dl class="details-grid">
          <div>
            <dt>{{ t("有效期", "Validity") }}</dt>
            <dd>{{ selectedPass.startsOn }} — {{ selectedPass.endsOn }}</dd>
          </div>
          <div>
            <dt>
              {{ t("未预留／预留／已核销", "Unheld / reserved / used") }}
            </dt>
            <dd>
              {{
                detailData.summary.available === null
                  ? t("不限", "Unlimited")
                  : detailData.summary.available
              }}
              / {{ selectedPass.held }} / {{ selectedPass.used }}
            </dd>
          </div>
          <div>
            <dt>{{ t("约定价格", "Agreed price") }}</dt>
            <dd>{{ cashLabel(selectedPass.price, selectedPass.currency) }}</dd>
          </div>
          <div>
            <dt>{{ t("净收款登记", "Net receipts") }}</dt>
            <dd>
              {{ cashLabel(detailData.summary.netPaid, selectedPass.currency) }}
            </dd>
          </div>
          <div v-if="selectedPass.state === 'DRAFT'">
            <dt>{{ t("待收金额", "Outstanding") }}</dt>
            <dd>
              {{ cashLabel(detailData.summary.due, selectedPass.currency) }}
            </dd>
          </div>
          <div v-if="selectedPass.freezeUntil">
            <dt>{{ t("冻结至（不含）", "Frozen until (exclusive)") }}</dt>
            <dd>{{ selectedPass.freezeUntil }}</dd>
          </div>
        </dl>
        <div class="inline-actions">
          <button
            v-if="can('finance') && selectedPass.state !== 'ACTIVE'"
            type="button"
            class="primary"
            @click="openCash"
          >
            {{ t("资金登记", "Record cash") }}</button
          ><button
            v-if="
              can('membership') && detailData.summary.effective === 'ACTIVE'
            "
            type="button"
            @click="passAction('freeze')"
          >
            {{ t("冻结", "Freeze") }}</button
          ><button
            v-if="
              can('membership') && detailData.summary.effective === 'FROZEN'
            "
            type="button"
            @click="passAction('unfreeze')"
          >
            {{ t("提前解冻", "End freeze") }}</button
          ><button
            v-if="can('membership') && selectedPass.state !== 'CLOSED'"
            type="button"
            class="danger-link"
            @click="passAction('close')"
          >
            {{ t("关卡", "Close card") }}
          </button>
        </div>
        <h3>{{ t("资金流水", "Cash history") }}</h3>
        <div class="compact-table table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ t("凭据", "Reference") }}</th>
                <th>{{ t("类型", "Type") }}</th>
                <th>{{ t("金额", "Amount") }}</th>
                <th>{{ t("剩余可退", "Refundable") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="x in detailData.cash" :key="x.entry.id">
                <td>
                  {{ x.entry.reference
                  }}<small>{{
                    dateTime(x.entry.createdAt, x.entry.departmentId)
                  }}</small>
                </td>
                <td>
                  {{ status(x.entry.kind)
                  }}<small v-if="x.reversed">{{
                    t("已冲正", "Reversed")
                  }}</small>
                </td>
                <td>{{ cashLabel(x.entry.amount, selectedPass.currency) }}</td>
                <td>
                  {{
                    x.entry.kind === "RECEIPT"
                      ? cashLabel(x.refundable, selectedPass.currency)
                      : "—"
                  }}
                </td>
              </tr>
              <tr v-if="!detailData.cash.length">
                <td colspan="4" class="empty-cell">
                  {{ t("暂无收退款登记", "No cash records") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <h3>{{ t("课时流水", "Credit ledger") }}</h3>
        <ul class="ledger-list">
          <li v-for="x in detailData.credits" :key="x.id">
            <div>
              <strong>{{ status(x.kind) }}</strong
              ><small
                >{{ dateTime(x.createdAt, x.departmentId) }} ·
                {{ x.actor }}</small
              >
            </div>
            <span
              >{{
                x.totalDelta
                  ? `${t("授权", "Granted")} ${x.totalDelta} · `
                  : ""
              }}{{ t("预留", "Held") }} {{ x.heldDelta > 0 ? "+" : ""
              }}{{ x.heldDelta }} · {{ t("核销", "Used") }}
              {{ x.usedDelta > 0 ? "+" : "" }}{{ x.usedDelta }}</span
            >
          </li>
        </ul>
      </template>
      <template v-if="selectedSession"
        ><h3>{{ label(selectedSession) }}</h3>
        <p>
          {{ dateTime(selectedSession.startsAt, selectedSession.departmentId) }}
          — {{ dateTime(selectedSession.endsAt, selectedSession.departmentId) }}
        </p>
        <p class="muted">
          {{ detailData.summary.coachName }} ·
          {{ detailData.summary.roomName }} · {{ detailData.summary.booked }} /
          {{ selectedSession.capacity }}
        </p>
        <span class="status" :class="selectedSession.state.toLowerCase()">{{
          sessionStateLabel(detailData.summary)
        }}</span>
        <div class="inline-actions">
          <button
            v-if="can('schedule') && selectedSession.state === 'DRAFT'"
            type="button"
            @click="sessionForm(selectedSession)"
          >
            {{ t("编辑", "Edit") }}</button
          ><button
            v-if="can('schedule') && selectedSession.state === 'DRAFT'"
            type="button"
            class="primary"
            @click="sessionAction('publish')"
          >
            {{ t("发布", "Publish") }}</button
          ><button
            v-if="
              can('schedule') &&
              selectedSession.state === 'PUBLISHED' &&
              new Date(selectedSession.startsAt).getTime() > now
            "
            type="button"
            @click="sessionAction('withdraw')"
          >
            {{ t("撤回", "Withdraw") }}</button
          ><button
            v-if="
              can('schedule') &&
              ['DRAFT', 'PUBLISHED'].includes(selectedSession.state)
            "
            type="button"
            class="danger-link"
            @click="sessionAction('cancel')"
          >
            {{ t("取消课程", "Cancel class") }}</button
          ><button
            v-if="
              (can('schedule') || can('coach')) &&
              selectedSession.state === 'PUBLISHED' &&
              endReached(selectedSession)
            "
            type="button"
            @click="sessionAction('finish')"
          >
            {{ t("结束并核对未到", "Complete & reconcile no-shows") }}
          </button>
        </div>
        <DataTable
          :lang="lang"
          :rows="
            detailData.roster.map((x) => ({
              ...x.booking,
              memberName: x.memberName,
              memberCode: x.memberCode,
              displayState: status(x.booking.state),
            }))
          "
          :columns="
            columns([
              ['memberName', '会员', 'Member'],
              ['memberCode', '编号', 'Code'],
              ['displayState', '状态', 'State'],
            ])
          "
          :search="false"
          :empty="t('暂无预约会员', 'No bookings')"
          ><template #actions="{ row }"
            ><button
              v-if="
                (can('checkin') || can('coach')) &&
                row.state === 'BOOKED' &&
                atWindow(selectedSession)
              "
              type="button"
              @click="bookingAction(row, 'attend')"
            >
              {{ t("签到", "Attend") }}</button
            ><button
              v-if="
                (can('checkin') || can('coach')) &&
                row.state === 'BOOKED' &&
                endReached(selectedSession)
              "
              type="button"
              @click="bookingAction(row, 'no-show')"
            >
              {{ t("未到", "No-show") }}</button
            ><button
              v-if="can('membership') && row.state === 'BOOKED'"
              type="button"
              @click="bookingAction(row, 'cancel')"
            >
              {{ t("取消", "Cancel") }}</button
            ><button
              v-if="
                can('membership') && ['ATTENDED', 'NO_SHOW'].includes(row.state)
              "
              type="button"
              @click="bookingAction(row, 'correct')"
            >
              {{ t("纠错", "Correct") }}</button
            ><button type="button" @click="showDetail('booking', row.id)">
              {{ t("记录", "History") }}
            </button></template
          ></DataTable
        >
      </template>
      <template v-if="detail.kind === 'booking'"
        ><h3>{{ label(detailData.session.session) }}</h3>
        <p>
          {{
            dateTime(
              detailData.session.session.startsAt,
              detailData.booking.departmentId,
            )
          }}
        </p>
        <span class="status" :class="detailData.booking.state.toLowerCase()">{{
          status(detailData.booking.state)
        }}</span>
        <p>
          {{ t("会员取消截止时间", "Member cancellation deadline") }} ·
          {{
            dateTime(
              detailData.session.cancelDeadline,
              detailData.booking.departmentId,
            )
          }}
        </p>
        <p>{{ detailData.booking.note }}</p></template
      >
      <h3>{{ t("操作历史", "History") }}</h3>
      <ul class="timeline">
        <li v-for="e in detailData.events" :key="e.id">
          <span>{{ status(e.action) }}</span
          ><small
            >{{ dateTime(e.createdAt, e.departmentId) }} · {{ e.actor }}</small
          >
          <p>{{ e.note }}</p>
        </li>
        <li v-if="!detailData.events.length" class="muted">
          {{ t("暂无记录", "No records") }}
        </li>
      </ul>
    </section>
  </div>
  <div v-if="modal" class="overlay" @click.self="!busy && closeForm()">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="modalTitle"
    >
      <div class="dialog-header">
        <h2>{{ modalTitle }}</h2>
        <button
          type="button"
          :disabled="busy"
          :aria-label="t('关闭表单', 'Close form')"
          @click="closeForm"
        >
          ×
        </button>
      </div>
      <div v-if="error" role="alert" class="feedback error">{{ error }}</div>
      <p v-if="modal.warning" class="callout">{{ modal.warning }}</p>
      <div v-if="modal.deletePath" class="dialog-actions">
        <button type="button" :disabled="busy" @click="closeForm">
          {{ t("取消", "Cancel") }}</button
        ><button
          type="button"
          class="danger"
          :disabled="busy || formUnknown"
          @click="remove"
        >
          {{ t("确认删除", "Delete record") }}
        </button>
      </div>
      <form v-else @submit.prevent="saveForm">
        <template v-if="modal.kind === 'import'"
          ><div class="inline-actions">
            <button
              type="button"
              @click="
                downloadText(
                  'code,name,departmentId,contactNote,enabled\r\n',
                  'clubdesk-member-template.csv',
                )
              "
            >
              {{ t("下载空模板", "Download blank template") }}</button
            ><button type="button" @click="csvFile.click()">
              {{ t("选择CSV文件", "Choose CSV file") }}</button
            ><input
              ref="csvFile"
              type="file"
              accept=".csv,text/csv"
              hidden
              @change="importFile"
            />
          </div>
          <p class="muted">
            {{ t("可用门店编号", "Available branch IDs") }}:
            {{ branches.map((b) => b.id + " = " + label(b)).join("；") }}
          </p>
          <label
            >{{ t("CSV内容", "CSV content")
            }}<textarea
              v-model="csvText"
              rows="6"
              maxlength="1048576"
              @input="
                csvRows = [];
                csvError = '';
              "
            /></label
          ><button type="button" @click="previewCsv">
            {{ t("校验并预览", "Validate & preview") }}
          </button>
          <div v-if="csvError" role="alert" class="feedback error">
            {{ csvError }}
          </div>
          <DataTable
            v-if="csvRows.length"
            :lang="lang"
            :rows="csvRows.map((x, i) => ({ ...x, id: i + 1 }))"
            :columns="
              columns([
                ['code', '会员编号', 'Code'],
                ['name', '姓名', 'Name'],
                ['departmentId', '门店编号', 'Branch ID'],
              ])
            "
            :search="false"
          ></DataTable
        ></template>
        <template v-else
          ><p v-if="modal.kind === 'booking' && currentSession" class="callout">
            {{ currentSession.nameLabel }} · {{ currentSession.timeLabel
            }}<br />{{
              t(
                "预约先预留一课时，实际签到或未到核销时才使用。",
                "Booking holds one credit; attendance or confirmed no-show consumes it.",
              )
            }}
          </p>
          <p v-if="modal.kind === 'session'" class="muted">
            {{ t("输入时间属于门店时区", "Enter time in the branch timezone") }}
            · {{ zoneFor(form.departmentId) }}
          </p>
          <p v-if="modal.kind === 'cash'" class="callout">
            {{
              t(
                "仅登记已核实的实际外部收退款。此操作不会发起转账。",
                "Record verified external receipts or refunds. This action does not transfer money.",
              )
            }}
          </p>
          <div class="form-grid">
            <template v-for="f in fields" :key="f.key"
              ><label
                v-if="f.type !== 'hidden' && f.type !== 'permissions'"
                :class="{
                  'full-width': f.type === 'textarea',
                  'checkbox-label': f.type === 'checkbox',
                }"
                ><span
                  >{{ f.label
                  }}<span
                    v-if="f.required && !f.readonly"
                    class="required-mark"
                  >
                    *</span
                  ></span
                ><select
                  v-if="f.type === 'select'"
                  v-model="form[f.key]"
                  :required="f.required"
                  :disabled="f.readonly"
                  @change="formChange(f.key)"
                >
                  <option :value="null">{{ t("请选择", "Select") }}</option>
                  <option
                    v-for="o in f.options || []"
                    :key="o.value"
                    :value="o.value"
                  >
                    {{ o.label }}
                  </option></select
                ><textarea
                  v-else-if="f.type === 'textarea'"
                  v-model="form[f.key]"
                  :required="f.required"
                  :maxlength="f.max"
                  rows="3"
                ></textarea
                ><input
                  v-else-if="f.type === 'checkbox'"
                  v-model="form[f.key]"
                  type="checkbox" /><input
                  v-else-if="f.type === 'money'"
                  v-model="form[f.key]"
                  type="text"
                  inputmode="decimal"
                  pattern="[0-9]+(\.[0-9]{1,2})?"
                  :required="f.required"
                  :readonly="f.readonly"
                  maxlength="12" /><input
                  v-else-if="f.type === 'number'"
                  v-model.number="form[f.key]"
                  type="number"
                  step="1"
                  :min="f.min"
                  :max="f.max"
                  :required="f.required"
                  :readonly="f.readonly"
                  @change="formChange(f.key)" /><input
                  v-else
                  v-model="form[f.key]"
                  :type="f.type"
                  :required="f.required"
                  :readonly="f.readonly"
                  :maxlength="f.max"
                  :minlength="f.minLength"
                  :pattern="f.pattern"
                  :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
                  @change="formChange(f.key)"
              /></label>
              <fieldset
                v-else-if="f.type === 'permissions'"
                class="full-width permission-options"
              >
                <legend>{{ f.label }}</legend>
                <label v-for="o in f.options" :key="o.value"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="o.value"
                  />{{ o.label }}</label
                >
              </fieldset></template
            >
          </div>
          <p
            v-if="modal.kind === 'booking' && !form.passOptions?.length"
            class="feedback error"
          >
            {{
              t(
                "这位会员没有覆盖课程的可用卡。请先核对收款、有效期、冻结期和剩余课时。",
                "No usable card covers this class. Check full payment, validity, freeze and remaining credits.",
              )
            }}
          </p>
          <p v-if="modal.kind === 'issue'" class="muted">
            {{
              t(
                "续开会建立独立新卡。请选择开始日期，原卡和历史不会覆盖。",
                "Renewal creates a separate card. Choose the start date; existing cards and history remain.",
              )
            }}
          </p></template
        >
        <div class="dialog-actions">
          <button type="button" :disabled="busy" @click="closeForm">
            {{ t("取消", "Cancel") }}</button
          ><button
            class="primary"
            :disabled="
              busy ||
              formUnknown ||
              (modal.kind === 'import' &&
                (!csvRows.length || Boolean(csvError))) ||
              (modal.kind === 'booking' && !form.passId) ||
              (modal.kind === 'cash' &&
                form.kind !== 'RECEIPT' &&
                !form.originalId)
            "
          >
            {{
              busy
                ? t("正在保存…", "Saving…")
                : modal.kind === "import"
                  ? t("确认整批新增", "Import this batch")
                  : t("确认保存", "Save")
            }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="about" class="overlay" @click.self="about = false">
    <section
      class="dialog about-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('联系知华科技', 'Contact ZhuaTech')"
    >
      <div class="dialog-header">
        <h2>{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          type="button"
          :aria-label="t('关闭联系方式', 'Close contact details')"
          @click="about = false"
        >
          ×
        </button>
      </div>
      <img
        src="/brand/logo.jpg"
        alt="知华科技正式LOGO"
        width="52"
        height="52"
      />
      <h3>ClubDesk · 1.0.0</h3>
      <p>上海如静知华信息科技有限公司</p>
      <p>
        {{
          t(
            "公开源码学习版／非商业源码版。未经书面授权不得商用。",
            "Non-commercial source edition. Commercial use requires written authorization.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/</a
      >
      <p>
        {{
          t(
            "商业授权、私有部署、品牌定制和系统集成咨询",
            "Commercial licensing, private deployment, branding and integration",
          )
        }}
      </p>
      <div class="contact-qrs">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="知华科技微信 zhuatech" />
          <figcaption>zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="知华科技微信 zhuatech2" />
          <figcaption>zhuatech2</figcaption>
        </figure>
      </div>
      <p class="third-party-links">
        <a href="/third-party/vue.txt" target="_blank" rel="noopener">Vue</a> ·
        <a href="/third-party/lucide.txt" target="_blank" rel="noopener"
          >Lucide</a
        >
        ·
        <a href="/third-party/qrcode.txt" target="_blank" rel="noopener"
          >QRCode</a
        >
        ·
        <a href="/third-party/zxing-browser.txt" target="_blank" rel="noopener"
          >ZXing Browser</a
        >
        ·
        <a href="/third-party/zxing-library.txt" target="_blank" rel="noopener"
          >ZXing Library</a
        >
      </p>
    </section>
  </div>
</template>
