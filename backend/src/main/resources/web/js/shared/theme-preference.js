const THEME_COOKIE = "smart_parking_theme";
const themeHandlers = new WeakMap();

export function readThemePreference(cookieHeader) {
  for (const part of cookieHeader.split(";")) {
    const separator = part.indexOf("=");
    if (separator < 0 || part.slice(0, separator).trim() !== THEME_COOKIE) continue;

    const value = part.slice(separator + 1).trim();
    return value === "light" || value === "dark" ? value : null;
  }

  return null;
}

export function resolveInitialTheme(cookieHeader, prefersDark) {
  return readThemePreference(cookieHeader) ?? (prefersDark ? "dark" : "light");
}

export function serializeThemeCookie(theme, secure) {
  if (theme !== "light" && theme !== "dark") {
    throw new TypeError("Theme must be light or dark");
  }

  return `${THEME_COOKIE}=${theme}; Path=/; SameSite=Lax${secure ? "; Secure" : ""}`;
}

function synchronizeTheme(document, theme, getComputedStyle) {
  const root = document.documentElement;
  root.dataset.theme = theme;

  const background = getComputedStyle(root).getPropertyValue("--color-background").trim();
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor && background) themeColor.content = background;

  for (const button of document.querySelectorAll("[data-theme-toggle]")) {
    button.setAttribute("aria-pressed", String(theme === "dark"));
    button.setAttribute("aria-label", "Chuyển giao diện sáng/tối");
    const label = button.querySelector("[data-theme-toggle-label]");
    if (label) label.textContent = `Giao diện: ${theme === "dark" ? "Tối" : "Sáng"}`;
  }
}

export function bootstrapTheme(document, window, getComputedStyle) {
  const rootTheme = document.documentElement.dataset.theme;
  const theme = rootTheme === "light" || rootTheme === "dark"
    ? rootTheme
    : resolveInitialTheme(
        document.cookie,
        window.matchMedia("(prefers-color-scheme: dark)").matches,
      );

  synchronizeTheme(document, theme, getComputedStyle);

  if (themeHandlers.has(document)) return;

  const onClick = (event) => {
    if (!event.target?.closest?.("[data-theme-toggle]")) return;

    const nextTheme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    synchronizeTheme(document, nextTheme, getComputedStyle);
    document.cookie = serializeThemeCookie(
      nextTheme,
      window.location.protocol === "https:",
    );
  };

  document.addEventListener("click", onClick);
  themeHandlers.set(document, onClick);
}
