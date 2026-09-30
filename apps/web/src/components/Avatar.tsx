import clsx from 'clsx'

const GRADIENTS = [
  'from-orange-400 to-rose-500',
  'from-amber-400 to-orange-500',
  'from-emerald-400 to-teal-500',
  'from-sky-400 to-indigo-500',
  'from-fuchsia-400 to-purple-500',
  'from-lime-400 to-emerald-500',
  'from-rose-400 to-pink-500',
  'from-cyan-400 to-blue-500',
]

const SIZES = {
  sm: 'size-9 text-sm',
  md: 'size-12 text-lg',
  lg: 'size-16 text-2xl',
  xl: 'size-24 text-4xl',
}

/** 프로필 사진이 없으면 사용자 ID로 고른 그라데이션 위에 닉네임 첫 글자를 보여준다. */
export function Avatar({
  id,
  name,
  imageUrl,
  size = 'md',
  className,
  online,
}: {
  id: number
  name: string
  imageUrl?: string | null
  size?: keyof typeof SIZES
  className?: string
  /** true면 오른쪽 아래에 초록 점(현재 접속중) */
  online?: boolean
}) {
  if (online) {
    return (
      <span className="relative inline-flex shrink-0">
        <Avatar id={id} name={name} imageUrl={imageUrl} size={size} className={className} />
        <span
          className={clsx(
            'absolute right-0 bottom-0 rounded-full bg-emerald-500 ring-2 ring-white',
            size === 'sm' ? 'size-2.5' : 'size-3.5',
          )}
          aria-label="현재 접속중"
        />
      </span>
    )
  }
  if (imageUrl) {
    return <img src={imageUrl} alt="" className={clsx('shrink-0 rounded-full object-cover', SIZES[size], className)} />
  }
  return (
    <div
      aria-hidden
      className={clsx(
        'flex shrink-0 items-center justify-center rounded-full bg-linear-to-br font-bold text-white',
        GRADIENTS[id % GRADIENTS.length],
        SIZES[size],
        className,
      )}
    >
      {name.charAt(0).toUpperCase()}
    </div>
  )
}
