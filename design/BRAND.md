# Baracel UI brand guide (Employee Portal)

The look follows the existing Baracel apps: dark navy, one strong blue for actions, quiet grey text, soft rounded cards. Product name in the UI: **Baracel**. Page title: `Baracel`.

| File | Use |
|---|---|
| `tokens.css` | Every colour, font, radius and spacing value. The only place colours are defined. |
| `logo/baracel-logo-transparent.png` | Logo for dark backgrounds (sidebar, login page). |
| `logo/baracel-logo-dark.png` | Logo on its own navy background (emails, favicon source). |
| `screens/*.png` | The approved sample screens. Match these. |
| `html/*.html` | The same screens as plain HTML, for exact spacing and structure. |

## Rules
1. Dark theme is the default. A light theme exists (`data-theme="light"`), toggled with the moon icon in the sidebar footer and remembered in localStorage.
2. Never hard-code a colour, radius or font size in a component. Use the CSS variables.
3. One primary action per screen, using `--primary-gradient`. Secondary actions are outlined (`--border-strong`, transparent fill). Destructive actions use `--danger` and always confirm.
4. Blue means action or selection. Green and red mean success or failure only. Amber means warning or pending.
5. Calm screens: plenty of spacing, one idea per card, short helper text in `--text-secondary` under the title.

## Layout
- Left sidebar (`--sidebar-width`, `--bg-sidebar`): logo, grouped nav items (20px outline icon + label), user avatar, name, role and theme toggle at the bottom. Selected item: `--primary-soft-bg` with `--primary-soft-text`, `--radius-md`.
- Content on `--bg-page`, 32px padding. On phones the sidebar becomes a drawer.
- Uppercase section labels: `--text-xs`, semibold, `--label-letter-spacing`, `--text-secondary`.

## Components
| Component | Spec |
|---|---|
| Card | `--bg-card`, 1px `--border-default`, `--radius-lg`, 24px padding |
| Summary list | `--bg-inset`, `--radius-md`, rows split by `--border-subtle`, label left in `--text-secondary`, value right |
| Primary button | `--primary-gradient`, white text, `--radius-md`, 44 to 48px high |
| Secondary button | transparent, 1px `--border-strong` |
| Input / select | `--bg-inset` or `--bg-input`, 1px `--border-default`, `--radius-md`, 48px high, focus ring `--focus-ring` |
| Status pill | `--radius-pill`, soft background with strong text of the same status |
| Segmented tabs | pills; selected = `--primary` fill, white text; others outlined |
| Modal | `--bg-card-raised`, `--radius-xl`, one primary button |
| IDs, keys, branches | `--font-mono` on `--bg-inset` |

## Typography
`--font-display` (Space Grotesk) for page titles and big numbers. `--font-body` (Inter) for everything else. `--font-mono` (JetBrains Mono) for IDs, branches and code. Sentence case for titles and buttons.

## Accessibility
Real buttons, links, labels and inputs. Touch targets at least 44px. Text contrast at least 4.5:1, so use `--text-secondary` (not `--text-muted`) for readable small text. State is never shown by colour alone: pair it with text or an icon.
