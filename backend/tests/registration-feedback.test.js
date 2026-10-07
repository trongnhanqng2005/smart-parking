import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { bindRegistrationFeedback } from "../src/main/resources/web/js/shared/registration-feedback.js";

function createForm() {
  const formAttributes = new Map();
  let submitHandler;
  const button = {
    dataset: { pendingLabel: "Đang lưu…" },
    disabled: false,
    textContent: "Lưu thay đổi",
  };
  const status = { hidden: true, textContent: "" };
  const form = {
    dataset: { pendingMessage: "Đang lưu, vui lòng đợi." },
    addEventListener(name, handler) {
      if (name === "submit") submitHandler = handler;
    },
    querySelector(selector) {
      if (selector === "button[type='submit']") return button;
      if (selector === "[data-pending-status]") return status;
      return null;
    },
    setAttribute(name, value) {
      formAttributes.set(name, value);
    },
  };

  return {
    button,
    form,
    formAttributes,
    get submitHandler() {
      return submitHandler;
    },
    status,
  };
}

test("mutation feedback exposes pending state and blocks duplicate submits without retrying", () => {
  const fixture = createForm();
  const document = {
    querySelectorAll: () => [fixture.form],
    querySelector: () => null,
  };

  bindRegistrationFeedback(document);

  let prevented = false;
  fixture.submitHandler({ preventDefault: () => { prevented = true; } });
  assert.equal(prevented, false, "the first submission follows the native form POST");
  assert.equal(fixture.formAttributes.get("aria-busy"), "true");
  assert.equal(fixture.button.disabled, true);
  assert.equal(fixture.button.textContent, "Đang lưu…");
  assert.equal(fixture.status.hidden, false);
  assert.equal(fixture.status.textContent, "Đang lưu, vui lòng đợi.");

  fixture.submitHandler({ preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true, "a repeated submit is canceled rather than retried");
});

test("registration error feedback receives keyboard focus after the server response", () => {
  let focused = false;
  const errorSummary = { focus: () => { focused = true; } };
  bindRegistrationFeedback({
    querySelectorAll: () => [],
    querySelector: (selector) => selector === ".resident-registration [data-focus-on-load]"
      ? errorSummary
      : null,
  });

  assert.equal(focused, true);
});

test("NV01 mutation forms expose pending states and focusable error summaries", async () => {
  const html = await readFile(
    new URL("../src/main/resources/templates/app/resident-registration.html", import.meta.url),
    "utf8",
  );
  const mutationForms = (html.match(/<form\b[^>]*>/g) ?? [])
    .filter((form) => form.includes("data-mutation-form"));
  const errors = html.match(/<div\b[^>]*resident-registration__feedback--error[^>]*role="alert"[^>]*>/g) ?? [];

  assert.equal(mutationForms.length, 6);
  assert.ok(mutationForms.every((form) => form.includes("data-pending-message")));
  assert.ok(errors.length > 0);
  assert.ok(errors.every((error) => error.includes('tabindex="-1"') && error.includes("data-focus-on-load")));
});

test("NV01 forms use theme-aware select styling and never call management REST APIs", async () => {
  const [template, css, applicationJavascript, feedbackJavascript] = await Promise.all([
    readFile(new URL("../src/main/resources/templates/app/resident-registration.html", import.meta.url), "utf8"),
    readFile(new URL("../src/main/resources/web/css/app.css", import.meta.url), "utf8"),
    readFile(new URL("../src/main/resources/web/js/shared/app.js", import.meta.url), "utf8"),
    readFile(new URL("../src/main/resources/web/js/shared/registration-feedback.js", import.meta.url), "utf8"),
  ]);

  assert.match(template, /<select\b/);
  assert.match(css, /\.resident-registration__form[^{}]*select/);
  assert.doesNotMatch(`${applicationJavascript}\n${feedbackJavascript}`, /\/api\/management\//);
});
