import clsx from 'clsx'
import { useEffect, useRef, useState } from 'react'

const RECENT_KEY = 'fitmate.recentEmojis'
const MAX_RECENT = 16

const CATEGORIES: { key: string; icon: string; label: string; emojis: string[] }[] = [
  {
    key: 'workout',
    icon: '💪',
    label: '운동',
    emojis: ['💪', '🏋️', '🏃', '🏃‍♀️', '🚴', '🧗', '🏊', '🎾', '🏸', '⚽', '🏀', '🧘', '🥾', '🤸', '🏆', '🥇', '🔥', '⏱️', '💦', '🥤', '🥗', '🍌', '👟', '🎯'],
  },
  {
    key: 'face',
    icon: '😀',
    label: '표정',
    emojis: ['😀', '😄', '😁', '😆', '😂', '🤣', '😊', '🙂', '😉', '😍', '🥰', '😘', '😎', '🤩', '🥳', '😅', '🥲', '😢', '😭', '😤', '😡', '🤔', '🙄', '😴', '🤗', '🫡', '😮‍💨', '🤯', '😱', '🫠', '😇', '🤭'],
  },
  {
    key: 'hand',
    icon: '👍',
    label: '손짓',
    emojis: ['👍', '👎', '👏', '🙌', '🙏', '👌', '✌️', '🤞', '🤝', '👋', '🫶', '✊', '👊', '💯', '✅', '❌', '⭐', '❗', '❓', '👀'],
  },
  {
    key: 'heart',
    icon: '❤️',
    label: '하트',
    emojis: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '💖', '💕', '💗', '💓', '✨', '🎉', '🎊', '🌟', '☀️', '🌈'],
  },
  {
    key: 'food',
    icon: '🍗',
    label: '음식',
    emojis: ['🍗', '🍕', '🍔', '🍜', '🍣', '🥩', '🍳', '🥪', '🍱', '🍺', '🍻', '☕', '🧋', '🍦', '🍎', '🍓', '🥑', '🍠'],
  },
]

function readRecent(): string[] {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as string[]
  } catch {
    return []
  }
}

function saveRecent(emoji: string) {
  try {
    const next = [emoji, ...readRecent().filter((e) => e !== emoji)].slice(0, MAX_RECENT)
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    // 저장소를 쓸 수 없어도 이모티콘 입력은 계속 동작한다
  }
}

/** 채팅 입력창 위에 뜨는 이모티콘 선택 창. 바깥을 누르거나 Esc를 누르면 닫힌다. */
export function EmojiPicker({ onSelect, onClose }: { onSelect: (emoji: string) => void; onClose: () => void }) {
  const [recent] = useState(readRecent)
  const [tab, setTab] = useState(() => (recent.length > 0 ? 'recent' : 'workout'))
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) onClose()
    }
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    // 여는 클릭이 바로 닫기로 처리되지 않도록 다음 틱에 등록한다
    const timer = setTimeout(() => document.addEventListener('mousedown', onClick))
    window.addEventListener('keydown', onKey)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('mousedown', onClick)
      window.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const tabs = [
    ...(recent.length > 0 ? [{ key: 'recent', icon: '🕘', label: '최근', emojis: recent }] : []),
    ...CATEGORIES,
  ]
  const current = tabs.find((t) => t.key === tab) ?? tabs[0]

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label="이모티콘 선택"
      className="absolute bottom-full left-0 z-20 mb-2 w-[min(20rem,calc(100vw-1.5rem))] animate-pop overflow-hidden rounded-2xl bg-white shadow-lift ring-1 ring-ink-200"
    >
      <div className="flex border-b border-ink-100 px-1.5 pt-1.5">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            title={t.label}
            aria-label={t.label}
            aria-pressed={t.key === current.key}
            className={clsx(
              'flex-1 cursor-pointer rounded-t-lg border-b-2 py-1.5 text-lg transition-colors',
              t.key === current.key ? 'border-brand-500 bg-brand-50' : 'border-transparent hover:bg-ink-50',
            )}
          >
            {t.icon}
          </button>
        ))}
      </div>
      <p className="px-3 pt-2 text-xs font-semibold text-ink-400">{current.label}</p>
      <div className="grid max-h-52 grid-cols-8 gap-0.5 overflow-y-auto p-2 pt-1 scrollbar-thin">
        {current.emojis.map((emoji) => (
          <button
            key={emoji}
            type="button"
            onClick={() => {
              saveRecent(emoji)
              onSelect(emoji)
            }}
            className="flex aspect-square cursor-pointer items-center justify-center rounded-lg text-2xl transition-transform hover:scale-110 hover:bg-ink-100"
          >
            {emoji}
          </button>
        ))}
      </div>
    </div>
  )
}
