import type { ButtonHTMLAttributes, ReactNode } from 'react'

interface PrimaryButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  loading?: boolean
}

export function PrimaryButton({ children, loading = false, disabled, ...props }: PrimaryButtonProps) {
  return (
    <button className="button-primary" disabled={disabled || loading} {...props}>
      {loading ? <><span className="spinner" aria-hidden="true" />Please wait...</> : children}
    </button>
  )
}
