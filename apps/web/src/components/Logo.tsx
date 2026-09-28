import clsx from 'clsx'

export function Logo({ light = false, className }: { light?: boolean; className?: string }) {
  return (
    <div className={clsx('flex items-center gap-2', className)}>
      <img src="/favicon.svg" alt="" className="size-8" />
      <span className={clsx('text-xl font-extrabold tracking-tight', light ? 'text-white' : 'text-ink-900')}>
        Fit<span className={light ? 'text-brand-200' : 'text-brand-500'}>Mate</span>
      </span>
    </div>
  )
}
