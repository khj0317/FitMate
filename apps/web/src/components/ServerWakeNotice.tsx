import { LoaderCircle } from 'lucide-react'
import { useSyncExternalStore } from 'react'
import { slowRequestStore } from '../lib/api'

/**
 * API 응답이 몇 초 넘게 오지 않으면 화면 위쪽에 안내를 띄운다.
 * 무료 서버(Render)는 한동안 접속이 없으면 잠들었다가 첫 요청에 30초~1분 걸려 깨어나기 때문에,
 * 버튼을 눌러도 반응이 없는 것처럼 보이지 않게 기다리는 이유를 알려 준다.
 */
export function ServerWakeNotice() {
  const slow = useSyncExternalStore(slowRequestStore.subscribe, slowRequestStore.isSlow)
  if (!slow) return null

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-x-0 top-3 z-[60] flex justify-center px-4"
    >
      <div className="flex max-w-md animate-fade-up items-start gap-3 rounded-2xl bg-ink-900/95 px-4 py-3 text-white shadow-lift backdrop-blur">
        <LoaderCircle className="mt-0.5 size-5 shrink-0 animate-spin text-brand-400" />
        <div className="text-sm">
          <p className="font-semibold">서버를 깨우는 중이에요</p>
          <p className="mt-0.5 text-white/70">
            한동안 접속이 없으면 서버가 잠들어요. 처음 연결에 최대 1분 정도 걸릴 수 있어요.
          </p>
        </div>
      </div>
    </div>
  )
}
