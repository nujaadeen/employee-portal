import type { SVGProps } from 'react'

/** Outline brand marks (lucide has no brand icons). 20px, currentColor, like the screens. */
function Svg(props: SVGProps<SVGSVGElement>) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ flex: 'none' }} {...props} />
}
export const GoogleIcon = () => (
  <Svg>
    <path d="M21 12.2c0-.7-.1-1.3-.2-1.9H12v3.6h5.1a4.4 4.4 0 0 1-1.9 2.9v2.4h3.1c1.8-1.7 2.7-4.1 2.7-7z" />
    <path d="M12 21c2.6 0 4.7-.9 6.3-2.3l-3.1-2.4c-.9.6-2 .9-3.2.9-2.5 0-4.5-1.7-5.3-3.9H3.5v2.5A9.5 9.5 0 0 0 12 21z" />
    <path d="M6.7 13.3a5.7 5.7 0 0 1 0-3.6V7.2H3.5a9.5 9.5 0 0 0 0 8.6z" />
    <path d="M12 6.8c1.4 0 2.7.5 3.7 1.4l2.7-2.7A9.5 9.5 0 0 0 3.5 7.2l3.2 2.5C7.5 8.5 9.5 6.8 12 6.8z" />
  </Svg>
)
export const GithubIcon = () => (
  <Svg>
    <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
    <path d="M9 18c-4.51 2-5-2-7-2" />
  </Svg>
)
export const FacebookIcon = () => (
  <Svg><path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z" /></Svg>
)
export const InstagramIcon = () => (
  <Svg><rect x="2" y="2" width="20" height="20" rx="5" ry="5" /><path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z" /><path d="M17.5 6.5h.01" /></Svg>
)
export const LinkedinIcon = () => (
  <Svg><path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" /><rect x="2" y="9" width="4" height="12" /><circle cx="4" cy="4" r="2" /></Svg>
)
