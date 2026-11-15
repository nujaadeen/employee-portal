export type ReturnMessage = { tone: 'success' | 'danger' | 'info'; text: string } | null

const REASONS: Record<string, string> = {
  'invalid-state': 'The sign-in link expired or was already used. Start the connection again.',
  'no-organization': 'You do not administer a LinkedIn company page, so nothing was connected.',
  'linkedin-error': 'LinkedIn did not complete the connection. Try again in a moment.',
  'no-code': 'LinkedIn did not send an approval. Try again.',
}

/** What the /accounts page says when LinkedIn sends the browser back (?linkedin=connected|cancelled|error&target=&reason=). */
export function returnMessage(params: URLSearchParams): ReturnMessage {
  const flag = params.get('linkedin')
  if (!flag) return null
  const target = params.get('target') === 'company' ? 'Company LinkedIn page' : 'Your LinkedIn account'
  if (flag === 'connected') return { tone: 'success', text: `${target} is connected.` }
  if (flag === 'cancelled') return { tone: 'info', text: 'You cancelled at LinkedIn. Nothing was connected.' }
  return { tone: 'danger', text: REASONS[params.get('reason') ?? ''] ?? 'The connection did not work. Try again.' }
}
