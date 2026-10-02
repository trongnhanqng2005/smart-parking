import {
  ArrowLeft,
  ArrowRight,
  createIcons,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  ShieldCheck,
} from "lucide";
import { validatePasswordChange } from "./password-change-validation.js";

createIcons({
  icons: { ArrowLeft, ArrowRight, Eye, EyeOff, KeyRound, LockKeyhole, ShieldCheck },
  root: document.querySelector(".account-security"),
});

const form = document.querySelector("[data-password-change-form]");
const currentPassword = form.querySelector('[name="current_password"]');
const newPassword = form.querySelector('[name="new_password"]');
const confirmation = form.querySelector('[name="confirmation"]');
const feedback = form.querySelector("[data-password-feedback]");
const submitButton = form.querySelector("[data-password-submit]");
const submitLabel = submitButton.querySelector("[data-password-submit-label]");
const touchedFields = new Set();
let pending = false;

for (const button of form.querySelectorAll("[data-password-toggle]")) {
  const input = document.getElementById(button.getAttribute("aria-controls"));
  const [visibleIcon, hiddenIcon] = button.querySelectorAll("svg");
  hiddenIcon.setAttribute("hidden", "");

  button.addEventListener("click", () => {
    const show = input.type === "password";
    input.type = show ? "text" : "password";
    visibleIcon.toggleAttribute("hidden", show);
    hiddenIcon.toggleAttribute("hidden", !show);
    const label = show ? button.dataset.hideLabel : button.dataset.showLabel;
    button.setAttribute("aria-label", label);
    button.setAttribute("aria-pressed", String(show));
    button.querySelector("[data-password-toggle-label]").textContent = label;
  });
}

function setFeedback(message, state = "idle") {
  feedback.textContent = message;
  feedback.dataset.state = state;
  feedback.setAttribute("role", state === "error" ? "alert" : "status");
  feedback.setAttribute("aria-live", state === "error" ? "assertive" : "polite");
}

function renderFieldErrors(submitAttempt = false) {
  const errors = validatePasswordChange(
    currentPassword.value,
    newPassword.value,
    confirmation.value,
  );
  const displayedErrors = { ...errors };
  if (!submitAttempt) {
    for (const name of Object.keys(displayedErrors)) {
      if (!touchedFields.has(name)) delete displayedErrors[name];
    }
  }

  const messages = {
    newPassword: {
      "minimum-length": "Mật khẩu mới cần ít nhất 10 ký tự Unicode.",
      "maximum-bytes": "Mật khẩu mới không được vượt quá 72 byte UTF-8.",
      "must-differ": "Mật khẩu mới phải khác mật khẩu hiện tại.",
    },
    confirmation: { mismatch: "Hai mật khẩu mới không khớp." },
  };
  const fields = [
    ["new_password", newPassword, displayedErrors.newPassword, messages.newPassword],
    ["confirmation", confirmation, displayedErrors.confirmation, messages.confirmation],
  ];
  for (const [name, input, code, fieldMessages] of fields) {
    const error = form.querySelector(`[data-password-error="${name}"]`);
    error.textContent = code ? fieldMessages[code] : "";
    if (code) input.setAttribute("aria-invalid", "true");
    else input.removeAttribute("aria-invalid");
  }

  return fields.filter(([, , code]) => code).map(([, input]) => input);
}

for (const input of [currentPassword, newPassword, confirmation]) {
  input.addEventListener("blur", () => {
    if (input === newPassword) touchedFields.add("newPassword");
    if (input === confirmation) touchedFields.add("confirmation");
    renderFieldErrors();
  });
  input.addEventListener("input", () => {
    setFeedback("");
    const field = input === newPassword ? "newPassword" : input === confirmation ? "confirmation" : "currentPassword";
    if (touchedFields.has(field)) renderFieldErrors();
  });
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (pending) return;

  const invalidFields = renderFieldErrors(true);
  if (invalidFields.length) {
    setFeedback("Vui lòng kiểm tra các mục được đánh dấu.", "error");
    invalidFields[0].focus();
    return;
  }

  const csrfToken = form.querySelector('input[name="_csrf"]')?.value;
  if (!csrfToken) {
    setFeedback("Không thể xác minh yêu cầu. Vui lòng tải lại trang.", "error");
    return;
  }

  pending = true;
  form.setAttribute("aria-busy", "true");
  submitButton.disabled = true;
  submitLabel.textContent = "Đang cập nhật…";
  setFeedback("Đang cập nhật mật khẩu…", "pending");

  try {
    const response = await window.fetch(form.action, {
      method: form.method,
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({
        current_password: currentPassword.value,
        new_password: newPassword.value,
      }),
      credentials: "same-origin",
    });

    if (response.status === 204) {
      form.reset();
      const login = new URL(form.dataset.loginUrl, window.location.origin);
      login.searchParams.set("passwordChanged", "1");
      window.location.assign(login);
      return;
    }

    if (response.status === 400) {
      setFeedback("Mật khẩu mới không đáp ứng yêu cầu. Vui lòng kiểm tra lại.", "error");
    } else if (response.status === 401) {
      setFeedback("Không thể xác minh mật khẩu hiện tại hoặc phiên đã hết hạn. Vui lòng kiểm tra mật khẩu và thử lại.", "error");
    } else if (response.status === 403) {
      setFeedback("Phiên hiện tại không được phép thực hiện thao tác này.", "error");
    } else {
      setFeedback("Không thể cập nhật mật khẩu. Vui lòng thử lại.", "error");
    }
  } catch {
    setFeedback("Không thể kết nối đến hệ thống. Vui lòng thử lại.", "error");
  } finally {
    pending = false;
    form.removeAttribute("aria-busy");
    submitButton.disabled = false;
    submitLabel.textContent = "Cập nhật mật khẩu";
  }
});
