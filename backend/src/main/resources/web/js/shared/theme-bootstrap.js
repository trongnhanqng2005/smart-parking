(() => {
  const preference = document.cookie
    .split(";")
    .map((part) => {
      const separator = part.indexOf("=");
      return separator < 0
        ? ["", ""]
        : [part.slice(0, separator).trim(), part.slice(separator + 1).trim()];
    })
    .find(([name]) => name === "smart_parking_theme")?.[1];
  const theme = preference === "light" || preference === "dark"
    ? preference
    : window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

  document.documentElement.dataset.theme = theme;
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor) themeColor.content = theme === "dark" ? "#07101D" : "#EAF2F9";
})();
