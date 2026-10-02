import assert from "node:assert/strict";
import test from "node:test";
import { validatePasswordChange } from "../src/main/resources/web/js/pages/password-change-validation.js";

test("counts Unicode code points for the minimum new-password length", () => {
  assert.deepEqual(
    validatePasswordChange("current-password", "🔐".repeat(9), "🔐".repeat(9)),
    { newPassword: "minimum-length" },
  );
});

test("enforces the 72-byte UTF-8 limit and accepts the exact boundary", () => {
  assert.deepEqual(
    validatePasswordChange("current-password", "🔐".repeat(18), "🔐".repeat(18)),
    {},
  );
  assert.deepEqual(
    validatePasswordChange("current-password", "🔐".repeat(19), "🔐".repeat(19)),
    { newPassword: "maximum-bytes" },
  );
});

test("requires a different new password and matching confirmation", () => {
  assert.deepEqual(
    validatePasswordChange("same-password", "same-password", "different-password"),
    { newPassword: "must-differ", confirmation: "mismatch" },
  );
  assert.deepEqual(
    validatePasswordChange("current-password", "replacement-password", "other-password"),
    { confirmation: "mismatch" },
  );
});
