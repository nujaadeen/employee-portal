import { useId } from 'react'
import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'

type Common = { label: string; hint?: ReactNode; error?: string | null }

export function Field({ label, hint, error, id, className = '', ...rest }: Common & InputHTMLAttributes<HTMLInputElement>) {
  const auto = useId()
  const fid = id ?? auto
  return (
    <div className={`field ${className}`.trim()}>
      <label htmlFor={fid}>{label}</label>
      <input id={fid} className="field__control" aria-invalid={error ? true : undefined} aria-describedby={error ? `${fid}-err` : hint ? `${fid}-hint` : undefined} {...rest} />
      {error ? <span id={`${fid}-err`} className="field__error" role="alert">{error}</span> : hint ? <span id={`${fid}-hint`} className="field__hint">{hint}</span> : null}
    </div>
  )
}

export function TextArea({ label, hint, error, id, className = '', ...rest }: Common & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const auto = useId()
  const fid = id ?? auto
  return (
    <div className={`field ${className}`.trim()}>
      <label htmlFor={fid}>{label}</label>
      <textarea id={fid} className="field__control" aria-invalid={error ? true : undefined} aria-describedby={error ? `${fid}-err` : undefined} {...rest} />
      {error ? <span id={`${fid}-err`} className="field__error" role="alert">{error}</span> : hint ? <span className="field__hint">{hint}</span> : null}
    </div>
  )
}
