export type Theme = 'dark' | 'light'
const KEY = 'baracel-theme'

export function getStoredTheme(): Theme {
  try {
    return window.localStorage.getItem(KEY) === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}

export function applyTheme(theme: Theme) {
  document.documentElement.setAttribute('data-theme', theme)
  try {
    window.localStorage.setItem(KEY, theme)
  } catch {
    /* storage can be blocked; the theme still applies for this visit */
  }
}

export function initTheme() {
  document.documentElement.setAttribute('data-theme', getStoredTheme())
}
