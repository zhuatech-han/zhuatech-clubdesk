# ClubDesk 部署与恢复

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 微信 zhuatech、zhuatech2。个人学习交流源码；企业部署与商业交付须书面授权。

## 本机启动

Docker及Compose v2，先 `python3 scripts/init-env.py`，再 `docker compose config --quiet` 和 `docker compose up -d --build --wait`。默认入口http://localhost:8124/，健康路径`/actuator/health`；admin初始口令位于私有`.env`的ADMIN_PASSWORD。已有.env不能再运行初始化覆盖；运行`docker compose ps`查看本实例健康，`docker compose logs backend`查看启动错误。日志与配置输出不得原样发到公开Issues。

MySQL8.4使用命名卷`mysql-data`，无主机数据库端口。后端等MySQL健康，前端等后端健康。镜像构建包含后端全部测试及前端格式、lint、测试和生产构建，不跳过Maven测试。修改WEB_PORT解决占用，不停止其他项目。分别开发见根目录README的Maven/Vite方法。

## 门店或公网入口

手机须使用能访问服务器的域名，不是手机localhost。用可信HTTPS入口代理前端8080，保留同源 `/api` 和健康路径；限制服务器网络，只对必要入口放行。安全配置在反向代理边界核验，避免信任用户任意转发头。HTTPS时设COOKIE_SECURE=true，首次使用完成本店岗位与权限验收。浏览器摄像头需要HTTPS或安全本地来源及用户设备授权；键盘输入、图片解码和人工核验不替代硬件型号验收。

默认Compose面向隔离本机网络，不是已经配置生产TLS的托管服务。若数据库跨服务器，安全注入DATABASE_URL/USER/PASSWORD，使用`jdbc:mysql://`并设置`sslMode=VERIFY_IDENTITY`及可信CA，不能把默认隔离网络连接中的信任模式用于公网数据库。真实配置、证书和备份留在版本控制之外。

## 备份与恢复

在本实例维护窗口暂停业务写入，私有备份目录设0700。以下命令在数据库容器内使用现有环境口令，命令参数不打印口令；SQL包含会员及资金数据，不可提交或公开。

```bash
mkdir -p private-backups
chmod 700 private-backups
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces zhuatech_clubdesk' > private-backups/clubdesk.sql
chmod 600 private-backups/clubdesk.sql
```

恢复先使用不同Compose项目名、不同前端端口和独立空卷。只启动目标mysql，确认目标环境与目标库后执行：

```bash
docker compose -p clubdesk-restore up -d --wait mysql
docker compose -p clubdesk-restore exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot zhuatech_clubdesk' < private-backups/clubdesk.sql
# 另设WEB_PORT为未占用测试端口，再启动其后端和前端
docker compose -p clubdesk-restore up -d --wait backend frontend
```

使用独立覆盖文件或专用私有环境文件设置恢复端口和凭据；不要让恢复服务占用原实例端口。恢复不会重新覆盖管理员，测试时使用备份中的账号口令。验证Flyway历史、账号岗位、卡约定、预约状态、课时原始流水、资金原收款/退款/冲正及门店对账，再决定是否切换实例。

升级先恢复验证，再以新迁移版本构建和启动。不能改写已运行的V1；数据库升级失败读取Flyway日志和实际版本，不能盲目删除卷。重启应保留业务，停止只用`docker compose stop`或不带`-v`的down。**仅本次可丢弃测试卷**在检查完成后才用对应项目`down -v`清理，业务卷和其他项目不操作。

Compose前端使用Docker内置DNS（127.0.0.11）动态解析backend，并禁用容器内未使用的IPv6解析；后端容器地址变化后代理自动更新。分别在宿主机运行Nginx时，不能原样使用该容器DNS地址，应按自己的受控网络配置后端与解析器。
