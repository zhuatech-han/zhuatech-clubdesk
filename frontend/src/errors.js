// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const errors = {
  MENU_PERMISSION_IMMUTABLE: [
    "菜单与接口权限的映射由系统固定，可编辑名称、顺序与启停。",
    "Menu-to-API permissions are fixed. Edit names, order or enabled state.",
  ],
  UNAUTHENTICATED: [
    "登录已失效，请重新登录。",
    "Your session expired. Please sign in again.",
  ],
  FORBIDDEN: [
    "当前账号没有此操作权限。",
    "This account does not have permission for this action.",
  ],
  OUT_OF_SCOPE: [
    "这份资料不属于当前账号的授权范围。",
    "This record is outside your permitted scope.",
  ],
  NOT_FOUND: [
    "资料已不存在，请刷新核对。",
    "The record no longer exists. Refresh to check.",
  ],
  INVALID_INPUT: [
    "请核对必填项、长度和整数范围。",
    "Check required fields, lengths and integer limits.",
  ],
  CONFLICT: [
    "编号重复或资料已被引用，不能保存或删除。",
    "A code is duplicated or the record is referenced.",
  ],
  VERSION_CONFLICT: [
    "记录已被更新，请关闭窗口、刷新核对后再操作。",
    "The record changed. Close this form, refresh and review before retrying.",
  ],
  RESOURCE_LIMIT: [
    "记录数量超过本版读取上限，请联系实施人员处理。",
    "The source edition’s record limit was reached. Contact your implementer.",
  ],
  INVALID_USERNAME: [
    "账号须为3–60位字母、数字、下划线、点或短横线。",
    "Use 3–60 letters, digits, underscores, dots or hyphens for the username.",
  ],
  INVALID_CURRENCY: [
    "请选择支持两位小数的ISO币种，如CNY或USD。",
    "Choose a two-decimal ISO currency, such as CNY or USD.",
  ],
  INVALID_PERMISSION: [
    "选择的权限不在系统权限目录内。",
    "Select permissions registered by the system.",
  ],
  INVALID_SCOPE: [
    "该权限与所选数据范围不匹配。",
    "The permissions and data scope do not match.",
  ],
  INVALID_SETTING: [
    "此参数不是可编辑的系统参数。",
    "This setting is not editable.",
  ],
  REGISTERED_MENUS_ONLY: [
    "菜单由系统登记，仅能编辑现有菜单。",
    "Edit registered menus; new interface routes cannot be added here.",
  ],
  REGISTERED_PERMISSIONS_ONLY: [
    "接口权限由系统登记，仅能编辑名称。",
    "Edit registered permission labels; new API permissions cannot be added here.",
  ],
  REGISTERED_SETTINGS_ONLY: [
    "系统参数由系统登记，请编辑现有参数。",
    "Edit an existing system setting.",
  ],
  WEAK_PASSWORD: [
    "密码至少12位，须含大小写字母和数字，UTF-8不超过72字节。",
    "Use 12+ characters with uppercase, lowercase and digits; maximum 72 UTF-8 bytes.",
  ],
  BUILTIN_RESOURCE: [
    "这是系统内建资源，不能删除。",
    "Built-in resources cannot be deleted.",
  ],
  CURRENCY_LOCKED: [
    "已有套餐或会员卡，不能更改币种。",
    "Currency cannot change after plans or membership cards exist.",
  ],
  DICTIONARY_KEY_IMMUTABLE: [
    "已有分类的类型和代码不能更改。",
    "Existing dictionary type and code cannot change.",
  ],
  LAST_ADMIN: [
    "必须保留至少一个启用的全范围管理员。",
    "Keep at least one enabled administrator with all-branch access.",
  ],
  LINKED_ACCOUNT_REQUIRED: [
    "会员或教练角色必须绑定对应档案。",
    "Member and coach accounts need the matching profile binding.",
  ],
  MEMBER_BINDING_IMMUTABLE: [
    "已有账号的会员或教练绑定不能转换，请新建独立账号。",
    "Existing member and coach bindings cannot change. Create a separate account.",
  ],
  MEMBER_ROLE_REQUIRED: [
    "会员账号只能使用本人会员范围和会员入口权限。",
    "Member accounts require own-member scope and only member-portal permission.",
  ],
  TRAINER_ROLE_REQUIRED: [
    "教练账号只能使用指派课程范围和教练入口权限。",
    "Coach accounts require assigned-class scope and only coaching permission.",
  ],
  BINDING_EXCLUSIVE: [
    "账号只能绑定会员或教练其中一种身份。",
    "An account can bind to a member or a coach, not both.",
  ],
  TIMEZONE_LOCKED: [
    "已有会员卡或课次，门店时区不能更改。",
    "The branch timezone cannot change after cards or classes exist.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "The old password is incorrect."],
  LOGIN_FAILED: [
    "账号或密码不正确，或账号已停用。",
    "Incorrect credentials or the account is disabled.",
  ],
  LOGIN_THROTTLED: [
    "登录失败次数过多，请五分钟后再试。",
    "Too many failed logins. Try again in five minutes.",
  ],
  AMBIGUOUS_LOCAL_TIME: [
    "该本地时间处于夏令时缺口或重复区间，请换一个明确时间。",
    "This local time is missing or repeated due to daylight saving. Choose a unique time.",
  ],
  INVALID_AMOUNT: [
    "金额须大于0，最多两位小数，且不超过999999.99。",
    "Use a positive amount with at most two decimals, no greater than 999999.99.",
  ],
  PASS_FROZEN: [
    "这节课在会员卡冻结期间内，不能预约或签到。",
    "This class falls within the pass freeze interval.",
  ],
  PASS_NOT_VALID: [
    "会员卡尚未足额收款、已关闭或有效期不覆盖整节课。",
    "The pass is unpaid, closed or does not cover the full class.",
  ],
  INVALID_START_DATE: [
    "开卡开始日期须在门店今天至未来730天内。",
    "Choose a start date between branch today and 730 days ahead.",
  ],
  INVALID_SESSION_TIME: [
    "请选择未来730天内的开课时间。",
    "Choose a future class time within 730 days.",
  ],
  INVALID_CASH_KIND: [
    "请选择收款、退款或冲正登记。",
    "Choose a receipt, refund or reversal record.",
  ],
  INVALID_CHECK_CODE: [
    "请扫描完整的会员动态签到码。",
    "Scan a complete member check-in code.",
  ],
  ATTENDANCE_ALREADY_RECORDED: [
    "该预约已核销或取消，不能再次签到。",
    "The booking is already checked in, consumed or cancelled.",
  ],
  ATTENDANCE_EXISTS: [
    "已有签到记录，请先核对并纠错后再取消课程。",
    "Attendance exists. Review and correct it before cancelling the class.",
  ],
  BOOKING_CLOSED: [
    "这节课未发布或已经开课，不能新预约。",
    "The class is unpublished or already started.",
  ],
  CANCELLATION_DEADLINE: [
    "已超过取消期限，请联系前台核对。",
    "The cancellation deadline passed. Contact reception.",
  ],
  CHECKIN_WINDOW: [
    "签到开放时间为开课前30分钟至结束后30分钟。",
    "Check-in is available from 30 minutes before class until 30 minutes after it ends.",
  ],
  CHECK_CODE_EXPIRED: [
    "签到码已过期、使用或被刷新，请会员重新生成。",
    "The code expired, was used or was refreshed. Ask the member for a new code.",
  ],
  CLASS_FULL: [
    "这节课名额已满，请选择其他课次。",
    "The class is full. Choose another session.",
  ],
  CLOSE_BEFORE_REFUND: [
    "先关卡并处理未完成预约，再登记实际退款。",
    "Close the pass and resolve pending bookings before recording a refund.",
  ],
  CLOSE_BEFORE_REVERSAL: [
    "生效卡的收款不能直接冲正，请先关卡。",
    "Close the active card before reversing its receipt.",
  ],
  CREDIT_INVARIANT: [
    "课时余额与记录不一致，操作未提交，请联系管理员核对。",
    "Credit records do not reconcile. Nothing was saved; contact the administrator.",
  ],
  DUPLICATE_REFERENCE: [
    "这份外部凭据已登记，不能重复提交。",
    "This external reference was already recorded.",
  ],
  FREEZE_BOOKING_CONFLICT: [
    "已有预约会落入冻结期或调整后的到期日之外，请先处理预约。",
    "A booking conflicts with the freeze or revised expiry. Resolve it first.",
  ],
  FREEZE_LIMIT: [
    "累计冻结延期不能超过365天。",
    "Total freeze extensions cannot exceed 365 days.",
  ],
  INVALID_ORIGINAL: [
    "原资金记录不匹配、已冲正或金额与原记录不一致。",
    "The original record is mismatched, reversed or has a different amount.",
  ],
  INVALID_STATE: [
    "该记录当前状态不允许此操作。",
    "The current record state does not allow this action.",
  ],
  MEMBER_DISABLED: [
    "会员已停用，不能新开卡、预约或签到。",
    "This member is disabled. New passes, bookings and attendance are blocked.",
  ],
  COACH_DISABLED: [
    "教练已停用，请联系管理员核对。",
    "This coach is disabled. Contact the administrator.",
  ],
  MEMBER_TIME_CONFLICT: [
    "会员在这个时间已有其他预约，不能重叠约课。",
    "The member already has an overlapping class.",
  ],
  NO_BOOKING: [
    "该会员没有这节课的预约，请先核对。",
    "This member has no booking for the selected class.",
  ],
  NO_CREDITS: [
    "该次卡剩余课时已被预留或用完。",
    "No unreserved class credits remain.",
  ],
  PASS_NOT_FROZEN: [
    "会员卡当前没有生效的冻结记录。",
    "The pass has no current freeze.",
  ],
  PAYMENT_EXCEEDS_DUE: [
    "收款金额超过待收余额，请核对实际金额。",
    "The receipt exceeds the outstanding amount.",
  ],
  PERIOD_PASS_REQUIRED: [
    "场馆到店登记需要本人当前有效的期限卡。",
    "Venue entry requires the member’s currently valid period membership.",
  ],
  PLAN_NOT_AVAILABLE: [
    "套餐已停用或不属于会员所在门店。",
    "The plan is disabled or belongs to another branch.",
  ],
  REFUND_DEPENDENCY: [
    "原收款已有未冲正退款，不能直接冲正收款。",
    "Reverse outstanding refunds before reversing their receipt.",
  ],
  REFUND_EXCEEDS_RECEIPT: [
    "退款超过这笔原收款剩余可退金额，或原收款已冲正。",
    "The refund exceeds the remaining amount of this original receipt.",
  ],
  RESOURCE_NOT_AVAILABLE: [
    "课程、教练、教室或分类已停用，或不属于此门店。",
    "The course, category, coach or room is disabled or belongs to another branch.",
  ],
  ROOM_CAPACITY: [
    "课程名额不能超过教室容量。",
    "Class seats cannot exceed the room capacity.",
  ],
  SCHEDULE_CONFLICT: [
    "教练或教室在这个时间已被安排，不能重叠。",
    "The coach or room is already booked at this time.",
  ],
  SESSION_NOT_DRAFT: [
    "请先撤回无预约课程为草稿，再编辑。",
    "Withdraw a class with no active bookings to draft before editing it.",
  ],
  SESSION_NOT_FINISHED: [
    "课程尚未结束，不能登记未到或结束课程。",
    "The class has not ended. No-show and completion cannot be recorded yet.",
  ],
  QR_REFRESH_LIMIT: [
    "请稍候几秒再刷新签到码。",
    "Wait a few seconds before refreshing the code.",
  ],
  INVALID_CODE: [
    "代码须为2–60位字母、数字、点、下划线或短横线。",
    "Use 2–60 letters, digits, dots, underscores or hyphens for codes.",
  ],
  INVALID_IMPORT: [
    "一次导入须有1–300条有效会员记录。",
    "Import 1–300 valid member records per batch.",
  ],
  ACTIVE_BOOKINGS: [
    "仍有未取消的预约或核销记录，请先处理。",
    "Resolve active bookings or attendance before this action.",
  ],
  BRANCH_DISABLED: [
    "门店已停用，请联系管理员核对。",
    "This branch is disabled. Contact the administrator.",
  ],
  BRANCH_IMMUTABLE: [
    "已有档案不能换门店，请建立独立档案。",
    "Existing profiles cannot move branches. Create a separate profile.",
  ],
  CATEGORY_DISABLED: [
    "请选择启用的课程分类。",
    "Choose an enabled course category.",
  ],
  CODE_IMMUTABLE: [
    "已有会员或教练代码不能更改。",
    "Existing member and coach codes cannot change.",
  ],
  RESOURCE_IN_USE: [
    "教练或教室仍被课程引用，请先调整或取消课程。",
    "Upcoming classes use this coach or room. Change or cancel them first.",
  ],
  INVALID_REPORT_DATES: [
    "请选择先后正确且跨度不超过366天的日期。",
    "Choose ordered dates spanning no more than 366 days.",
  ],
  ALREADY_BOOKED: [
    "会员已经预约或核销过这节课。",
    "The member already booked or attended this class.",
  ],
  ALREADY_VISITED: [
    "会员今天已登记到店，不会再次计数。",
    "The member already checked into the venue today.",
  ],
  NETWORK_ERROR: [
    "无法读取服务，请检查连接后刷新。",
    "Unable to read the service. Check your connection and refresh.",
  ],
  RESULT_UNKNOWN: [
    "未收到保存结果，不代表未保存。请关闭窗口、刷新核对，勿重复提交。",
    "The save result is unknown. Close this form and refresh to check; do not submit again.",
  ],
  UPLOAD_TOO_LARGE: [
    "导入内容过大，请分批处理。",
    "The import is too large. Split it into smaller batches.",
  ],
  IMPORT_INVALID: [
    "整批导入未提交，请修正标出的记录后重新预览。",
    "No rows were committed. Fix the highlighted record and preview again.",
  ],
  INVALID_CSV: [
    "CSV引号或换行格式不正确，请使用下载模板。",
    "Invalid CSV quotes or line breaks. Use the template.",
  ],
  CSV_HEADER: [
    "表头须为 code,name,departmentId,contactNote,enabled。",
    "Use the header code,name,departmentId,contactNote,enabled.",
  ],
  CSV_COLUMNS: [
    "该记录必须包含5个字段。",
    "Each record must contain five fields.",
  ],
  CSV_BOOLEAN: ["enabled须填写true或false。", "enabled must be true or false."],
  DUPLICATE_MEMBER: [
    "同一批导入中会员代码重复。",
    "A member code is duplicated in this batch.",
  ],
  CAMERA_UNAVAILABLE: [
    "无法启用摄像头，请使用HTTPS并允许摄像头，或使用扫码枪及图片识别。",
    "Camera unavailable. Use HTTPS and grant access, or use a scanner or QR image.",
  ],
  NO_QR_FOUND: [
    "没有识别到完整二维码，请换清晰图片。",
    "No complete QR code was found. Choose a clearer image.",
  ],
};
/** 安全错误说明与导入记录编号，不回显原始个人资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function errorMessage(error, lang = "zh") {
  const index = lang === "zh" ? 0 : 1;
  let text = (errors[error.message] || errors.INVALID_INPUT)[index];
  if (error.row)
    text +=
      (index === 0 ? " 记录 " : " Record ") +
      error.row +
      " · " +
      (errors[error.detail] || errors[error.message] || errors.INVALID_INPUT)[
        index
      ];
  return text;
}
