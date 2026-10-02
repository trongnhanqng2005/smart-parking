# Smart Parking — Shared Visual System

**Status:** Accepted shared visual rules

**Accepted:** 2026-10-01, after explicit user acceptance of the rendered MANAGEMENT login

**Product:** Vietnamese Smart Parking management Web for internal parking operations

**Source of truth:** The accepted rendered login, its implementation, and Task 9 evidence in `.opencode/workflow.md`. This document records only reusable choices demonstrated there; it does not authorize new product capabilities.

## Visual identity

- Use the approved cool, spatial smart-mobility identity: ice/light-blue surfaces with indigo and cyan-teal signals in Light; midnight/navy surfaces with luminous periwinkle and cyan-teal signals in Dark.
- Smart Parking is identified by the local route-shaped “P” mark at `backend/src/main/resources/web/icons/smart-parking-mark.svg` and the `Smart Parking` wordmark. Use the mark as the product identity in shared shell chrome; do not replace it with a generic parking badge.
- Spatial glass and depth are material/interaction language. A future page does not inherit the login's 3D scene or composition unless its own accepted override calls for them.

## Light and Dark palette

The app preference `smart_parking_theme=light|dark` takes precedence over OS/browser color preference. OS preference is only the initial fallback when there is no valid app choice. Set `color-scheme: only light` or `only dark` to match the explicit app choice where supported.

| Semantic token | Light | Dark |
|---|---|---|
| `--color-background` | `#EAF2F9` | `#07101D` |
| `--color-surface` | `#F8FBFE` | `#0E1B2B` |
| `--color-foreground` | `#122236` | `#F0F6FE` |
| `--color-muted` | `#E0EAF4` | `#17283B` |
| `--color-muted-foreground` | `#506981` | `#B1C3D6` |
| `--color-border` | `#71869B` | `#7189A2` |
| `--color-primary` | `#315DEA` | `#A8BEFF` |
| `--color-accent` | `#008F88` | `#5BE0D0` |
| `--color-error` | `#B32645` | `#FF93A5` |
| `--color-on-primary` | `#FFFFFF` | `#091527` |
| `--color-accent-glow` | `rgb(0 143 136 / 40%)` | `rgb(91 224 208 / 56%)` |
| `--color-glass` | `rgb(248 251 254 / 78%)` | `rgb(12 25 41 / 82%)` |
| `--color-glass-highlight` | `rgb(255 255 255 / 82%)` | `rgb(22 42 63 / 62%)` |
| `--color-glass-edge` | `rgb(255 255 255 / 84%)` | `rgb(214 235 255 / 20%)` |
| `--shadow-glass` | `rgb(40 69 105 / 23%)` | `rgb(0 0 0 / 52%)` |
| `--color-input` | `rgb(255 255 255 / 76%)` | `rgb(7 17 30 / 72%)` |
| `--color-input-border` | `#71869B` | `#7189A2` |

Keep text legible over the composed glass/surface, not just the opaque token swatches. The accepted palette was checked in both themes; recheck contrast if a surface or opacity changes.

## Typography

- Interface stack: `"Segoe UI Variable", "Segoe UI", ui-sans-serif, system-ui, sans-serif` (`--font-sans`).
- Display stack: `"Segoe UI Variable Display", "Segoe UI Variable", "Segoe UI", ui-sans-serif, system-ui, sans-serif` (`--font-display`).
- Use the display stack for primary page headings; use the interface stack for body copy, fields, controls and navigation. Reserve small tracked uppercase text for concise metadata, not paragraphs.
- Keep Vietnamese diacritics clear, copy concise and task-first, and use a visible size/weight hierarchy rather than editorial serif styling. Fonts are system-local; no runtime font CDN is used.

## Spacing rhythm

- Use rem-based, compact spacing with repeated tiers: roughly 8–12px for control gaps, 16px for field/label groups, 24–32px for component regions, and fluid `clamp()` gutters at the shell edge.
- Let page-specific composition set its own larger scene spacing. Avoid evenly oversized gaps or a rigid poster grid.

## Shape and control language

- Shared control radius: `--radius-control: 0.9rem`; shared pill radius: `--radius-pill: 99px`.
- Reserve pill shapes for compact state/context chips and the theme-switch track. Keep panel, field and primary-action radii distinct by hierarchy; do not round every surface identically.
- Shared motion durations: `--motion-fast: 160ms`, `--motion-control: 180ms`, `--motion-action: 170ms`, `--motion-depth: 240ms`.

## Glass, surfaces and depth

- Apply the shared glass fill/highlight/edge/shadow tokens to functional surfaces such as the switch and accepted login surface. The accepted implementation uses a 1.5rem backdrop blur with modest saturation where supported, a fine light-catching edge, and an opaque-enough token fallback.
- Depth comes from layered translucent planes, restrained inset highlights, deliberate z-order and the semantic glass shadow. Do not use isolated heavy shadows as a substitute for spatial layering.
- Keep controls readable and outlined in both themes. If `backdrop-filter` is unavailable, the surface stays legible and usable without it.

## Lucide icons and brand

- Use the locally bundled Lucide set consistently for interface icons; do not mix icon families.
- Decorative Lucide icons next to visible text are hidden from assistive technology. Standalone icon controls need an accessible name and an exposed state where applicable.
- The route-shaped Smart Parking mark is a distinct brand asset, not a generic UI glyph or a placeholder badge. Keep it paired with the wordmark in shell branding.

## Shared theme control and interaction

- The Light/Dark control is a native keyboard-operable button with `aria-pressed`, a visible current-state label, and a switch-style track/knob. Preserve the app-cookie contract and ensure the visual position/label match the exposed state.
- Keep visible `:focus-visible` treatment, field `:focus-within` feedback, and stable control bounds. Hover is supplemental; essential actions and feedback must work by keyboard and touch.
- Motion is brief and feedback-led. The accepted durations above are used for state/hover/depth feedback, not continuous decorative motion.

## Responsive and accessibility constraints

- Use fluid shell gutters and test breakpoint boundaries; preserve readable labels, fields, focus targets and theme state as composition changes.
- Keep both themes intentionally designed. Text contrast target is at least 4.5:1; meaningful non-text control boundaries target at least 3:1 against adjacent surfaces.
- Keep forced-colors enabled. Use system Canvas/Field/Button colors and Highlight focus in forced colors; decorative glass/scene art may disappear, but text and controls remain usable.
- Under `prefers-reduced-motion: reduce`, suppress nonessential movement, transitions and animated depth while retaining immediate focus/pressed feedback.

## Approved anti-patterns

- Warm-mineral/beige/oxide identity, serif editorial hero, Swiss/poster grid and marketing/landing-page login composition.
- Large literal barrier illustration, flat generic form card, generic enterprise/Swiss template, text-only login, Brutalism, Neumorphism, Cyberpunk, dark-only/OLED styling, or a data-dense dashboard aesthetic.
- Fake metrics, availability/status values, social proof, unsupported navigation/actions or future business features.
- Placeholder brand badges, remote font/icon CDNs, low-contrast glass, hover-only actions, and motion without a reduced-motion fallback.

## Page overrides

Future pages inherit these shared tokens and interaction/accessibility constraints, then define their own accepted composition and page-specific tokens in `design-system/smart-parking/pages/<page>.md`. Do not copy the login's spatial scene, right-side form placement or login-only scene tokens into this master.
