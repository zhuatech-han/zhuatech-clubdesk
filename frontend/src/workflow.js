// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 写入失败保留输入，写入成功后只重载读取；禁止因重载失败重发写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function commitChange(write, committed, refresh) {
  await write();
  committed();
  try {
    await refresh();
    return null;
  } catch (error) {
    return error;
  }
}

/** 比较账号与资料范围，权限顺序变化不误清空数据，门店或岗位变更必须清理旧缓存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function profileKey(profile) {
  return JSON.stringify([
    profile?.id,
    profile?.role,
    profile?.scope,
    profile?.departmentId,
    profile?.memberId,
    profile?.coachId,
    [...(profile?.permissions || [])].sort(),
  ]);
}
