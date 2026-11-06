import type { InputHTMLAttributes } from 'react'

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
}

export function FormField({ label, hint, id, ...inputProps }: FormFieldProps) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} {...inputProps} />
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  )
}
