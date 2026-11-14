export const env = {
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/baracel',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'portal-web',
}
