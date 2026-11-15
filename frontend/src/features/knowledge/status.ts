import type { ActivityItem, CollectionAdmin } from '@/api/types'
import type { Tone } from '@/ui/StatusPill'

export const RUNNING = ['QUEUED', 'RUNNING']
export const isRunning = (status: string) => RUNNING.includes(status)

export type StatusView = { label: string; tone: Tone }

export function collectionStatus(c: Pick<CollectionAdmin, 'status' | 'failedDocuments'>): StatusView {
  if (c.failedDocuments > 0) return { label: `${c.failedDocuments} ${c.failedDocuments === 1 ? 'file' : 'files'} failed`, tone: 'danger' }
  switch (c.status) {
    case 'READY': return { label: 'Ready', tone: 'success' }
    case 'INDEXING': return { label: 'Indexing', tone: 'info' }
    case 'FAILED': return { label: 'Failed', tone: 'danger' }
    case 'EMPTY': return { label: 'No documents', tone: 'neutral' }
    default: return { label: 'Unknown', tone: 'neutral' }
  }
}

export function activityStatus(a: Pick<ActivityItem, 'status' | 'progress'>): StatusView {
  switch (a.status) {
    case 'QUEUED': return { label: 'Waiting to index', tone: 'info' }
    case 'RUNNING': return { label: a.progress > 0 ? `Indexing ${a.progress}%` : 'Indexing', tone: 'info' }
    case 'DONE': return { label: 'Ready', tone: 'success' }
    case 'PARTIAL': return { label: 'Some files failed', tone: 'warning' }
    case 'FAILED': return { label: 'Failed', tone: 'danger' }
    default: return { label: 'Status unknown', tone: 'neutral' }
  }
}

const SOURCE_LABEL: Record<string, string> = { PDF: 'PDF', DRIVE: 'Drive', WEB: 'website' }
export const sourceTypes = (types: string[] | undefined) => (types ?? []).map((t) => SOURCE_LABEL[t] ?? t).join(', ') || '–'

/** Cards at the top: count, documents, last sync, needs attention. */
export function summarize(list: CollectionAdmin[]) {
  const syncs = list.map((c) => c.lastSync).filter((x): x is string => !!x).sort()
  return {
    collections: list.length,
    documents: list.reduce((n, c) => n + c.documents, 0),
    lastSync: syncs.length ? syncs[syncs.length - 1]! : null,
    needsAttention: list.filter((c) => c.failedDocuments > 0 || c.status === 'FAILED').length,
    failedFiles: list.reduce((n, c) => n + c.failedDocuments, 0),
  }
}
