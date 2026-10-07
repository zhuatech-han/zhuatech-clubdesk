// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 控制摄像头生命周期；页面切换或退出发生在启动完成前也必须释放摄像头。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function scannerSession(reader, video, onResult) {
  let cancelled = false,
    controls;
  const ready = reader
    .decodeFromVideoDevice(undefined, video, (result, _error, current) => {
      if (cancelled || !result) return;
      cancelled = true;
      current?.stop();
      controls?.stop();
      onResult(result.getText());
    })
    .then((value) => {
      controls = value;
      if (cancelled) value.stop();
      return value;
    });
  return {
    ready,
    stop() {
      cancelled = true;
      controls?.stop();
      for (const track of video.srcObject?.getTracks() || []) track.stop();
      video.srcObject = null;
    },
  };
}
