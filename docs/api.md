# ClubDesk 接口与状态

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业咨询微信 zhuatech、zhuatech2。自有源码非商业使用，以根目录 LICENSE 为准。

## 认证与错误

同源 HTTP 会话，Cookie 为 `CLUBDESK_SESSION`、HttpOnly、SameSite=Strict，空闲30分钟失效。先 `GET /api/auth/csrf` 获取 `header`、`token`；所有 POST、PUT、DELETE 在该响应指定的请求头中发送 token，携带同一会话。`POST /api/auth/login` 的 JSON 为 `username`、`password`；登录后重新获取 CSRF。`GET /api/auth/me` 返回当前账号、权限与范围；`POST /api/auth/logout` 退出。`POST /api/auth/password` 接收 `oldPassword`、`newPassword`，成功使旧认证凭据失效。

响应为 JSON；CSV 成功响应例外。401 表示认证失效，403 表示权限或资料范围不足，400 表示字段错误，409 表示版本、引用或状态冲突。业务错误有稳定 `code`，整批导入错误另外提供从1开始的 `row`。不返回堆栈或密码散列。前端将错误码映射为中英文反馈。

写入不自动重试。连接在提交后断开时可能已保存，先刷新核对再操作，不能按失败重扣课时或重复登记收款。状态与目录编辑带当前 `revision`；预约带卡和课次各自版本。

## 路由

| 路由 | 权限与行为 |
|---|---|
| GET `/api/dashboard`、`/api/workspace` | 工作台或门店业务查看；后台每次重新核对账号范围 |
| GET `/api/portal`、`/api/coaching` | 绑定会员本人资料，或绑定教练被指派的课程 |
| POST `/api/master/{type}`；PUT/DELETE `/api/master/{type}/{id}` | `master`；type为members/coaches/rooms/courses/plans，删除带revision查询参数 |
| POST `/api/members/import` | `master`；最多300条 JSON 记录，任一失败整批回滚 |
| POST `/api/passes`；GET `/api/passes/{id}` | 建立待收款卡；授权员工或本人查看卡约定及台账 |
| POST `/api/passes/{id}/{freeze,unfreeze,close}` | `membership`；有效版本及原因，freeze另外带days |
| POST `/api/passes/{id}/cash` | `finance`；登记外部收款、退款或冲正，不能发起支付 |
| POST `/api/sessions`；PUT `/api/sessions/{id}` | `schedule`；创建或编辑无有效预约的草稿 |
| GET `/api/sessions/{id}` | 授权员工或该课次指派教练查看名单；教练不读取联系资料或金额 |
| POST `/api/sessions/{id}/{publish,withdraw,cancel,finish}` | `schedule`；状态及时间校验，结束归档未到人员 |
| POST `/api/reservations`、`/api/portal/reservations` | 员工代约或绑定会员本人约课；同样校验名额、卡、时间与门店 |
| GET `/api/reservations/{id}` | 授权员工、本人或对应教练的预约历史 |
| POST `/api/reservations/{id}/cancel`、`/api/portal/reservations/{id}/cancel` | 员工取消须原因；本人取消另核对提前截止时间 |
| POST `/api/reservations/{id}/{attend,no-show,correct}` | 前台或指派教练核验出勤；纠错限授权员工并追加历史 |
| POST `/api/portal/check-token` | 本人生成2分钟单次码；至少间隔5秒，刷新作废旧码 |
| POST `/api/checkin`、`/api/visits` | 前台或指派教练核验课程；期限卡场馆到店只限前台 |
| GET `/api/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | `report`；按门店本地日期汇总 |
| GET `/api/exports/{type}.csv` | `report`；type为members/passes/attendance/cash；权限不足仍为JSON错误 |
| GET `/api/audit` | `audit`；当前资料范围的操作记录 |
| GET/POST `/api/admin/{type}`；PUT/DELETE `/api/admin/{type}/{id}` | `admin`；type为users/roles/permissions/menus/departments/dictionaries/settings |
| GET `/actuator/health` | 公开健康状态，未开放其他Actuator端点 |

## 核心载荷

以下主键和版本取自当前授权读取，不存在固定演示ID。

| 操作 | 字段 |
|---|---|
| 开卡 | memberId、planId、startsOn（YYYY-MM-DD）、note |
| 排课 | departmentId、courseId、coachId、roomId、localStart（门店本地ISO时间）、capacity、cancelHours、note、revision（编辑） |
| 预约 | memberId、sessionId、passId、sessionRevision、passRevision |
| 卡或预约状态 | revision、note；冻结另外days（1–90） |
| 资金 | kind（RECEIPT/REFUND/REVERSAL）、reference（外部唯一编号）、amount（正数两位小数）、originalId（退款/冲正）、note、revision |
| 扫码 | token、sessionId（课程签到）；不带课次为期限卡到店，可带passId选择有效卡 |
| 人工到店 | memberId、passId |

基础资料字段见[表单定义](../frontend/src/schema.js)和[MasterService.Input](../backend/src/main/java/cn/zhuatech/clubdesk/MasterService.java)，后台字段见[AdminService.Input](../backend/src/main/java/cn/zhuatech/clubdesk/AdminService.java)。选项、布尔值与整数严格校验，不接受小数容量截断或额外资金精度。

## 状态约束

- 卡持久化业务状态：DRAFT（待收款）→ ACTIVE（足额）→ CLOSED。冻结写入区间及延期，不把资金生效状态改为FROZEN；详情的effective字段按日期派生FROZEN、UPCOMING或EXPIRED。冻结区间外且完整有效的课次可约。已关闭不能重新激活，延期累计上限365天；续开另建新卡。
- 课次：DRAFT → PUBLISHED → COMPLETED；无有效预约且尚未开课可撤回DRAFT；取消变CANCELLED并释放未完成预约。已到课/未到核销的课次不能取消。
- 预约：BOOKED → ATTENDED / NO_SHOW / CANCELLED。取消释放预留；到课或未到将预留转已使用；纠错保留历史并退回使用量。取消后可重新预约，沿用唯一会员课次记录并追加新的事件。
- 资金：历史不能编辑或删除；退款引用原收款，单次全额冲正引用原记录，依赖退款须先处理。软件不核验银行到账。
