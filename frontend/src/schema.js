// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 真实业务表单字段与固定管理入口，选择项来自当前授权目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function fieldsFor(kind, { t, data, admin, form, editing, label }) {
  const f = (key, zh, en, type = "text", extra = {}) => ({
    key,
    label: t(zh, en),
    type,
    required: true,
    ...extra,
  });
  const opt = (rows) => rows.map((x) => ({ value: x.id, label: label(x) }));
  const branch = f("departmentId", "门店", "Branch", "select", {
    options: opt(data.branches || admin.departments || []),
    readonly: editing,
  });
  const enabled = f("enabled", "启用", "Enabled", "checkbox", {
    required: false,
  });
  const name = f("name", "名称", "Name", "text", { max: 160 });
  const english = f("nameEn", "英文名称", "English name", "text", { max: 160 });
  const revision = { key: "revision", type: "hidden" };
  const same = (rows) =>
    (rows || []).filter(
      (x) => x.enabled && x.departmentId === form.departmentId,
    );
  const notes = f("note", "说明", "Note", "textarea", {
    max: 1000,
    required: false,
  });
  const reason = f("note", "核对依据或原因", "Evidence or reason", "textarea", {
    max: 1000,
  });
  switch (kind) {
    case "members":
      return [
        branch,
        f("code", "会员编号", "Member code", "text", {
          max: 60,
          pattern: "[A-Za-z0-9_.-]{2,60}",
          readonly: editing,
        }),
        name,
        f("contactNote", "联系备注", "Contact note", "text", {
          max: 300,
          required: false,
        }),
        enabled,
        revision,
      ];
    case "coaches":
      return [
        branch,
        f("code", "教练编号", "Coach code", "text", {
          max: 60,
          pattern: "[A-Za-z0-9_.-]{2,60}",
          readonly: editing,
        }),
        name,
        f("specialty", "专长", "Specialty", "text", {
          max: 300,
          required: false,
        }),
        enabled,
        revision,
      ];
    case "rooms":
      return [
        branch,
        name,
        f("capacity", "教室容量", "Room capacity", "number", {
          min: 1,
          max: 100,
        }),
        enabled,
        revision,
      ];
    case "courses":
      return [
        branch,
        name,
        english,
        f("categoryId", "课程分类", "Course category", "select", {
          options: opt((data.categories || []).filter((x) => x.enabled)),
        }),
        f("durationMinutes", "时长（分钟）", "Duration (minutes)", "number", {
          min: 5,
          max: 480,
        }),
        f("capacity", "默认名额", "Default seats", "number", {
          min: 1,
          max: 100,
        }),
        enabled,
        revision,
      ];
    case "plans":
      return [
        branch,
        name,
        english,
        f("mode", "套餐类型", "Plan type", "select", {
          options: [
            { value: "CREDITS", label: t("次卡", "Class pack") },
            { value: "PERIOD", label: t("期限卡", "Period membership") },
          ],
        }),
        ...(form.mode === "CREDITS"
          ? [
              f("credits", "课时次数", "Class credits", "number", {
                min: 1,
                max: 9999,
              }),
            ]
          : []),
        f("validDays", "有效天数", "Valid days", "number", {
          min: 1,
          max: 730,
        }),
        f("price", "套餐价格", "Plan price", "money"),
        enabled,
        revision,
      ];
    case "issue":
      return [
        f("memberId", "会员", "Member", "select", {
          options: opt((data.members || []).filter((x) => x.enabled)),
          readonly: Boolean(form.fixedMember),
        }),
        f("planId", "套餐", "Plan", "select", {
          options: opt(
            (data.plans || []).filter(
              (x) =>
                x.enabled &&
                x.departmentId ===
                  (data.members || []).find((m) => m.id === form.memberId)
                    ?.departmentId,
            ),
          ),
        }),
        f("startsOn", "开始日期", "Start date", "date"),
        notes,
      ];
    case "session":
      return [
        branch,
        f("courseId", "课程项目", "Course", "select", {
          options: opt(same(data.courses)),
        }),
        f("coachId", "教练", "Coach", "select", {
          options: opt(same(data.coaches)),
        }),
        f("roomId", "教室", "Room", "select", {
          options: opt(same(data.rooms)),
        }),
        f(
          "localStart",
          "门店当地开课时间",
          "Branch local start time",
          "datetime-local",
        ),
        f("capacity", "名额", "Seats", "number", { min: 1, max: 100 }),
        f(
          "cancelHours",
          "提前取消时限（小时）",
          "Cancellation notice (hours)",
          "number",
          { min: 0, max: 168 },
        ),
        notes,
        revision,
      ];
    case "booking":
      return [
        f("memberId", "会员", "Member", "select", {
          options: opt((data.members || []).filter((x) => x.enabled)),
          readonly: Boolean(form.fixedMember),
        }),
        f("passId", "使用会员卡", "Membership card", "select", {
          options: form.passOptions || [],
        }),
      ];
    case "freeze":
      return [
        f("days", "冻结天数", "Freeze days", "number", { min: 1, max: 90 }),
        reason,
      ];
    case "action":
      return [reason];
    case "cash":
      return [
        f("kind", "登记类型", "Record type", "select", {
          options: form.cashOptions || [],
        }),
        ...(form.kind !== "RECEIPT"
          ? [
              f("originalId", "原资金记录", "Original record", "select", {
                options: form.originalOptions || [],
              }),
            ]
          : []),
        f(
          "reference",
          "实际外部凭据编号",
          "Actual external reference",
          "text",
          { max: 160 },
        ),
        f("amount", "实际金额", "Actual amount", "money", {
          readonly: form.kind === "REVERSAL",
        }),
        reason,
      ];
    case "password":
      return [
        f("oldPassword", "原密码", "Old password", "password", { max: 128 }),
        f("newPassword", "新密码", "New password", "password", {
          max: 72,
          minLength: 12,
        }),
      ];
    case "users":
      return [
        f("username", "登录账号", "Username", "text", {
          max: 60,
          pattern: "[A-Za-z0-9_.-]{3,60}",
        }),
        f("displayName", "显示名称", "Display name", "text", { max: 120 }),
        f(
          "password",
          editing ? "新密码（留空保留）" : "初始密码",
          editing ? "New password (blank to keep)" : "Initial password",
          "password",
          { required: !editing, max: 72, minLength: 12 },
        ),
        f("departmentId", "所属门店", "Branch", "select", {
          options: opt(admin.departments || []),
        }),
        f("roleId", "角色", "Role", "select", {
          options: opt(admin.roles || []),
        }),
        f(
          "memberId",
          "会员绑定（可选）",
          "Member binding (optional)",
          "select",
          {
            required: false,
            options: opt(data.members || []),
            readonly: editing,
          },
        ),
        f("coachId", "教练绑定（可选）", "Coach binding (optional)", "select", {
          required: false,
          options: opt(data.coaches || []),
          readonly: editing,
        }),
        enabled,
      ];
    case "roles":
      return [
        f("name", "角色名称", "Role name", "text", { max: 120 }),
        f("scope", "数据范围", "Data scope", "select", {
          options: ["ALL", "DEPARTMENT", "TRAINER", "MEMBER"].map((value) => ({
            value,
            label: {
              ALL: t("全部门店", "All branches"),
              DEPARTMENT: t("本人门店", "Own branch"),
              TRAINER: t("本人指派课程", "Assigned classes"),
              MEMBER: t("本人会员", "Own member"),
            }[value],
          })),
        }),
        f("permissions", "接口权限", "API permissions", "permissions", {
          options: (admin.permissions || []).map((x) => ({
            value: x.code,
            label: label(x),
          })),
        }),
      ];
    case "permissions":
      return [
        f("code", "接口权限代码", "Permission code", "text", {
          readonly: true,
        }),
        f("name", "名称", "Name", "text", { max: 120 }),
      ];
    case "menus":
      return [
        f("code", "菜单代码", "Menu code", "text", { readonly: true }),
        f("name", "中文名称", "Chinese name", "text", { max: 120 }),
        f("nameEn", "英文名称", "English name", "text", { max: 120 }),
        f("permissionCode", "可见权限", "Visibility permission", "select", {
          readonly: true,
          options: (admin.permissions || []).map((x) => ({
            value: x.code,
            label: label(x),
          })),
        }),
        f("position", "排列顺序", "Sort order", "number", {
          min: 0,
          max: 1000,
        }),
        enabled,
      ];
    case "departments":
      return [
        f("name", "门店名称", "Branch name", "text", { max: 120 }),
        f("zone", "IANA时区", "IANA timezone", "text", { max: 80 }),
        enabled,
      ];
    case "dictionaries":
      return [
        f("type", "分类类型", "Dictionary type", "text", { readonly: true }),
        f("code", "分类代码", "Category code", "text", {
          readonly: editing,
          max: 60,
        }),
        f("name", "中文名称", "Chinese name", "text", { max: 120 }),
        f("nameEn", "英文名称", "English name", "text", { max: 120 }),
        enabled,
      ];
    case "settings":
      return [
        f("code", "参数代码", "Setting code", "text", { readonly: true }),
        f("value", "参数值", "Value", "textarea", {
          max:
            form.code === "currency"
              ? 3
              : form.code === "companyName"
                ? 160
                : 1000,
        }),
      ];
    default:
      return [];
  }
}
