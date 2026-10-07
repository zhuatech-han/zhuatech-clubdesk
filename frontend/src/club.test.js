// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import {
  localDay,
  eligiblePass,
  canCancel,
  parseCsv,
  parseMembers,
} from "./club.js";
const pass = {
  id: 1,
  departmentId: 1,
  state: "ACTIVE",
  mode: "CREDITS",
  startsOn: "2026-10-06",
  endsOn: "2026-10-07",
  credits: 3,
  held: 1,
  used: 1,
};
const session = {
  departmentId: 1,
  startsAt: "2026-10-06T15:30:00Z",
  endsAt: "2026-10-06T16:00:00Z",
  cancelHours: 1,
};
test("branch dates differ from browser timezone and retain midnight coverage", () => {
  assert.equal(localDay("2026-10-06T23:30:00Z", "Asia/Shanghai"), "2026-10-07");
  assert.equal(
    localDay("2026-10-06T23:30:00Z", "America/New_York"),
    "2026-10-06",
  );
  assert.equal(
    eligiblePass({ ...pass, endsOn: "2026-10-06" }, session, "Asia/Shanghai"),
    true,
  );
  assert.equal(
    eligiblePass(
      { ...pass, endsOn: "2026-10-06" },
      { ...session, endsAt: "2026-10-06T16:00:01Z" },
      "Asia/Shanghai",
    ),
    false,
  );
});
test("eligibility respects held credits, future freezes, branch and unpaid cards", () => {
  assert.equal(eligiblePass(pass, session, "Asia/Shanghai"), true);
  assert.equal(
    eligiblePass({ ...pass, held: 2 }, session, "Asia/Shanghai"),
    false,
  );
  assert.equal(
    eligiblePass(
      { ...pass, mode: "PERIOD", credits: 0, held: 50, used: 30 },
      session,
      "Asia/Shanghai",
    ),
    true,
  );
  assert.equal(
    eligiblePass({ ...pass, state: "DRAFT" }, session, "Asia/Shanghai"),
    false,
  );
  assert.equal(
    eligiblePass({ ...pass, departmentId: 2 }, session, "Asia/Shanghai"),
    false,
  );
  assert.equal(
    eligiblePass(
      { ...pass, freezeStarted: "2026-10-06", freezeUntil: "2026-10-07" },
      session,
      "Asia/Shanghai",
    ),
    false,
  );
  assert.equal(
    eligiblePass(
      { ...pass, freezeStarted: "2026-10-05", freezeUntil: "2026-10-06" },
      session,
      "Asia/Shanghai",
    ),
    true,
  );
});
test("member cancellation closes exactly at the snapshot deadline", () => {
  const cutoff = Date.parse(session.startsAt) - 3600000;
  assert.equal(canCancel(session, cutoff - 1), true);
  assert.equal(canCancel(session, cutoff), false);
});
test("member CSV preserves commas, escaped quotes, BOM and multiline notes", () => {
  const text =
    '\uFEFFcode,name,departmentId,contactNote,enabled\r\nM001,"TEST, ""Studio""",1,"two\nlines",true\r\n';
  const rows = parseMembers(text, [{ id: 1 }]);
  assert.equal(rows[0].name, 'TEST, "Studio"');
  assert.equal(rows[0].contactNote, "two\nlines");
  assert.equal(rows[0].enabled, true);
  assert.throws(() => parseCsv('code,"unfinished'), /INVALID_CSV/);
});
test("CSV reports exact record and rejects duplicate, branch and non-boolean fields", () => {
  const header = "code,name,departmentId,contactNote,enabled\n";
  assert.throws(
    () =>
      parseMembers(header + "M1,TEST,1,,true\nM1,TEST,1,,true", [{ id: 1 }]),
    (e) => e.message === "DUPLICATE_MEMBER" && e.row === 2,
  );
  assert.throws(
    () => parseMembers(header + "M1,TEST,2,,true", [{ id: 1 }]),
    (e) => e.message === "OUT_OF_SCOPE" && e.row === 1,
  );
  assert.throws(
    () => parseMembers(header + "M1,TEST,1,,yes", [{ id: 1 }]),
    (e) => e.message === "CSV_BOOLEAN" && e.row === 1,
  );
});

test("malformed CSV does not silently append text after a closing quote", () => {
  assert.throws(() => parseCsv('a,"TEST"junk,c'), /INVALID_CSV/);
  assert.deepEqual(parseCsv('a,"TEST"  ,c'), [["a", "TEST", "c"]]);
});
