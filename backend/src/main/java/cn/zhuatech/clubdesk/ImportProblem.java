// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

/** 只返回导入记录编号与安全业务原因，不回显会员联系方式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class ImportProblem extends Problem {
  public final int row;
  public final String detail;

  public ImportProblem(int row, String detail) {
    super(400, "IMPORT_INVALID");
    this.row = row;
    this.detail = detail;
  }
}
