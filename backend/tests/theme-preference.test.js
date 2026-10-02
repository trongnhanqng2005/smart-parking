import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { runInNewContext } from "node:vm";
import {
  bootstrapTheme,
  readThemePreference,
  resolveInitialTheme,
  serializeThemeCookie,
} from "../src/main/resources/web/js/shared/theme-preference.js";

function createBrowserState({ cookie = "", prefersDark = false, protocol = "https:" } = {}) {
  let currentCookie = cookie;
  const themeLabel = { textContent: "" };
  const buttonAttributes = new Map();
  const themeButton = {
    setAttribute(name, value) {
      buttonAttributes.set(name, value);
    },
    querySelector(selector) {
      return selector === "[data-theme-toggle-label]" ? themeLabel : null;
    },
  };
  const themeColor = { content: "#EAF2F9" };
  const listeners = new Map();
  const document = {
    documentElement: { dataset: {}, style: {} },
    addEventListener(name, listener) {
      listeners.set(name, listener);
    },
    querySelector(selector) {
      return selector === 'meta[name="theme-color"]' ? themeColor : null;
    },
    querySelectorAll(selector) {
      return selector === "[data-theme-toggle]" ? [themeButton] : [];
    },
  };
  Object.defineProperty(document, "cookie", {
    get: () => currentCookie,
    set: (value) => {
      currentCookie = value;
    },
  });
  const window = {
    location: { protocol },
    matchMedia: () => ({ matches: prefersDark }),
  };
  const getComputedStyle = (element) => ({
    getPropertyValue: (name) => {
      if (name !== "--color-background") return "";
       return element.dataset.theme === "dark" ? "#07101D" : "#EAF2F9";
    },
  });

  return {
    buttonAttributes,
    document,
    getComputedStyle,
    listeners,
    themeButton,
    themeColor,
    themeLabel,
    window,
    get cookie() {
      return currentCookie;
    },
  };
}

test("reads only the approved light/dark UI preference cookie", () => {
  assert.equal(readThemePreference("session_id=opaque; smart_parking_theme=dark"), "dark");
  assert.equal(readThemePreference("smart_parking_theme=light; other=value"), "light");
  assert.equal(readThemePreference("smart_parking_theme=system"), null);
  assert.equal(readThemePreference("smart_parking_theme_extra=dark"), null);
});

test("explicit app choice overrides OS preference; OS supplies the initial fallback", () => {
  assert.equal(resolveInitialTheme("smart_parking_theme=light", true), "light");
  assert.equal(resolveInitialTheme("smart_parking_theme=dark", false), "dark");
  assert.equal(resolveInitialTheme("", true), "dark");
  assert.equal(resolveInitialTheme("", false), "light");
});

test("writes only the UI cookie with the approved path and SameSite policy", () => {
  assert.equal(
    serializeThemeCookie("dark", true),
    "smart_parking_theme=dark; Path=/; SameSite=Lax; Secure",
  );
  assert.equal(
    serializeThemeCookie("light", false),
    "smart_parking_theme=light; Path=/; SameSite=Lax",
  );
});

test("bootstrap synchronizes theme marker, theme-color and accessible toggle state", () => {
  const browser = createBrowserState({ cookie: "smart_parking_theme=dark" });

  bootstrapTheme(browser.document, browser.window, browser.getComputedStyle);

  assert.equal(browser.document.documentElement.dataset.theme, "dark");
  assert.equal(browser.themeColor.content, "#07101D");
  assert.equal(browser.buttonAttributes.get("aria-pressed"), "true");
  assert.equal(browser.buttonAttributes.get("aria-label"), "Chuyển giao diện sáng/tối");
  assert.equal(browser.themeLabel.textContent, "Giao diện: Tối");

  browser.listeners.get("click")({ target: { closest: () => browser.themeButton } });

  assert.equal(browser.document.documentElement.dataset.theme, "light");
  assert.equal(browser.themeColor.content, "#EAF2F9");
  assert.equal(browser.buttonAttributes.get("aria-pressed"), "false");
  assert.equal(browser.themeLabel.textContent, "Giao diện: Sáng");
  assert.equal(browser.cookie, "smart_parking_theme=light; Path=/; SameSite=Lax; Secure");
});

