import type { components } from '@/api/schema'
import { getAccessToken } from '@/auth/oidc'
import { env } from '@/env'

type Accepted = components['schemas']['JobAccepted']

/** Uploads a PDF with progress (fetch cannot report upload progress, XMLHttpRequest can). */
export function uploadPdf(collection: string, file: File, onProgress: (percent: number) => void, signal?: AbortSignal): Promise<Accepted> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${env.apiUrl}/api/knowledge/collections/${encodeURIComponent(collection)}/documents`)
    const token = getAccessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)) }
    xhr.onload = () => {
      let body: unknown = null
      try { body = JSON.parse(xhr.responseText) } catch { /* not JSON */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(body as Accepted)
      else reject(new UploadError(xhr.status, (body as { detail?: string; type?: string } | null) ?? {}))
    }
    xhr.onerror = () => reject(new UploadError(0, { detail: 'The upload could not reach the server.' }))
    xhr.onabort = () => reject(new DOMException('Aborted', 'AbortError'))
    signal?.addEventListener('abort', () => xhr.abort())
    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}

export class UploadError extends Error {
  constructor(readonly status: number, readonly problem: { detail?: string; type?: string }) {
    super(problem.detail ?? (status === 413 ? 'The file is too large.' : status === 403 ? 'You are not allowed to upload.' : status === 503 ? 'The AI service is not available right now. Try again in a minute.' : 'The upload failed.'))
  }
}
