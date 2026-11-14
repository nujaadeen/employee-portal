type Props = { checked: boolean; onChange: (v: boolean) => void; label: string; disabled?: boolean }

export function Toggle({ checked, onChange, label, disabled }: Props) {
  return (
    <span className="toggle-hit">
      <button type="button" role="switch" aria-checked={checked} aria-label={label} className="toggle" disabled={disabled} onClick={() => onChange(!checked)} />
    </span>
  )
}
