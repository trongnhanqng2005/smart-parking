# MANAGEMENT Login — Page Override

**Status:** Accepted rendered page; visual acceptance recorded 2026-10-01

**Inherits:** [`../MASTER.md`](../MASTER.md)

**Surface:** Public Smart Parking MANAGEMENT authentication page

**Source:** Accepted login implementation and browser evidence recorded in `.opencode/workflow.md` under Build Task 9.

This override contains login-only composition and interaction decisions. Shared palette, typography, brand, glass, icon, theme-control and accessibility rules remain in `MASTER.md`.

## Composition

- On wide screens, place the compact glass login panel on the right and the spatial mobility scene on the left. Keep both within the same spatial field; this is a product sign-in surface, not a marketing hero plus separate floating form.
- The stage is capped at 96rem and uses viewport-height sizing. At standard desktop sizes the complete header and panel fit without vertical scrolling.
- At widths up to 60rem, center the login panel and move the scene behind it. At 40rem and below, retain a single-column reading order, reduce scene prominence, and let the glass panel remain the focus. At 23rem and below, tighten header and panel gutters. Short desktop viewports use the compact vertical spacing variant.

## Spatial mobility scene

- Login-only CSS tokens:
  - Light: `--scene-grid: rgb(48 91 146 / 12%)`; `--scene-deep: rgb(39 86 156 / 20%)`; `--scene-glass: rgb(255 255 255 / 42%)`; `--theme-halo-one: rgb(49 93 234 / 15%)`; `--theme-halo-two: rgb(0 143 136 / 14%)`.
  - Dark: `--scene-grid: rgb(146 183 223 / 10%)`; `--scene-deep: rgb(54 91 169 / 34%)`; `--scene-glass: rgb(166 196 255 / 15%)`; `--theme-halo-one: rgb(60 97 231 / 22%)`; `--theme-halo-two: rgb(0 190 177 / 16%)`.
  - `--scene-glow` inherits the shared `--color-accent-glow` for each theme.
- The scene is built from three offset glass slabs, two elliptical paths, restrained grid lines, light-trail routes and raised Waypoints/CarFront Lucide nodes. It is CSS-isometric: 1100px perspective, volume rotated 58° on X and −34° on Z, with the slab layers offset in depth. It depicts no live parking count or operational status.
- The upper/lower/foundation slab placements, elliptical paths, route positions, scene opacity and all `--scene-*`/`--theme-halo-*` values are login-only. Future pages may choose a different spatial visualization.

## Login panel and form hierarchy

1. Wordmark and route-shaped Smart Parking mark, with a concise MANAGEMENT context chip.
2. Metadata eyebrow: `CỔNG ĐIỀU HÀNH / WEB`.
3. Primary heading: `Đăng nhập`.
4. Supporting copy: `Dành cho tài khoản Ban quản lý Smart Parking.`
5. Username, then password; each has a visible Vietnamese label, required marker and leading Lucide icon. Keep `username`/`current-password` autocomplete and paste/password-manager support.
6. Password visibility is an accessible button with Eye/EyeOff icons and pressed/name state. Feedback remains in the form's live region below fields; the primary `Đăng nhập` action follows it.

The login panel is up to 29.5rem wide with a 1.6rem radius. Its header, content and fields retain a clear internal hierarchy; controls use the inherited 0.9rem control radius. No recovery, registration, remember-me, fake status, or auxiliary marketing content is part of this page.

## Login-specific motion

- With a hover-capable pointer and no reduced-motion preference, the spatial field lifts/scales subtly, the panel lifts 2px, and the submit arrow shifts 2px on hover.
- Fields use the shared focus-within treatment; the password icon and submit feedback follow their accessible control states.
- The reduced-motion shared rule removes the nonessential transforms/transitions and leaves the scene/form in a stable, readable state.

## Responsive evidence

The accepted Chromium render was checked at 320, 375, 768, 1024 and 1440px widths, including 1440×900, 1024×600 and 375×667 viewports. The tested layouts had no horizontal overflow; the tested 1440×900, 1024×600, 375×667 and 320×700 views fit vertically. Browser coverage beyond the available Chromium runtime is recorded as unverified in `.opencode/workflow.md`.
