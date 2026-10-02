import "../../css/app.css";
import { Building2, createIcons, KeyRound, LogOut, ShieldCheck, UserRound } from "lucide";
import { bootstrapTheme } from "./theme-preference.js";

bootstrapTheme(document, window, getComputedStyle);

if (document.body.dataset.shell === "authenticated") {
  const headerActions = document.querySelector(".app-header__actions");
  if (headerActions) createIcons({ icons: { KeyRound, LogOut }, root: headerActions });

  const managementHome = document.querySelector(".management-home__bento");
  if (managementHome) {
    createIcons({ icons: { Building2, ShieldCheck, UserRound }, root: managementHome });
  }

  const logoutForm = document.querySelector("[data-logout-form]");
  if (logoutForm) {
    const button = logoutForm.querySelector("button[type='submit']");
    const label = logoutForm.querySelector("[data-logout-label]");
    const feedback = logoutForm.querySelector("[data-logout-feedback]");
    let pending = false;

    logoutForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (pending) return;

      const csrfToken = logoutForm.querySelector('input[name="_csrf"]')?.value;
      if (!csrfToken) {
        feedback.textContent = "Không thể xác minh yêu cầu. Vui lòng tải lại trang.";
        return;
      }

      pending = true;
      logoutForm.setAttribute("aria-busy", "true");
      button.disabled = true;
      label.textContent = "Đang đăng xuất…";
      feedback.textContent = "";

      try {
        const response = await window.fetch(logoutForm.action, {
          method: logoutForm.method,
          headers: { "X-CSRF-TOKEN": csrfToken },
          credentials: "same-origin",
        });
        if (response.status === 204) {
          window.location.assign(logoutForm.dataset.loginUrl);
          return;
        }
        feedback.textContent = "Không thể đăng xuất. Vui lòng thử lại.";
      } catch {
        feedback.textContent = "Không thể kết nối đến hệ thống. Vui lòng thử lại.";
      } finally {
        pending = false;
        logoutForm.removeAttribute("aria-busy");
        button.disabled = false;
        label.textContent = "Đăng xuất";
      }
    });
  }
}

globalThis.smartParkingWeb = Object.freeze({ createIcons });
