import { useId } from 'react'
import type { ReactNode, SelectHTMLAttributes } from 'react'

type Props = SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string | null; hint?: ReactNode; options: { value: string; label: string }[]; placeholder?: string }

export function Select({ label, error, hint, options, placeholder, id, className = '', ...rest }: Props) {
  const auto = useId()
  const fid = id ?? auto
  return (
    <div className={`field ${className}`.trim()}>
      <label htmlFor={fid}>{label}</label>
      <select id={fid} className="field__control" aria-invalid={error ? true : undefined} aria-describedby={error ? `${fid}-err` : undefined} {...rest}>
        {placeholder ? <option value="">{placeholder}</option> : null}
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      {error ? <span id={`${fid}-err`} className="field__error" role="alert">{error}</span> : hint ? <span className="field__hint">{hint}</span> : null}
    </div>
  )
}
