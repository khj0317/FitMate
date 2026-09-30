/**
 * 카카오맵 JavaScript SDK를 필요할 때 한 번만 불러온다 (지도 보기·모임 만들기를 열 때). 장소 검색(services)도 함께.
 * 키는 브라우저에 공개되는 JavaScript 키이고, 카카오 개발자 콘솔에 등록한 도메인에서만 동작한다.
 */
const KEY = import.meta.env.VITE_KAKAO_MAP_KEY as string | undefined

export const kakaoMapAvailable = Boolean(KEY)

let loading: Promise<typeof kakao> | null = null

export function loadKakaoMap(): Promise<typeof kakao> {
  if (!KEY) return Promise.reject(new Error('VITE_KAKAO_MAP_KEY가 없어요'))
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(KEY)}&libraries=services&autoload=false`
    script.async = true
    const fail = () => {
      loading = null // 네트워크 문제였으면 다음에 다시 시도할 수 있게
      script.remove()
      reject(new Error('카카오맵을 불러오지 못했어요'))
    }
    // 등록하지 않은 도메인이면 스크립트 대신 오류 JSON이 와서, 불러오기는 성공해도 kakao 객체가 없다
    script.onload = () => (window.kakao?.maps ? window.kakao.maps.load(() => resolve(window.kakao)) : fail())
    script.onerror = fail
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
      getLat(): number
      getLng(): number
    }
    class LatLngBounds {
      extend(latlng: LatLng): void
    }
    class Map {
      constructor(container: HTMLElement, options: { center: LatLng; level: number })
      setBounds(bounds: LatLngBounds, paddingTop?: number, paddingRight?: number, paddingBottom?: number, paddingLeft?: number): void
      addControl(control: ZoomControl, position: ControlPosition): void
      relayout(): void
      getCenter(): LatLng
      panTo(latlng: LatLng): void
      setLevel(level: number): void
    }
    class ZoomControl {}
    enum ControlPosition {
      RIGHT,
    }
    class CustomOverlay {
      constructor(options: { position: LatLng; content: HTMLElement; yAnchor?: number; zIndex?: number; clickable?: boolean })
      setMap(map: Map | null): void
      setPosition(position: LatLng): void
    }
    namespace event {
      function addListener(target: Map, type: 'click', handler: (event: { latLng: LatLng }) => void): void
      function removeListener(target: Map, type: 'click', handler: (event: { latLng: LatLng }) => void): void
    }
    namespace services {
      enum Status {
        OK = 'OK',
        ZERO_RESULT = 'ZERO_RESULT',
        ERROR = 'ERROR',
      }
      interface PlaceResult {
        id: string
        place_name: string
        category_group_name: string
        address_name: string
        road_address_name: string
        x: string
        y: string
      }
      class Places {
        keywordSearch(
          keyword: string,
          callback: (data: PlaceResult[], status: Status) => void,
          options?: { location?: LatLng; sort?: 'accuracy' | 'distance'; size?: number },
        ): void
      }
      interface AddressResult {
        address: { address_name: string } | null
        road_address: { address_name: string; building_name: string } | null
      }
      class Geocoder {
        coord2Address(longitude: number, latitude: number, callback: (result: AddressResult[], status: Status) => void): void
      }
    }
  }
}
