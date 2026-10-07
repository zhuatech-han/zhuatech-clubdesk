[中文](README.md) | [English](README.en.md)

<p><img src="frontend/public/brand/logo.jpg" width="64" alt="ZhiHua Technology logo"></p>

# ClubDesk · Membership and Class Management

**1.0.0 · Public source, non-commercial use. Written authorization is required for commercial use.**

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · [Official website](https://www.zhuatech.cn/).

ClubDesk is for independent fitness gyms, yoga and Pilates studios, and teams implementing their software. It connects membership terms, recorded external receipts, class capacity, reservation holds, attendance, credit usage and refund history in one traceable workflow. Chinese and English interfaces share a real backend; desktop and phone users access their privately deployed instance.

[Operating manual (Chinese)](docs/manual.md) · [English operating guide](docs/english-guide.md) · [Deployment and recovery (Chinese)](docs/deployment.md) · [API (Chinese)](docs/api.md)

## A complete membership workflow

1. Create branches, members, coaches, rooms, courses and plans. Create member and coach accounts bound to their respective profiles.
2. Issue a card with a snapshot of agreed price, credits, validity and currency. It starts unpaid; record verified external receipts, including installments. Full payment activates the card. Editing a plan does not rewrite sold cards.
3. Schedule a draft after room capacity and coach/room conflict checks, then publish it. Members select a class and their own eligible card on a phone. Duplicate or overlapping bookings are rejected.
4. Booking holds a seat and one credit. Verified attendance converts the hold into usage. Repeated check-in or concurrent attempts at the last seat cannot double-spend credits.
5. Member cancellation before the class deadline releases the hold. Staff exceptions require a reason. After class, a verified no-show consumes one credit. Corrections append history and return usage; they do not erase the original event.
6. Freezing checks conflicting bookings and extends expiry. Early unfreeze removes unused extension without invalidating existing bookings. Renewal creates a new card. Closing cancels pending bookings; record actual refunds separately against the original receipt and its refundable balance.

**Period memberships** allow unlimited class bookings while valid, plus venue-arrival registration. **Credit packs** grant one credit per class, not unlimited venue entry. Plans belong to one branch; there is no stored-value wallet or shared cross-branch membership.

## Staff, coach and member interfaces

- **Branch staff:** member profiles, card issuance, installment receipts, scheduling and publication, assisted booking, attendance, freezes, closing/refunds, reconciliation, export and audit. Both buttons and server endpoints check permissions.
- **Coaches:** their assigned published classes and rosters, attendance and no-shows. They cannot read member contact notes, card prices, cash records or other coaches' rosters.
- **Members:** their own cards, cash and credit history, branch classes, booking/cancellation, short-lived check-in codes and venue-arrival history. Other members' profiles are inaccessible.

One instance serves one operator and can contain multiple branches. Administrators can access all branches; ordinary staff have one branch. Member and coach profiles remain bound to their branch. This is not a multi-company SaaS or a shared chain-wide membership system.

## Actual running screens

Screenshots use explicitly marked TEST acceptance data. Fresh installation does not create these members, transactions or classes.

| Sign-in: account authentication and language switch | Workspace: authorized member/card/class overview |
|---|---|
| ![Sign-in](docs/screenshots/login.jpg) | ![Workspace](docs/screenshots/workspace.jpg) |

| Member portal: own cards and reservations | Schedule: drafts, publication and capacity |
|---|---|
| ![Member portal](docs/screenshots/portal.jpg) | ![Schedule](docs/screenshots/schedule.jpg) |

| Membership detail: terms, cash and credit history | Coach interface: assigned classes only |
|---|---|
| ![Membership](docs/screenshots/pass.jpg) | ![Coaching](docs/screenshots/coach.jpg) |

| Accounts: roles and profile bindings | Roles: permissions and data scope |
|---|---|
| ![Accounts](docs/screenshots/accounts.jpg) | ![Roles](docs/screenshots/roles.jpg) |

| Reconciliation: branch cash and attendance | Phone: member booking and check-in code controls |
|---|---|
| ![Reports](docs/screenshots/reports.jpg) | ![Phone](docs/screenshots/mobile.jpg) |

## Implemented features

| Area | Implemented behavior |
|---|---|
| Members and catalogue | Create/edit/enable members, coaches, rooms, courses and credit/period plans; versions and reference protection; search, sorting and pagination; member CSV preview, per-record feedback and atomic import |
| Membership and cash | Unpaid cards, immutable terms, activation after full recorded receipts, installments, receipt-linked refunds, once-only reversals, immutable cash history, currency lock, freezes/early unfreeze, renewal and closing |
| Scheduling | Branch-local times, IANA zones, coach/room conflicts, capacity, draft/publish/withdraw/cancel/finish and cancellation-deadline snapshots |
| Booking and attendance | Self/assisted booking; one member-class record; hold/release/use/correction credit entries; overlap and balance protection; single-use code, manual attendance, no-show usage and once-per-local-day period-card venue arrival |
| Administration | Accounts, roles, permission labels, menu labels/order/enabled state, branches/timezones, course categories and parameters; immutable bound identities and last-administrator protection |
| Shared capabilities | BCrypt cost 12, HttpOnly sessions, CSRF, strong passwords and old-credential invalidation, live endpoint and data permissions, business/audit history, Chinese/English, responsive screens, dashboard, branch-date reporting and CSV |

## Explicit limitations

- Receipts, refunds and reversals are **manual records of independently verified external facts**. The system does not transfer money, charge cards, verify payment callbacks or issue tax invoices. A software record is not proof of payment.
- Check-in codes last two minutes and are single-use. Keyboard scanner input, local QR-image decoding and browser camera reading are implemented. Cameras require HTTPS or a secure local origin and device permission. Staff must still verify physical identity. Door unlocking, biometrics, offline attendance, location checks and prevention of proxy attendance are not included.
- No online payments, subscription charging, SMS, email/WhatsApp notifications, electronic signatures, general ledger, coach payroll, weighted credits, shared branch memberships, public registration or native app. Hardware and payment integration requires separate development and acceptance.
- Each attended/no-show reservation uses one credit; capacity-one classes can represent personal training. There is no waiting list, recurring bulk scheduling, shared card, repeating cross-timezone rule or automatic class restoration.
- Authorized lists are loaded and searched/sorted on the client, ten rows per page, at most 10,000 records per entity type. This is not large-scale server pagination. A foundation-branch lock serializes instance writes for small studios; it is not a high-throughput distributed SaaS.

## Install a fresh instance

Docker and Compose v2 are sufficient. Separate development requires Java **21**, Maven **3.9**, Node **24.19.0+**, MySQL **8.4**. Backend: Spring Boot **4.0.7**, Security, JPA and Flyway. Frontend: Vue **3.5.40**, Vite **8.1.5**. MySQL Connector/J **9.7.0** connects to MySQL using `jdbc:mysql://`.

```bash
python3 scripts/init-env.py
# Credentials remain in private .env. Override WEB_PORT if occupied.
docker compose config --quiet
docker compose up -d --build --wait
```

Visit [http://localhost:8124/](http://localhost:8124/); health is [http://localhost:8124/actuator/health](http://localhost:8124/actuator/health). Initial username is `admin`; password is `ADMIN_PASSWORD` in your private `.env`. **There is no public demo password.** Initialization creates three independent strong passwords with file permissions 0600, refuses overwriting existing credentials, and does not reset business data or passwords on restart.

The empty database initializes one main branch, six roles, twelve permissions, eleven menus, four course categories, three settings and the administrator. It does **not** seed members, coaches, rooms, plans, cards, reservations or cash records.

| Configuration | Purpose |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Unique database-administration password |
| `DATABASE_PASSWORD` | Independent application-database password |
| `ADMIN_USERNAME / ADMIN_PASSWORD` | Initial administrator; 12–72-byte password with upper/lowercase letters and digits |
| `WEB_PORT / BIND_ADDRESS` | Default 8124 / 127.0.0.1, configurable |
| `COOKIE_SECURE` | false for local HTTP; true behind trusted HTTPS |
| `DATABASE_URL / DATABASE_USER` | Optional external MySQL; use certificate verification and trusted CA for an external connection |

Phones need an address reachable from the phone; phone `localhost` is not the computer. Use trusted HTTPS for a deployed entry point. No model service, third-party secret, SMS or cloud account is needed for core operations. QR images are decoded locally in the browser and not uploaded to the server.

For separate development, start MySQL and safely inject `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and administrator variables. Run `mvn -f backend/pom.xml spring-boot:run`; run `cd frontend && npm ci --no-audit --no-fund && npm run dev` for Vite. Its default port is 5173 with a same-origin proxy to 8080. Production Nginx proxies `/api` using the backend container's service name.

## Project layout and database

```text
backend/src/main/java/cn/zhuatech/clubdesk/   Access, cards, classes, credits and cash
backend/src/main/resources/db/migration/    Versioned Flyway schema
backend/src/test/                           Rules and HTTP/JPA regression
frontend/src/                              Staff, member, coach, admin and scanning
scripts/                                  Credential initialization and verification
frontend/public/brand/ + docs/images/       Original brand assets
docs/                                     Operations, API, architecture and deployment
compose.yaml                              MySQL, Java and Vue/Nginx services
```

There are 22 application tables plus Flyway history; see [V1](backend/src/main/resources/db/migration/V1__club_schema.sql). Money uses BigDecimal and two-decimal database values; extra precision and fractional integers are rejected. Membership/class terms, business events, cash and credit entries persist in the database. READ_COMMITTED transactions, a write lock, foreign keys, unique constraints and state/balance rules protect history.

Before an upgrade, back up and restore into an independent new volume. Verify accounts, roles, classes, balances and original entries, then add V2 or later migrations. Do not rewrite an executed V1. JPA validates schema instead of creating it. See [architecture](docs/architecture.md) and [backup/recovery](docs/deployment.md#备份与恢复).

## Tests and deployment checks

```bash
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
docker compose build
python3 scripts/release-check.py
git diff --check
```

Backend regression has 33 rule/HTTP/JPA tests; frontend has 16 tests. H2 does not replace real MySQL acceptance. Docker Maven builds run all tests. Only on a **fresh, disposable** test instance, run `TEST_URL=http://127.0.0.1:8124 python3 scripts/smoke-test.py`; it refuses nonempty business data and creates TEST records. See [testing](docs/testing.md) for scope and physical-device limitations.

Compose keeps MySQL in a named volume without exposing its database port. Backend startup waits for healthy MySQL; frontend waits for a healthy backend. Use a trusted HTTPS reverse proxy, `COOKIE_SECURE=true`, strong passwords, restricted networking and restore drills. Default Compose is a local isolated deployment, not a preconfigured production TLS hosting service. Stop without deleting business volumes; use `down -v` only for this named disposable test environment. Detailed backup/restore commands are in [deployment](docs/deployment.md).

Common issues:

- Ineligible card: check full recorded payment, whole-class validity, freeze interval, unheld credits and branch.
- Unavailable class: check publication, start time, remaining seats and overlap. Drafts cannot be booked; resolve bookings before editing a class.
- Freeze rejection: cancel conflicting reservations first; early unfreeze must not invalidate future bookings.
- Invalid code: check expiry, refresh and prior use. A wrong class does not consume it; choose the correct class and verify identity.
- Incorrect cash record: append an actual reversal. Close active cards first and resolve dependent refunds before reversing their receipt.
- Import/version conflict: failed batches save no partial data. Fix the reported record and preview again. Refresh stale versions; never automatically replay an uncertain write.
- Startup failure: inspect credentials, port and the three containers' health/logs. Do not delete other projects' data.

## Security, contribution and license

Use least-privilege staff roles and regular backup/restore acceptance. Password hashes never enter API responses. Plain short-lived check-in codes appear only in the member's generation response; the database stores hashes. Login failure limiting is instance-local for five minutes; SSO, MFA and distributed rate limiting are not implemented. Branch timezone locks after dependent cards/classes; currency locks after priced plans/cards. See [security](docs/security.md).

Contributions should include a reproducible business issue, validation and a small reviewable diff, preserving attribution and licenses. Public Issues must contain only sanitized steps and environment details, never customer data, credentials, sessions or database copies. Report vulnerabilities privately through the contacts below.

This is **public source for personal learning, technical research and non-commercial exchange**, not an OSI-approved open-source license. Commercial use, enterprise deployment, paid client delivery, resale and SaaS operation require prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. [LICENSE](LICENSE) controls the terms. Vue, Lucide, QRCode, ZXing and other dependencies retain their own licenses; see [third-party notices](docs/third-party.md). Their MIT/Apache terms do not make ClubDesk freely usable commercially.

Software is provided as-is. Deployers must independently accept their own business rules, refund terms, third-party services and security requirements. No payment-arrival, tax, hardware or regulatory guarantee is made.

## Contact ZhiHua Technology

For commercial licensing or in-depth custom development, contact ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.). Services include source licensing, private deployment, membership-data migration, brand adaptation, hardware/payment adapter development, business-rule customization and system integration. Commercial authorization does not automatically transfer source copyright.

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
