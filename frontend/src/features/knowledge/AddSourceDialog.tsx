import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CircleCheck, FileText, Upload } from 'lucide-react'
import { useRef, useState } from 'react'
import { api, ApiError, unwrap } from '@/api/client'
import type { CollectionAdmin } from '@/api/types'
import { Button } from '@/ui/Button'
import { Field, TextArea } from '@/ui/Field'
import { Modal } from '@/ui/Modal'
import { SegmentedTabs } from '@/ui/SegmentedTabs'
import { Select } from '@/ui/Select'
import { useToast } from '@/ui/Toast'
import { uploadPdf, UploadError } from './upload'
import { driveFolderId, MAX_UPLOAD_MB, parseUrls, validateDriveFolder, validatePdf } from './validation'

type Tab = 'pdf' | 'drive' | 'web'
type Item = { file: File; progress: number; state: 'ready' | 'uploading' | 'sent' | 'error'; error?: string }

export function AddSourceDialog({ open, onClose, collections, initial }: { open: boolean; onClose: () => void; collections: CollectionAdmin[]; initial?: string }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('pdf')
  const [code, setCode] = useState(initial ?? collections[0]?.code ?? '')
  const target = collections.find((c) => c.code === code) ?? collections[0]
  const done = () => { void qc.invalidateQueries({ queryKey: ['knowledge'] }) }
  const finish = () => { setTab('pdf'); onClose() }

  return (
    <Modal open={open} title="Add source" onClose={finish} description="Documents are read, split and indexed in the background. You can watch the progress under Indexing activity."
      actions={<Button onClick={finish}>Close</Button>}>
      <Select label="Add to collection" value={target?.code ?? ''} onChange={(e) => setCode(e.target.value)} options={collections.map((c) => ({ value: c.code, label: c.name }))} />
      <SegmentedTabs label="Source type" value={tab} onChange={setTab} tabs={[{ value: 'pdf', label: 'Upload PDF' }, { value: 'drive', label: 'Google Drive folder' }, { value: 'web', label: 'Website' }]} />
      <div role="tabpanel" style={{ marginTop: 'var(--space-4)' }}>
        {target ? (tab === 'pdf' ? <PdfTab code={target.code} onSent={() => { done(); toast.success('Upload received. Indexing has started.') }} />
          : tab === 'drive' ? <DriveTab code={target.code} onSent={() => { done(); toast.success('Drive sync started.'); finish() }} />
            : <WebTab code={target.code} onSent={() => { done(); toast.success('Website indexing started.'); finish() }} />) : <p className="muted">Create a collection first.</p>}
      </div>
    </Modal>
  )
}

