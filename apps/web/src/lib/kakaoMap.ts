/**
 * 카카오맵 JavaScript SDK를 필요할 때 한 번만 불러온다 (지도 보기를 누를 때).
 * 키는 브라우저에 공개되는 JavaScript 키이고, 카카오 개발자 콘솔에 등록한 도메인에서만 동작한다.
 */
const KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

export const kakaoMapAvailable = Boolean(KEY)

let loading: Promise<typeof kakao> | null = null

export function loadKakaoMap(): Promise<typeof kakao> {
  if (!KEY) return Promise.reject(new Error('VITE_KAKAO_MAP_KEY가 없어요'))
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(KEY)}&autoload=false`
    script.async = true
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
    script.onerror = () => {
      loading = null // 네트워크 문제였으면 다음에 다시 시도할 수 있게
      script.remove()
      reject(new Error('카카오맵을 불러오지 못했어요'))
    }
    document.head.appendChild(script)
  })
  return loading
}

/* 이 프로젝트에서 쓰는 카카오맵 API만 최소한으로 타입을 적어 둔다 */
declare global {
  interface Window {
    kakao: typeof kakao
  }
  namespace kakao.maps {
    function load(callback: () => void): void
    class LatLng {
      constructor(latitude: number, longitude: number)
    }
    class LatLngBounds {
      extend(latlng: LatLng): void
    }
    class Map {
      constructor(container: HTMLElement, options: { center: LatLng; level: number })
      setBounds(bounds: LatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void
      addControl(control: ZoomControl, position: ControlPosition): void
      relayout(): void
    }
    class ZoomControl {}
    enum ControlPosition {
      RIGHT,
    }
    class CustomOverlay {
      constructor(options: { position: LatLng; content: HTMLElement; yAnchor?: number; zIndex?: number; clickable?: boolean })
      setMap(map: Map | null): void
    }
    namespace event {
      function addListener(target: Map, type: 'click', handler: () => void): void
    }
  }
}
