# ClubDesk — setup and operating guide

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.). [Official website](https://www.zhuatech.cn/) · Commercial licensing and implementation: [han@zhuatech.cn](mailto:han@zhuatech.cn), [jack@zhuatech.cn](mailto:jack@zhuatech.cn), WhatsApp [+86 17521234993](https://wa.me/8617521234993). Public source for non-commercial use; written authorization is required for commercial use, paid deployment or resale.

[中文主页](../README.md) | [English README](../README.en.md)

ClubDesk serves independent fitness gyms, yoga and Pilates studios, and their software implementers. One operator can manage several branches. Staff, members and coaches belong to one branch; administrators have all-branch access. This is not a multi-company SaaS or a shared cross-branch membership wallet.

## Install

Docker and Compose v2 are sufficient. Development uses Java 21, Maven 3.9, Node 24.19.0+, MySQL 8.4, Spring Boot 4.0.7 and Vue 3.5.40. Run `python3 scripts/init-env.py`, `docker compose config --quiet`, then `docker compose up -d --build --wait`. Visit http://localhost:8124/; health is `/actuator/health`. The initial username is admin; the random password is in your private `.env` under `ADMIN_PASSWORD`. There is no public demo password. The script refuses to overwrite existing credentials. Override WEB_PORT if occupied.

The fresh database initializes only system roles, permissions, menus, four course categories, three settings and the administrator. No customers, cards, classes or financial records are fabricated. Create your actual catalogue and members before use. Click English to switch the operating interface.

## Operate a complete membership

1. Set the branch timezone and instance currency before creating cards, classes or priced plans. Those settings become locked after dependent records exist.
2. Add coaches, rooms and capacity, courses and duration, and credit packs or period memberships. Create members and bind their own-portal accounts. Bind coaches to assigned-coaching accounts; these identities cannot become employee accounts.
3. Issue a card with an agreed start date. Terms and price are snapshots. The card is unpaid until verified external receipts total the price; partial receipts are allowed. No payment is initiated by this application.
4. Schedule a future class in the **branch’s local timezone**, save a draft and publish it. Room/coach conflicts and seat capacity are checked. Missing or ambiguous daylight-saving times are rejected.
5. Members book their own classes from a phone. One seat and one credit are held, not spent. Double booking, overlapping classes, insufficient credits, frozen/expired cards and over-capacity attempts are rejected transactionally.
6. At reception, members show a two-minute single-use code. Staff or the assigned coach verify physical attendance and scan the code, read a local QR image, or record attendance from the roster. The window is 30 minutes before class to 30 minutes after its end. A successful check-in converts a hold into usage once.
7. Member cancellation releases a hold before the class’s cancellation deadline. Staff exceptions require a reason. After the class ends, confirmed no-shows consume a credit. Corrections append history and return the used credit; records are never erased.

Period memberships allow unlimited class bookings while valid, plus one venue arrival record per local day. Class packs grant one use per class and do not imply unlimited venue entry. Capacity-one classes can serve personal training. There is no weighted credit scheme or waiting list.

## Freeze, renew and refund

Freeze a currently active card for 1–90 days after resolving conflicting bookings. Expiry extends by that interval. Early unfreeze removes unused extension, but cannot invalidate an existing future booking. Total freeze extension is limited to 365 days. Renewal creates a separate new card; existing terms remain unchanged.

Close a card to cancel its pending reservations. Actual refunds must cite the original receipt and fit its refundable balance. Refund amounts are verified outside the software, not automatically priced by remaining credits. A wrong financial record is corrected through a once-only full reversal with a real external reference. Resolve dependent refunds before reversing their receipt. No banking, charging, tax invoicing or payment verification is performed.

## Import, reporting and safe deployment

Member CSV header: `code,name,departmentId,contactNote,enabled`. Use true/false, authorized branch IDs and at most 300 records. Preview before submitting; any failing record rolls the whole batch back. Quoted commas and multiline notes are supported. CSV exports cover members, cards, attendance and cash records, with spreadsheet formula text escaped.

Reports use each branch’s local dates. Company totals equal branch totals; receipt and refund figures include their reversals. These records are not a general ledger or proof that money arrived.

Use trusted HTTPS, strong passwords, limited network exposure, backups and restore drills. Phone localhost points to the phone, not the desktop. Camera scanning needs HTTPS or a secure local origin and permission; keyboard scanners, image decoding and manual reception remain available. Hardware models and camera devices require on-site acceptance. No door unlocking, biometrics, offline synchronization, public sign-up, payment gateway, native app, SMS, emails or electronic signatures are included.

A rejected save retains inputs. A committed save with a failed reload must not be submitted again. An unknown write result requires refreshing and checking; writes are never automatically replayed. Session expiry clears forms and private data; changed account scope clears old records. See the API, deployment, security and testing documents for exact interfaces and limits.
