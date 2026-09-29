# FitMate

동네 운동 친구를 찾는 매칭, 채팅, 커뮤니티 서비스 (웹 + 앱)

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 4, Spring Data JPA, Spring Security, WebSocket(STOMP), Flyway |
| DB / Cache | PostgreSQL + PostGIS, Redis |
| Web | React, Vite, TypeScript |
| App | React Native (Expo) — 예정 |
| 배포 | Vercel(웹), Railway/Render(API), Supabase(DB), Upstash(Redis), Expo EAS(앱) |

## 프로젝트 구조

```
fitmate/
├─ backend/            Spring Boot API 서버
├─ apps/
│  ├─ web/             React 웹
│  └─ mobile/          React Native 앱 (예정)
├─ packages/           웹/앱 공유 코드 (OpenAPI 클라이언트 등, 예정)
└─ docker-compose.yml  로컬 PostgreSQL(PostGIS) + Redis
```

## 로컬 실행

Docker Desktop을 켠 뒤, 처음 한 번만 `npm install`을 실행하고 아래 명령 하나로 전부 켭니다.

```bash
npm run dev
```
DB·Redis·Mailpit 컨테이너 → 백엔드(http://localhost:8081) → 웹(http://localhost:5173)이 한 터미널에서 차례로 뜨고, 로그는 `[api]`, `[web]`으로 구분됩니다. `Ctrl + C` 한 번으로 모두 종료됩니다.
Windows에서는 루트의 **`dev.cmd`를 더블클릭**해도 됩니다.

| 명령 | 설명 |
|---|---|
| `npm run dev` | 전체 실행 (컨테이너 + 백엔드 + 웹) |
| `npm run dev:api` / `npm run dev:web` | 백엔드만 / 웹만 실행 |
| `npm run test:api` | 백엔드 테스트 |
| `npm run infra:up` / `npm run infra:down` | 컨테이너 켜기 / 끄기 |

> Windows PowerShell에서 `npm` 실행이 막히면 `npm.cmd run dev`를 사용하세요.

- Swagger: http://localhost:8081/swagger-ui.html · 메일함(Mailpit): http://localhost:8025

웹 개발 서버는 `/api`, `/ws` 요청을 백엔드(기본 `http://localhost:8081`)로 프록시합니다.
백엔드 포트가 다르면 `BACKEND_URL=http://localhost:8082 npm run dev:web`처럼 지정하세요.
로그인 화면의 **데모 계정 입력** 버튼(개발 모드 전용)으로 `demo01`에 바로 로그인할 수 있습니다.

## 웹 화면

| 화면 | 내용 |
|---|---|
| 로그인 · 회원가입 | 브랜드 소개 패널 + 폼, 필드별 입력 오류 표시, 가입 후 프로필 설정으로 안내 |
| 운동 메이트 | 매칭 점수 링, 점수 내역(거리·실력·시간·매너), 공통 종목 실력 비교, 종목·반경 필터, 요청 모달 |
| 매칭 요청 | 받은/보낸 요청 탭, 상태 필터, 수락하면 바로 채팅방으로 이동 |
| 채팅 | 실시간 수신, 안 읽은 수 배지, 날짜 구분선, 연속 메시지 묶기, 이전 대화 불러오기, 이모티콘(최근 사용, 이모티콘만 보내면 크게 표시), 사진(버튼·붙여넣기, 크게 보기), 읽음 표시 "1", 상대 접속 상태 |
| 내 프로필 | 차단 목록·해제, 회원 탈퇴, 프로필 사진(즉시 저장), 기본 정보, 활동 지역(전국 자동완성 검색), 운동 종목·실력, 요일×시간대 표로 운동 가능 시간 선택 |

- **기술**: React 19, Vite, TypeScript, Tailwind CSS v4, TanStack Query, React Router, STOMP.js, Pretendard
- **반응형**: 데스크톱은 사이드바, 모바일은 하단 탭바와 하단 시트 모달
- **토큰 자동 재발급**: 여러 요청이 동시에 401을 받아도 재발급은 한 번만 수행 (서버 리프레시 토큰이 1회용이라 중복 재발급 시 로그아웃되는 문제 방지)
- **실시간 연결**: 로그인하면 내 모든 채팅방을 구독해서 다른 화면에서도 안 읽은 수 배지가 실시간 갱신. 연결이 끊기면 REST로 전송
- **한글 입력**: IME 조합 중 Enter는 전송하지 않아 마지막 글자가 중복 전송되는 문제 방지

## 테스트

### 자동 테스트
```bash
cd backend && ./gradlew test
```
Testcontainers로 실제 PostGIS와 Redis 컨테이너를 띄워서 통합 테스트를 실행합니다. Docker가 실행 중이어야 합니다.
결과 리포트: `backend/build/reports/tests/test/index.html`

GitHub에 푸시하면 GitHub Actions에서도 같은 테스트가 자동으로 실행됩니다 (저장소의 **Actions** 탭).

### Swagger로 직접 테스트하기
1. Docker Desktop 실행 후 `npm run infra:up`
2. `cd backend && ./gradlew bootRun`
3. 브라우저에서 http://localhost:8081/swagger-ui.html 열기
4. **회원가입** `POST /api/auth/signup` → Try it out
   ```json
   {"email": "test@fitmate.com", "password": "password123", "nickname": "테스터"}
   ```
5. **로그인** `POST /api/auth/login` → 응답의 `accessToken` 복사
6. 오른쪽 위 **Authorize** 버튼 → 토큰 붙여넣기 (`Bearer ` 없이 토큰만)
7. 이제 인증이 필요한 API를 호출할 수 있습니다. 예시:
   - `PUT /api/users/me/location`
     ```json
     {"latitude": 37.5445, "longitude": 127.0557, "areaName": "서울 성동구 성수동"}
     ```
   - `PUT /api/users/me/sports` (종목 ID는 `GET /api/sports`에서 확인)
     ```json
     {"sports": [{"sportId": 1, "skillLevel": "BEGINNER"}, {"sportId": 2, "skillLevel": "ADVANCED"}]}
     ```
   - `PUT /api/users/me/available-times`
     ```json
     {"availableTimes": [{"dayOfWeek": "MONDAY", "startTime": "19:00", "endTime": "21:00"}]}
     ```
   - `GET /api/users/me`로 결과 확인

> Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용하세요.

## API

| Method | Path | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | |
| POST | `/api/auth/login` | 로그인 (액세스 30분 / 리프레시 14일) | |
| POST | `/api/auth/refresh` | 토큰 재발급 (리프레시 토큰 교체) | |
| POST | `/api/auth/logout` | 로그아웃 (리프레시 토큰 폐기) | |
| POST | `/api/auth/find-login-id` | 아이디 찾기 (등록한 이메일로 발송) | |
| POST | `/api/auth/password-reset/request` | 비밀번호 재설정 코드 요청 | |
| POST | `/api/auth/password-reset/confirm` | 코드 확인 후 비밀번호 재설정 | |
| GET | `/api/locations/search` | 지역 검색 (자동완성) | |
| GET | `/api/sports` | 운동 종목 목록 | |
| GET | `/api/users/me` | 내 프로필 | ✅ |
| PUT | `/api/users/me` | 프로필 전체 저장 (한 트랜잭션) | ✅ |
| PATCH | `/api/users/me` | 프로필 수정 (보낸 필드만) | ✅ |
| PUT | `/api/users/me/location` | 활동 지역 설정 | ✅ |
| PUT | `/api/users/me/sports` | 운동 종목·실력 설정 | ✅ |
| PUT | `/api/users/me/available-times` | 운동 가능 시간대 설정 | ✅ |
| GET | `/api/users/{userId}` | 다른 사용자 프로필 (이메일·좌표 비공개) | ✅ |
| GET | `/api/matching/recommendations` | 내 주변 운동 친구 추천 (`sportId`, `radiusKm`, `limit`) | ✅ |
| POST | `/api/match-requests` | 매칭 요청 보내기 | ✅ |
| GET | `/api/match-requests/received` · `/sent` | 받은·보낸 요청 목록 (`status`) | ✅ |
| POST | `/api/match-requests/{id}/accept` | 수락 → 1:1 채팅방 생성 | ✅ |
| POST | `/api/match-requests/{id}/reject` · `/cancel` | 거절 · 취소 | ✅ |
| GET | `/api/chat-rooms` | 내 채팅방 목록 (상대방, 마지막 메시지, 안 읽은 수) | ✅ |
| GET | `/api/chat-rooms/{id}/messages` | 메시지 목록 (커서 페이지네이션) | ✅ |
| POST | `/api/chat-rooms/{id}/messages` | 메시지 보내기 (REST) | ✅ |
| POST | `/api/chat-rooms/{id}/images` | 사진 보내기 (multipart) | ✅ |
| POST | `/api/chat-rooms/{id}/read` | 읽음 처리 | ✅ |
| PUT · DELETE | `/api/users/{id}/block` | 차단 · 해제 | ✅ |
| GET | `/api/users/me/blocks` | 내가 차단한 사용자 | ✅ |
| POST | `/api/users/{id}/report` | 신고 (사유, 내용, 함께 차단) | ✅ |
| POST | `/api/users/me/withdrawal` | 회원 탈퇴 (비밀번호 확인) | ✅ |
| GET | `/api/users/presence?userIds=1,2` | 접속 상태 (현재 접속중 / 마지막 접속 시각) | ✅ |
| POST · DELETE | `/api/users/me/profile-image` | 프로필 사진 올리기 · 삭제 (multipart) | ✅ |

**WebSocket (STOMP)**: `ws://localhost:8081/ws`
- 연결: CONNECT 헤더에 `Authorization: Bearer <accessToken>`
- 구독: `/topic/chat-rooms/{roomId}` (메시지), `/topic/chat-rooms/{roomId}/reads` (읽음 위치) — 채팅방 멤버만 가능, 에러는 `/user/queue/errors`
- 전송: `/app/chat-rooms/{roomId}/messages` ← `{"content": "..."}`

전체 명세는 서버 실행 후 `http://localhost:8081/swagger-ui.html`에서 확인할 수 있습니다.

### 인증 설계
- **액세스 토큰**: Spring Security OAuth2 Resource Server + HS256 JWT. 직접 만든 필터 대신 표준 구현 사용
- **리프레시 토큰**: JWT가 아닌 랜덤 문자열을 Redis에 저장 (TTL 14일)
  - 원문이 아닌 SHA-256 해시를 키로 저장해 Redis가 유출돼도 토큰을 재사용할 수 없음
  - `GETDEL`로 원자적으로 꺼내고 삭제 → 같은 토큰으로 동시에 재발급을 요청해도 한 번만 성공 (테스트로 검증)
- **로그인 실패**: 계정이 없을 때와 비밀번호가 틀릴 때 같은 에러를 줘서 가입 여부를 노출하지 않음
- **중복 가입**: 사전 검사 + DB 유니크 제약 이중 방어. 같은 이메일 동시 가입 10건 중 1건만 성공 (테스트로 검증)

### 계정 찾기 설계
- **가입 여부 비노출**: 아이디·이메일이 가입돼 있든 아니든 항상 같은 응답(202)을 주고 메일만 조건부 발송. 메일은 `@Async`로 보내서 응답 시간 차이로도 알 수 없게 함
- **인증 코드**: 6자리 코드를 해시로 Redis에 저장(10분 TTL). `HINCRBY`로 시도 횟수를 원자적으로 세서 5번 틀리면 폐기 (무차별 대입 방지), 성공하면 즉시 삭제 (재사용 방지)
- **재요청 제한**: 같은 대상은 1분에 한 번 (`SET NX EX`)
- **비밀번호 변경 시 모든 기기 로그아웃**: 사용자별 리프레시 토큰 목록(Redis Set)을 관리해 한 번에 폐기
- **로컬 메일 확인**: docker-compose의 Mailpit이 메일을 받아서 http://localhost:8025 에서 볼 수 있음 (실제 발송 안 됨)

### 안전 · 계정 보호
- **추천 제외**: 차단 관계(어느 쪽이든), 이미 매칭된 사람, 대기 중인 요청이 있는 사람을 반경 검색 SQL 안에서 `NOT EXISTS`로 제외
- **차단**: 추천·매칭 요청·채팅에서 서로 차단. 차단 즉시 대기 중인 요청을 한 번의 UPDATE로 취소하고, 차단한 사람의 목록에서는 채팅방을 숨김. 상대에게 차단 사실을 알리지 않음
- **신고**: 사유와 함께 저장, 검토 중 중복 신고는 부분 유니크 인덱스로 차단. 신고하면서 바로 차단 가능
- **회원 탈퇴**: 비밀번호 확인 후 계정과 개인정보를 완전히 삭제(`ON DELETE CASCADE`), 상대방 채팅방의 대화는 남기고 보낸 사람만 비움(`SET NULL`), 신고 기록은 운영 검토용으로 유지, 프로필 사진 파일은 커밋 후 삭제, 모든 기기 로그아웃
- **로그인 시도 제한**: 같은 아이디 5회 / 같은 IP 20회 실패 시 15분 잠금 (Redis `INCR` + TTL). 아이디만 제한하면 아이디를 바꿔 가며 시도하거나 남의 계정을 일부러 잠글 수 있어 IP 제한을 함께 둠. 프록시 뒤에서는 `forward-headers-strategy: native`로 내부 프록시가 붙인 IP만 신뢰해 헤더 위조로 제한을 피할 수 없게 함

### 읽음 표시 · 접속 상태
- **읽음 "1"**: 메시지 목록에 상대의 마지막 읽음 위치를 함께 주고, 상대가 읽거나 답장하면 읽음 위치를 커밋 후 Redis로 발행 → `/reads` 토픽으로 실시간 전달. 읽음 위치가 실제로 앞으로 움직였을 때만 발행
- **접속 상태**: WebSocket 연결 = 접속. Redis 해시에 연결마다 만료 시각을 기록해 **여러 탭**(하나 닫아도 접속중)과 **서버 장애**(heartbeat 30초, 90초 지나면 만료로 보고 정리)를 모두 처리
- **경쟁 상황 수정**: 끊김 처리 순서(연결 삭제 → 마지막 접속 기록)를 반대로 바꿈. 그 사이 조회에서 "오프라인인데 마지막 접속 없음"이 보이던 문제를 WebSocket E2E 테스트가 잡아냄

### 사진 업로드
- **브라우저에서 먼저 축소**: 업로드 전에 긴 변 1600px JPEG로 줄여서 전송량을 줄이고, 휴대폰 사진의 회전(EXIF Orientation)을 이때 적용
- **서버에서 재검증·재인코딩**: 확장자가 아니라 파일 앞부분(매직 넘버)으로 JPEG/PNG 확인 → 픽셀 크기를 먼저 읽어 거대한 이미지는 디코딩 전에 거절(압축 폭탄 방지) → 다시 인코딩하면서 **촬영 위치(GPS) 등 EXIF 메타데이터 제거**
- **프로필 사진**: 가운데를 정사각형으로 잘라 512px. 바꾸거나 지우면 이전 파일은 **커밋 후** 삭제, DB 저장이 실패하면 방금 올린 파일을 **롤백 시** 삭제해서 DB와 저장소가 어긋나지 않음
- **저장소 추상화**: 개발은 로컬 폴더(`/files/**`), 배포는 S3 호환 저장소(Supabase Storage·AWS S3·R2)를 `STORAGE_TYPE`으로 전환. 파일명이 UUID라 1년 `immutable` 캐시
- **테스트**: EXIF 제거·압축 폭탄·파일 정리는 통합 테스트로, S3 구현은 S3Mock 컨테이너로 검증

### 지역 검색
- `KAKAO_REST_API_KEY`가 있으면 카카오 로컬 API(주소 + 키워드 검색), 결과는 Redis에 하루 캐시
- 키가 없거나 외부 API가 실패하면 내장 목록으로 대체: **전국 시 · 시군구 · 읍면동 3,827곳** + 주요 역·명소
  - 통계청 행정동 경계에서 동마다 넓이 가중 무게중심을 계산해 생성 (`backend/scripts/generate_kr_areas.py`)
  - "망원동"처럼 부르는 이름으로도 행정동(망원1동, 망원2동)을 찾도록 숫자·"N가"를 뺀 별칭으로도 검색
  - 결과는 이름 일치 → 주소 일치 순, 같은 순위면 넓은 지역(시 → 구 → 역 → 동) 먼저
- 저장하는 지역명은 동 단위까지만 잘라서(번지 제외) 개인 위치를 남기지 않음

### 매칭 설계
**점수(100점) = 거리 35 + 실력 30 + 운동 시간 25 + 매너 10**

| 항목 | 계산 |
|---|---|
| 거리 | 가까울수록 높음, 검색 반경 끝이면 0점 |
| 실력 | 같은 수준 30 / 한 단계 차이 15 / 두 단계 차이 0 |
| 운동 시간 | 같은 요일에 겹치는 시간 합계, 주 3시간 이상이면 만점 |
| 매너 | 매너 점수 30 이하 0점 ~ 50 이상 만점 |

- **반경 검색**: `ST_DWithin` + GIST 인덱스 (`EXPLAIN`으로 `Index Scan using idx_users_activity_location` 확인)
- **2단계 처리**: DB에서 반경 안 후보를 가까운 순 200명까지 거르고 겹치는 시간까지 계산 → 점수는 애플리케이션에서 계산. 가중치를 바꾸기 쉽고 점수 계산기를 DB 없이 단위 테스트할 수 있음
- **위치 보호**: 거리는 0.5km 단위로 올림해서 보여줘서 여러 지점에서 거리를 재 정확한 위치를 역추적(삼각측량)하기 어렵게 함
- **시간대 버그 수정**: `hibernate.jdbc.time_zone=UTC` 설정 때문에 운동 가능 시각(`time`)이 9시간 밀려 저장되던 문제를 데모 데이터 생성 중 발견. 설정을 제거하고, CI(UTC)에서도 재현되도록 테스트 JVM 시간대를 KST로 고정한 뒤 DB 저장값을 직접 검증하는 회귀 테스트 추가

### 매칭 요청 · 채팅 설계
```
클라이언트 ─STOMP SEND→ 서버 A ─저장(DB)→ 커밋 후 ─PUBLISH→ Redis "chat:messages"
                                                         │
                          ┌──────────────────────────────┴──────────────┐
                        서버 A (SUBSCRIBE)                          서버 B (SUBSCRIBE)
                          └─→ /topic/chat-rooms/{id} 구독자           └─→ 구독자
```
- **Redis Pub/Sub**: 서버가 여러 대여도 다른 서버에 연결된 사용자에게 메시지가 전달됨
- **커밋 후 발행**: `@TransactionalEventListener(AFTER_COMMIT)`로 DB 커밋이 끝난 메시지만 발행 → 롤백된 메시지 전송 방지
- **WebSocket 인증**: 브라우저 WebSocket은 핸드셰이크에 헤더를 못 붙이므로 STOMP CONNECT 프레임에서 JWT 검증, SUBSCRIBE 시 채팅방 멤버 여부 확인
- **요청 처리 동시성**: 수락·거절·취소는 `SELECT ... FOR UPDATE`(비관적 락)로 직렬화. 수락과 취소가 동시에 와도 하나만 성공, 같은 요청을 동시에 5번 수락해도 채팅방은 1개 (테스트로 검증)
- **1:1 채팅방 유일성**: `"작은ID:큰ID"` 키에 유니크 제약 → 요청 방향과 상관없이 두 사람 사이에 방은 하나
- **안 읽은 수**: 멤버별 `last_read_message_id` 이후 상대가 보낸 메시지 수. 읽음 위치는 앞으로만 이동해서 늦게 도착한 요청이 최신 값을 덮어쓰지 않음
- **메시지 페이지네이션**: `id < cursor` + `(room_id, id DESC)` 인덱스 → OFFSET 없이 대화가 길어져도 일정한 속도
- **권한 없는 접근은 404**: 남의 요청·채팅방은 존재 여부도 노출하지 않음

## 로컬 데모 데이터
`./gradlew bootRun`으로 실행하면 `local` 프로필이 켜지고, 처음 한 번 성수역 주변 8km 안에 데모 사용자 30명이 생성됩니다.

- 계정: 아이디 `demo01` ~ `demo30`, 비밀번호 `password123` (이메일 `demo01@fitmate.com` 등록됨)
- `demo01`은 성수역에 있고 헬스·러닝을 합니다. 이 계정으로 로그인해서 `GET /api/matching/recommendations`를 호출해 보세요.
- `demo01` ↔ `demo02`는 이미 매칭되어 대화가 있고, `demo03` → `demo01`로 대기 중인 매칭 요청이 있습니다.
- 배포 환경(jar 실행)에서는 생성되지 않습니다.

### 채팅 테스트 페이지 (로컬 전용)
http://localhost:8081/dev/chat.html
1. 브라우저 창 두 개를 열고 각각 `demo01`, `demo02`로 로그인
2. 채팅방을 선택하고 메시지를 보내면 다른 창에 실시간으로 나타납니다
3. 안 읽은 수 뱃지, 이전 메시지 더 보기도 확인할 수 있습니다

## ERD

```mermaid
erDiagram
    users ||--o{ user_sports : "운동 종목"
    sports ||--o{ user_sports : ""
    users ||--o{ user_available_times : "가능 시간대"
    users ||--o{ match_requests : "보낸 요청"
    users ||--o{ match_requests : "받은 요청"
    users ||--o{ gatherings : "모임 개설"
    gatherings ||--o{ gathering_participants : "참가자"
    users ||--o{ gathering_participants : ""
    gatherings |o--o| chat_rooms : "모임 채팅방"
    chat_rooms ||--o{ chat_room_members : ""
    users ||--o{ chat_room_members : ""
    chat_rooms ||--o{ chat_messages : ""
    users ||--o{ chat_messages : "보낸 메시지"

    users {
        bigint id PK
        varchar email UK
        varchar nickname UK
        geography activity_location "PostGIS POINT"
        smallint search_radius_km
        numeric manner_score
    }
    sports {
        smallint id PK
        varchar code UK
        varchar name
    }
    user_sports {
        bigint user_id PK
        smallint sport_id PK
        varchar skill_level
    }
    user_available_times {
        bigint id PK
        bigint user_id FK
        smallint day_of_week
        time start_time
        time end_time
    }
    match_requests {
        bigint id PK
        bigint requester_id FK
        bigint receiver_id FK
        smallint sport_id FK
        varchar status "PENDING 중복 방지 부분 유니크 인덱스"
    }
    gatherings {
        bigint id PK
        bigint host_id FK
        geography location
        timestamptz starts_at
        smallint capacity
        smallint current_count
        bigint version "낙관적 락"
    }
    gathering_participants {
        bigint gathering_id PK
        bigint user_id PK
        varchar status "JOINED/CANCELED/ATTENDED/NO_SHOW"
    }
    chat_rooms {
        bigint id PK
        varchar type "DIRECT/GATHERING"
        bigint gathering_id FK
    }
    chat_room_members {
        bigint room_id PK
        bigint user_id PK
        bigint last_read_message_id "읽음 처리"
    }
    chat_messages {
        bigint id PK
        bigint room_id FK
        bigint sender_id FK
        varchar content
    }
```

### 설계 포인트
- **위치 기반 검색**: `geography(POINT)` + GIST 인덱스로 `ST_DWithin` 반경 검색
- **중복 매칭 요청 방지**: `status = 'PENDING'` 부분 유니크 인덱스로 동시 요청도 DB에서 차단
- **모임 정원 동시성**: `current_count <= capacity` 체크 제약 + 버전 컬럼(낙관적 락), 이후 Redis 분산 락과 비교 예정
- **채팅 페이지네이션**: `(room_id, id DESC)` 인덱스 기반 커서 페이지네이션
- **읽음 처리**: 멤버별 `last_read_message_id`로 안 읽은 메시지 수 계산

## 데이터 출처

행정구역 검색 목록(`backend/src/main/resources/locations/kr-areas.csv`)은 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)에서 공공누리 제1유형으로 개방한 행정동 경계를 가공한 것이며(가공: vuski/admdongkor, https://github.com/vuski/admdongkor), CC BY 4.0으로 배포됩니다. 이 프로젝트에서는 경계에서 중심 좌표를 계산해 사용합니다.
