// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { commitChange, profileKey } from "./workflow.js";
import { scannerSession } from "./scanner.js";
test("successful write and failed refresh never replay the write", async () => {
  let writes = 0,
    committed = 0;
  const fault = new Error("NETWORK_ERROR");
  const result = await commitChange(
    async () => {
      writes++;
    },
    () => {
      committed++;
    },
    async () => {
      throw fault;
    },
  );
  assert.equal(result, fault);
  assert.equal(writes, 1);
  assert.equal(committed, 1);
});
test("a rejected write preserves the form and never calls committed or refresh", async () => {
  const form = { reference: "TEST-reference", amount: "10.01" };
  let committed = false,
    refreshed = false;
  await assert.rejects(
    commitChange(
      async () => {
        throw new Error("PAYMENT_EXCEEDS_DUE");
      },
      () => {
        committed = true;
        form.amount = "";
      },
      async () => {
        refreshed = true;
      },
    ),
    /PAYMENT_EXCEEDS_DUE/,
  );
  assert.deepEqual(form, { reference: "TEST-reference", amount: "10.01" });
  assert.equal(committed, false);
  assert.equal(refreshed, false);
});
test("camera is stopped even when navigation occurs before initialization resolves", async () => {
  let finish,
    stops = 0,
    tracks = 0;
  const video = {
    srcObject: {
      getTracks: () => [
        {
          stop() {
            tracks++;
          },
        },
      ],
    },
  };
  const reader = {
    decodeFromVideoDevice: () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  };
  const camera = scannerSession(reader, video, () =>
    assert.fail("cancelled scanner emitted data"),
  );
  camera.stop();
  finish({
    stop() {
      stops++;
    },
  });
  await camera.ready;
  assert.equal(stops, 1);
  assert.equal(tracks, 1);
  assert.equal(video.srcObject, null);
});
test("scanner delivers only the first decoded code and releases its controls", async () => {
  let callback,
    stops = 0;
  const received = [];
  const controls = {
    stop() {
      stops++;
    },
  };
  const reader = {
    decodeFromVideoDevice: async (_id, _video, fn) => {
      callback = fn;
      return controls;
    },
  };
  const camera = scannerSession(reader, {}, (code) => received.push(code));
  await camera.ready;
  callback({ getText: () => "CLUB1:TEST" }, null, controls);
  callback({ getText: () => "CLUB1:TEST" }, null, controls);
  assert.deepEqual(received, ["CLUB1:TEST"]);
  assert.ok(stops >= 1);
});

test("access fingerprint changes for branch or scope but not permission order", () => {
  const a = {
    id: 1,
    role: "Staff",
    scope: "DEPARTMENT",
    departmentId: 1,
    permissions: ["read", "membership"],
  };
  assert.equal(
    profileKey(a),
    profileKey({ ...a, permissions: ["membership", "read"] }),
  );
  assert.notEqual(profileKey(a), profileKey({ ...a, departmentId: 2 }));
  assert.notEqual(profileKey(a), profileKey({ ...a, scope: "ALL" }));
});