function PdfTab({ code, onSent }: { code: string; onSent: () => void }) {
  const [items, setItems] = useState<Item[]>([])
  const [over, setOver] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const add = (files: FileList | File[]) => {
    const next: Item[] = Array.from(files).map((file) => { const err = validatePdf(file); return { file, progress: 0, state: err ? 'error' : 'ready', error: err ?? undefined } })
    setItems((cur) => [...cur, ...next])
  }
  const patch = (f: File, p: Partial<Item>) => setItems((cur) => cur.map((i) => (i.file === f ? { ...i, ...p } : i)))
  const sending = items.some((i) => i.state === 'uploading')
  const readyItems = items.filter((i) => i.state === 'ready')
  const send = async () => {
    for (const it of readyItems) {
      patch(it.file, { state: 'uploading', progress: 0 })
      try {
        await uploadPdf(code, it.file, (p) => patch(it.file, { progress: p }))
        patch(it.file, { state: 'sent', progress: 100 })
        onSent()
      } catch (e) {
        patch(it.file, { state: 'error', error: e instanceof UploadError ? e.message : 'The upload failed.' })
      }
    }
  }
  return (
    <div>
      <div className={`drop${over ? ' drop--over' : ''}`} onDragOver={(e) => { e.preventDefault(); setOver(true) }} onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); add(e.dataTransfer.files) }}>
        <Upload size={24} aria-hidden="true" />
        <p>Drag PDF files here, or</p>
        <Button onClick={() => input.current?.click()}>Choose files</Button>
        <input ref={input} type="file" accept="application/pdf,.pdf" multiple hidden aria-label="PDF files" onChange={(e) => { if (e.target.files) add(e.target.files); e.target.value = '' }} />
        <p className="muted">PDF only, up to {MAX_UPLOAD_MB} MB each.</p>
      </div>
      {items.length ? (
        <ul className="plain-list upload-list" aria-label="Files">
          {items.map((i, n) => (
            <li key={n} className="upload-item">
              <FileText size={18} aria-hidden="true" />
              <div className="grow">
                <div>{i.file.name} <span className="muted">· {(i.file.size / 1024).toFixed(0)} KB</span></div>
                {i.state === 'uploading' ? <progress max={100} value={i.progress} aria-label={`Uploading ${i.file.name}`} /> : null}
                {i.state === 'sent' ? <span className="muted"><CircleCheck size={14} aria-hidden="true" /> Sent. Indexing in the background.</span> : null}
                {i.state === 'error' ? <span className="field__error" role="alert">{i.error}</span> : null}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="row" style={{ marginTop: 'var(--space-4)' }}>
        <Button variant="primary" icon={<Upload size={18} />} loading={sending} disabled={readyItems.length === 0} onClick={() => void send()}>Upload {readyItems.length > 1 ? `${readyItems.length} files` : 'file'}</Button>
      </div>
    </div>
  )
}

function DriveTab({ code, onSent }: { code: string; onSent: () => void }) {
  const [folder, setFolder] = useState('')
  const [recursive, setRecursive] = useState(true)
  const [touched, setTouched] = useState(false)
  const error = touched ? validateDriveFolder(folder) : null
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/knowledge/collections/{code}/sources/drive', { params: { path: { code } }, body: { folderId: driveFolderId(folder), recursive } })),
    onSuccess: onSent,
  })
  return (
    <form onSubmit={(e) => { e.preventDefault(); setTouched(true); if (!validateDriveFolder(folder)) m.mutate() }} noValidate>
      <Field label="Folder ID" value={folder} error={error} onChange={(e) => setFolder(e.target.value)}
        hint={<>Open the folder in Google Drive. The ID is the last part of the address, after <span className="mono">/folders/</span>. You can also paste the whole address. The folder must be shared with the portal&rsquo;s Google service account (ask an administrator for its address).</>} />
      <label className="check"><input type="checkbox" checked={recursive} onChange={(e) => setRecursive(e.target.checked)} /> Include sub-folders</label>
      <p className="muted">PDFs and Google Docs are indexed. Only files that changed since the last sync are read again.</p>
      {m.error ? <p className="field__error" role="alert">{m.error instanceof ApiError ? m.error.message : 'Could not start the sync.'}</p> : null}
      <div className="row"><Button type="submit" variant="primary" loading={m.isPending}>Start sync</Button></div>
    </form>
  )
}

function WebTab({ code, onSent }: { code: string; onSent: () => void }) {
  const [text, setText] = useState('')
  const [follow, setFollow] = useState(false)
  const [touched, setTouched] = useState(false)
  const parsed = parseUrls(text)
  const m = useMutation({
    mutationFn: () => unwrap(api.POST('/api/knowledge/collections/{code}/sources/web', { params: { path: { code } }, body: { urls: parsed.urls, followLinks: follow } })),
    onSuccess: onSent,
  })
  return (
    <form onSubmit={(e) => { e.preventDefault(); setTouched(true); if (!parsed.error) m.mutate() }} noValidate>
      <TextArea label="Web addresses" rows={4} value={text} placeholder="https://baracel.example/products" error={touched ? parsed.error : null}
        hint="One address per line, all on the same website. Pages that robots.txt does not allow are skipped." onChange={(e) => setText(e.target.value)} />
      <label className="check"><input type="checkbox" checked={follow} onChange={(e) => setFollow(e.target.checked)} /> Also read pages linked from these (same website, up to 30 pages)</label>
      {m.error ? <p className="field__error" role="alert">{m.error instanceof ApiError ? m.error.message : 'Could not start indexing.'}</p> : null}
      <div className="row"><Button type="submit" variant="primary" loading={m.isPending}>Index website</Button></div>
    </form>
  )
}
