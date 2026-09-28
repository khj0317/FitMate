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

/** 데모 계정(데모_07)처럼 숫자로 구분되는 이름은 숫자를, 그 외에는 첫 글자를 보여준다. */
function initials(name: string) {
  const stripped = name.replace(/^데모_/, '')
  return /^\d+$/.test(stripped) ? stripped.slice(-2) : stripped.charAt(0).toUpperCase()
}

/** 프로필 사진이 없으면 사용자 ID로 고른 그라데이션 위에 닉네임 첫 글자를 보여준다. */
export function Avatar({
  id,
  name,
  imageUrl,
  size = 'md',
  className,
}: {
  id: number
  name: string
  imageUrl?: string | null
  size?: keyof typeof SIZES
  className?: string
}) {
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
      {initials(name)}
    </div>
  )
}
