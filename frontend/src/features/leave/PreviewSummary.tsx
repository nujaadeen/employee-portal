import type { components } from '@/api/schema'
import { plural } from '@/lib/format'
import { SummaryList } from '@/ui/SummaryList'

type Preview = components['schemas']['Preview']

/** Live numbers under the request form: working days, balance after, overlaps. */
export function PreviewSummary({ preview, department }: { preview?: Preview; department?: string | null }) {
  const overlaps = preview?.overlaps ?? []
  return (
    <SummaryList label="Request summary" rows={[
      { label: 'Working days', value: preview ? String(preview.workingDays) : '–' },
      { label: 'Balance after', value: preview ? (preview.balanceAfter == null ? 'No limit' : <strong>{plural(preview.balanceAfter, 'day')}</strong>) : '–' },
      { label: 'Overlaps', value: !preview ? '–' : overlaps.length ? overlaps.join(', ') : `Nobody${department ? ` on ${department}` : ''}` },
    ]} />
  )
}
