// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import QRCode from "qrcode";
import {
  BinaryBitmap,
  HybridBinarizer,
  RGBLuminanceSource,
  QRCodeReader,
} from "@zxing/library";
test("real generated QR pixels decode to the complete short-lived code payload", () => {
  const value = "CLUB1:VGVzdDEyMzQ1Njc4OTAxMjM0NTY3ODkw";
  const qr = QRCode.create(value, { errorCorrectionLevel: "M" });
  const module = qr.modules.size,
    scale = 5,
    margin = 4,
    width = (module + margin * 2) * scale;
  const pixels = new Uint8ClampedArray(width * width).fill(255);
  for (let y = 0; y < module; y++)
    for (let x = 0; x < module; x++)
      if (qr.modules.get(y, x))
        for (let i = 0; i < scale; i++)
          for (let j = 0; j < scale; j++)
            pixels[
              ((y + margin) * scale + i) * width + (x + margin) * scale + j
            ] = 0;
  const bitmap = new BinaryBitmap(
    new HybridBinarizer(new RGBLuminanceSource(pixels, width, width)),
  );
  assert.equal(new QRCodeReader().decode(bitmap).getText(), value);
});
