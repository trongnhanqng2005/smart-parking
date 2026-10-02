import {
  ArrowRight,
  CarFront,
  createIcons,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
  UserRound,
  Waypoints,
} from "lucide";

const icons = {
  ArrowRight,
  CarFront,
  Eye,
  EyeOff,
  LockKeyhole,
  ShieldCheck,
  UserRound,
  Waypoints,
};
createIcons({ icons });

const form = document.querySelector("[data-login-form]");
const password = form.querySelector('[name="password"]');
const passwordToggle = form.querySelector("[data-password-toggle]");
const [showPasswordIcon, hidePasswordIcon] = passwordToggle.querySelectorAll("svg");
const passwordToggleLabel = passwordToggle.querySelector("[data-password-toggle-label]");
const feedback = form.querySelector("[data-login-feedback]");
const submitButton = form.querySelector("[data-login-submit]");
const submitLabel = submitButton.querySelector("[data-login-submit-label]");
const credentialFields = form.querySelectorAll('[name="username"], [name="password"]');
let pending = false;

hidePasswordIcon.setAttribute("hidden", "");

passwordToggle.addEventListener("click", () => {
  const passwordVisible = password.type === "password";
  password.type = passwordVisible ? "text" : "password";
  showPasswordIcon.toggleAttribute("hidden", passwordVisible);
  hidePasswordIcon.toggleAttribute("hidden", !passwordVisible);

  const label = passwordVisible ? "Ẩn mật khẩu" : "Hiện mật khẩu";
  passwordToggle.setAttribute("aria-label", label);
  passwordToggle.setAttribute("aria-pressed", String(passwordVisible));
  passwordToggleLabel.textContent = label;
});

function showFeedback(message, state = "error") {
  feedback.textContent = message;
  const invalidCredentials = state === "invalid";
  feedback.setAttribute("role", invalidCredentials ? "alert" : "status");
  feedback.setAttribute("aria-live", invalidCredentials ? "assertive" : "polite");
  for (const field of credentialFields) {
    if (invalidCredentials) {
      field.setAttribute("aria-invalid", "true");
    } else {
      field.removeAttribute("aria-invalid");
    }
  }
  if (message && state !== "pending") feedback.focus();
}

if (new URLSearchParams(window.location.search).get("passwordChanged") === "1") {
  feedback.textContent = "Mật khẩu đã được thay đổi. Vui lòng đăng nhập bằng mật khẩu mới.";
  feedback.dataset.state = "success";
}

form.addEventListener("input", () => showFeedback("", "idle"));

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (pending) return;

  pending = true;
  form.setAttribute("aria-busy", "true");
  submitButton.disabled = true;
  submitLabel.textContent = "Đang xác thực…";
  showFeedback("Đang xác thực thông tin đăng nhập…", "pending");

  try {
    const response = await window.fetch(form.action, {
      method: form.method,
      body: new FormData(form),
      credentials: "same-origin",
    });

    if (response.status === 204) {
      window.location.assign(form.dataset.homeUrl);
      return;
    }

    if (response.status === 401) {
      showFeedback("Không thể đăng nhập. Vui lòng kiểm tra lại thông tin và thử lại.", "invalid");
    } else if (response.status === 429) {
      showFeedback("Bạn đã thử đăng nhập nhiều lần. Vui lòng thử lại sau.", "throttled");
    } else {
      showFeedback("Không thể hoàn tất đăng nhập. Vui lòng thử lại sau.");
    }
  } catch {
    showFeedback("Không thể kết nối đến hệ thống. Vui lòng thử lại.");
  } finally {
    pending = false;
    form.removeAttribute("aria-busy");
    submitButton.disabled = false;
    submitLabel.textContent = "Đăng nhập";
  }
});
