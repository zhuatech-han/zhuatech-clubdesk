// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { api, resetCsrf } from "./api.js";
const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
test("uncertain POST is not retried and keeps its uncertainty distinct from validation", async () => {
  const original = globalThis.fetch;
  resetCsrf();
  let requests = 0;
  globalThis.fetch = async () => {
    requests++;
    if (requests === 1)
      return json({ header: "X-CSRF-TOKEN", token: "TEST-csrf" });
    throw new Error("disconnected");
  };
  try {
    await assert.rejects(
      api("/passes", "POST", { memberId: 1 }),
      /RESULT_UNKNOWN/,
    );
    assert.equal(requests, 2);
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
test("CSV GET checks type, sends no body and never downloads an HTML error as data", async () => {
  const original = globalThis.fetch;
  resetCsrf();
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith("/csrf"))
      return json({ header: "X-CSRF-TOKEN", token: "TEST-csrf" });
    return new Response("code,name\r\nTEST,Example", {
      headers: { "Content-Type": "text/csv" },
    });
  };
  try {
    assert.equal(
      await api("/exports/members.csv", "GET", undefined, true),
      "code,name\r\nTEST,Example",
    );
    assert.equal(calls[1].options.body, undefined);
    globalThis.fetch = async () =>
      new Response("<html>Error</html>", {
        headers: { "Content-Type": "text/html" },
      });
    await assert.rejects(
      api("/exports/members.csv", "GET", undefined, true),
      /NETWORK_ERROR/,
    );
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
test("server import rejection preserves safe record number and cause", async () => {
  const original = globalThis.fetch;
  resetCsrf();
  globalThis.fetch = async (url) =>
    url.endsWith("/csrf")
      ? json({ header: "X-CSRF-TOKEN", token: "TEST-csrf" })
      : json({ code: "IMPORT_INVALID", row: 2, detail: "CONFLICT" }, 400);
  try {
    await assert.rejects(
      api("/members/import", "POST", []),
      (e) =>
        e.message === "IMPORT_INVALID" &&
        e.row === 2 &&
        e.detail === "CONFLICT",
    );
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("forbidden request carries current safe access profile for cache invalidation", async () => {
  const original = globalThis.fetch;
  resetCsrf();
  const profile = {
    id: 1,
    scope: "DEPARTMENT",
    departmentId: 2,
    permissions: ["read"],
    menus: [],
  };
  globalThis.fetch = async (url) =>
    url.endsWith("/csrf")
      ? json({ header: "X-CSRF-TOKEN", token: "TEST-csrf" })
      : url.endsWith("/me")
        ? json(profile)
        : json({ code: "OUT_OF_SCOPE" }, 403);
  try {
    await assert.rejects(
      api("/passes/1"),
      (e) => e.message === "OUT_OF_SCOPE" && e.profile.departmentId === 2,
    );
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
