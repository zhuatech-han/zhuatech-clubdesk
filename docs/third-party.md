# 第三方依赖与声明

知华科技（上海如静知华信息科技有限公司） · [官网](https://www.zhuatech.cn/) · 商业咨询微信 zhuatech、zhuatech2。

自有业务源码遵循根目录[LICENSE](../LICENSE)的非商业条件。第三方许可证与原版权独立保留，品牌及联系方式不改变其授权。

| 依赖 | 用途与原许可 |
|---|---|
| Vue 3.5.40 | 页面框架，MIT；保留[正文](../frontend/public/third-party/vue.txt) |
| Lucide Vue 1.48.0 | 操作图标，ISC；保留[正文](../frontend/public/third-party/lucide.txt) |
| QRCode 1.5.4 | 浏览器动态签到码编码，MIT；保留[正文](../frontend/public/third-party/qrcode.txt) |
| ZXing Browser 0.2.1 / Library 0.23.0 | 图片及浏览器设备解码，MIT / Apache-2.0；保留[Browser](../frontend/public/third-party/zxing-browser.txt)与[Library](../frontend/public/third-party/zxing-library.txt)正文 |
| Spring Boot / Security / Data JPA、Flyway、Hibernate、MySQL Connector/J 9.7.0 | 后端与迁移依赖，遵循各发布组件及传递依赖的原许可；版本见[pom](../backend/pom.xml) |
| MySQL 8.4、Eclipse Temurin、Nginx、Node、Maven镜像 | 容器运行及构建工具，遵循镜像发行和上游各自条款；不修改其版权 |

前端锁文件固定安装版本；后端由Spring Boot依赖管理及pom固定版本。发布自己的衍生产品时，核对对应版本完整依赖清单与发行条件。不得将第三方MIT/Apache许可误写为整个ClubDesk允许免费商用。

MySQL Connector/J 为 MySQL 官方驱动，遵循其 GPLv2 及 Universal FOSS Exception 等发行条款，见[官方许可说明](https://dev.mysql.com/doc/connector-j/en/connector-j-introduction.html)。商业发行应核对实际授权条件，知华自有业务代码的许可不替代驱动许可。
