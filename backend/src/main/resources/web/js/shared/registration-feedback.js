export function bindRegistrationFeedback(document) {
  for (const form of document.querySelectorAll("[data-mutation-form]")) {
    let pending = false;
    const button = form.querySelector("button[type='submit']");
    const status = form.querySelector("[data-pending-status]");

    form.addEventListener("submit", (event) => {
      if (pending) {
        event.preventDefault();
        return;
      }

      pending = true;
      form.setAttribute("aria-busy", "true");
      if (button) {
        button.disabled = true;
        button.textContent = button.dataset.pendingLabel || "Đang gửi…";
      }
      if (status) {
        status.hidden = false;
        status.textContent = form.dataset.pendingMessage || "Đang gửi yêu cầu…";
      }
    });
  }

  document.querySelector(".resident-registration [data-focus-on-load]")?.focus();
}
