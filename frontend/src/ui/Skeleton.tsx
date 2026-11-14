export function Skeleton({ width = '100%', height = 16, label }: { width?: number | string; height?: number | string; label?: string }) {
  return <span className="skeleton" style={{ width, height }} role={label ? 'status' : undefined} aria-label={label} aria-hidden={label ? undefined : true} />
}

export function SkeletonRows({ rows = 4, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div className="stack" role="status" aria-label={label}>
      {Array.from({ length: rows }, (_, i) => <Skeleton key={i} height={20} width={`${90 - i * 7}%`} />)}
    </div>
  )
}