test("theme tokens include explicit schemes and preserve forced-colors and reduced-motion", async () => {
  const css = await readFile(
    new URL("../src/main/resources/web/css/app.css", import.meta.url),
    "utf8",
  );

  assert.match(css, /:root\[data-theme="light"\][^{]*\{[^}]*color-scheme:\s*only light/s);
  assert.match(css, /:root\[data-theme="dark"\][^{]*\{[^}]*color-scheme:\s*only dark/s);
  const lightTokens = css.match(/:root\[data-theme="light"\]\s*\{([^}]*)\}/s)?.[1];
  const darkTokens = css.match(/:root\[data-theme="dark"\]\s*\{([^}]*)\}/s)?.[1];
  assert.match(lightTokens ?? "", /--color-background:\s*#EAF2F9/);
  assert.match(lightTokens ?? "", /--color-primary:\s*#315DEA/);
  assert.match(darkTokens ?? "", /--color-background:\s*#07101D/);
  assert.match(darkTokens ?? "", /--color-primary:\s*#A8BEFF/);
  assert.match(css, /--color-glass:/);
  assert.match(css, /--color-input:/);
  assert.match(css, /--radius-control:/);
  const publicLight = css.match(/:root\[data-theme="light"\]:has\(body\[data-shell="public"\]\)\s*\{([^}]*)\}/s)?.[1];
  const publicDark = css.match(/:root\[data-theme="dark"\]:has\(body\[data-shell="public"\]\)\s*\{([^}]*)\}/s)?.[1];
  assert.match(publicLight ?? "", /--scene-grid:/);
  assert.match(publicDark ?? "", /--scene-grid:/);
  assert.doesNotMatch(publicLight ?? "", /--color-(?:background|primary|foreground|glass):/);
  assert.doesNotMatch(publicDark ?? "", /--color-(?:background|primary|foreground|glass):/);
  assert.match(css, /@media\s*\(forced-colors:\s*active\)/);
  assert.doesNotMatch(css, /forced-color-adjust\s*:\s*none/);
  assert.match(css, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
});

test("early bootstrap accepts only an exact app theme cookie before stylesheet loading", async () => {
  const bootstrap = await readFile(
    new URL("../src/main/resources/web/js/shared/theme-bootstrap.js", import.meta.url),
    "utf8",
  );

  for (const [cookie, prefersDark, expected] of [
    ["session=opaque; smart_parking_theme=dark", false, "dark"],
    ["smart_parking_theme=light", true, "light"],
    ["smart_parking_theme=dark=invalid", false, "light"],
    ["smart_parking_theme=system", true, "dark"],
  ]) {
    const root = { dataset: {} };
    const themeColor = { content: "#EAF2F9" };
    runInNewContext(bootstrap, {
      document: {
        cookie,
        documentElement: root,
        querySelector: () => themeColor,
      },
      window: { matchMedia: () => ({ matches: prefersDark }) },
    });
    assert.equal(root.dataset.theme, expected);
    assert.equal(themeColor.content, expected === "dark" ? "#07101D" : "#EAF2F9");
  }
});

test("public and authenticated shells reuse the early shared theme and asset head", async () => {
  const templates = new URL("../src/main/resources/templates/", import.meta.url);
  const [shared, publicLayout, authenticatedLayout] = await Promise.all([
    readFile(new URL("fragments/shared.html", templates), "utf8"),
    readFile(new URL("layouts/public.html", templates), "utf8"),
    readFile(new URL("layouts/authenticated.html", templates), "utf8"),
  ]);

  assert.match(publicLayout, /fragments\/shared :: head\(/);
  assert.match(authenticatedLayout, /fragments\/shared :: head\(/);

  for (const layout of [publicLayout, authenticatedLayout]) {
    assert.match(layout, /fragments\/shared :: header/);
    assert.match(layout, /<main\b/);
    assert.doesNotMatch(layout, /<nav\b/);
  }

  const bootstrapIndex = shared.indexOf("/assets/theme-bootstrap.js");
  const stylesheetIndex = shared.indexOf("/assets/app.css");
  const applicationIndex = shared.indexOf("/assets/app.js");
  assert.ok(bootstrapIndex >= 0 && bootstrapIndex < stylesheetIndex);
  assert.ok(stylesheetIndex < applicationIndex);
  assert.match(shared, /data-theme-toggle/);
  assert.match(shared, /name="theme-color"/);
  assert.match(shared, /class="app-header"/);
  assert.match(shared, /class="app-brand__mark"/);
  assert.match(shared, /assets\/smart-parking-mark\.svg/);
});
