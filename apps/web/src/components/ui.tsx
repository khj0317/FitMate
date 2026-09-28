import clsx from 'clsx'
import { LoaderCircle } from 'lucide-react'
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary:
    'bg-brand-500 text-white shadow-[0_6px_16px_-6px_rgb(255_90_31/0.6)] hover:bg-brand-600 active:bg-brand-700',
  secondary: 'bg-white text-ink-800 ring-1 ring-ink-200 hover:bg-ink-50 hover:ring-ink-300',
  ghost: 'text-ink-600 hover:bg-ink-100 hover:text-ink-900',
  danger: 'bg-white text-red-600 ring-1 ring-red-200 hover:bg-red-50',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  className,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
}) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-xl font-semibold transition-all',
        'disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none',
        size === 'sm' && 'h-9 px-3.5 text-sm',
        size === 'md' && 'h-11 px-5 text-[15px]',
        size === 'lg' && 'h-13 px-6 text-base',
        BUTTON_STYLES[variant],
        className,
      )}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" />}
      {children}
    </button>
  )
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={clsx('rounded-3xl bg-white shadow-card ring-1 ring-ink-100', className)}>{children}</div>
}

export function Chip({
  active = false,
  onClick,
  children,
  className,
}: {
  active?: boolean
  onClick?: () => void
  children: ReactNode
  className?: string
}) {
  const Component = onClick ? 'button' : 'span'
  return (
    <Component
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={clsx(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors',
        onClick && 'cursor-pointer',
        active ? 'bg-ink-900 text-white' : 'bg-white text-ink-700 ring-1 ring-ink-200 hover:ring-ink-300',
        className,
      )}
    >
      {children}
    </Component>
  )
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'brand' | 'green' | 'red'; children: ReactNode }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-semibold',
        tone === 'neutral' && 'bg-ink-100 text-ink-600',
        tone === 'brand' && 'bg-brand-50 text-brand-700',
        tone === 'green' && 'bg-emerald-50 text-emerald-700',
        tone === 'red' && 'bg-red-50 text-red-600',
      )}
    >
      {children}
    </span>
  )
}

export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-semibold text-ink-700">{label}</span>
      {children}
      {error ? (
        <span className="mt-1.5 block text-xs text-red-600">{error}</span>
      ) : (
        hint && <span className="mt-1.5 block text-xs text-ink-400">{hint}</span>
      )}
    </label>
  )
}

const INPUT_STYLE =
  'w-full rounded-xl bg-ink-50 px-4 text-[15px] text-ink-900 ring-1 ring-ink-200 transition placeholder:text-ink-400 focus:bg-white focus:ring-2 focus:ring-brand-400 focus:outline-none'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={clsx(INPUT_STYLE, 'h-12', className)} />
}

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={clsx(INPUT_STYLE, 'resize-none py-3', className)} />
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  size = 'md',
}: {
  options: { value: T; label: string }[]
  value: T | null
  onChange: (value: T) => void
  size?: 'sm' | 'md'
}) {
  return (
    <div className="inline-flex rounded-xl bg-ink-100 p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={clsx(
            'cursor-pointer rounded-lg font-semibold transition-all',
            size === 'md' ? 'px-4 py-2 text-sm' : 'px-2.5 py-1 text-xs',
            value === option.value ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-500 hover:text-ink-800',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <LoaderCircle className={clsx('animate-spin text-brand-500', className ?? 'size-6')} />
}

export function PageLoader() {
  return (
    <div className="flex h-64 items-center justify-center">
      <Spinner className="size-8" />
    </div>
  )
}

export function EmptyState({
  emoji,
  title,
  description,
  action,
}: {
  emoji: string
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center px-6 py-16 text-center">
      <div className="mb-4 flex size-20 items-center justify-center rounded-full bg-linear-to-br from-brand-50 to-brand-100 text-4xl">
        {emoji}
      </div>
      <p className="text-lg font-bold text-ink-900">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-sm leading-relaxed text-ink-500">{description}</p>}
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}

export function PageHeader({ title, description, action }: { title: string; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-ink-900 md:text-[28px]">{title}</h1>
        {description && <p className="mt-1 text-[15px] text-ink-500">{description}</p>}
      </div>
      {action}
    </div>
  )
}
