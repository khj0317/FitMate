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

```bash
# 1. DB, Redis 실행
npm run infra:up

# 2. 백엔드 (http://localhost:8081, Swagger: /swagger-ui.html)
cd backend && ./gradlew bootRun

# 3. 웹 (http://localhost:5173)
npm install
npm run dev:web
```
웹 개발 서버는 `/api`, `/ws` 요청을 백엔드(기본 `http://localhost:8081`)로 프록시합니다.
백엔드 포트가 다르면 `BACKEND_URL=http://localhost:8082 npm run dev:web`처럼 지정하세요.
로그인 화면의 **데모 계정 입력** 버튼(개발 모드 전용)으로 `demo01`에 바로 로그인할 수 있습니다.

## 웹 화면

| 화면 | 내용 |
|---|---|
| 로그인 · 회원가입 | 브랜드 소개 패널 + 폼, 필드별 입력 오류 표시, 가입 후 프로필 설정으로 안내 |
| 운동 메이트 | 매칭 점수 링, 점수 내역(거리·실력·시간·매너), 공통 종목 실력 비교, 종목·반경 필터, 요청 모달 |
| 매칭 요청 | 받은/보낸 요청 탭, 상태 필터, 수락하면 바로 채팅방으로 이동 |
| 채팅 | 실시간 수신, 안 읽은 수 배지, 날짜 구분선, 연속 메시지 묶기, 이전 대화 불러오기 |
| 내 프로필 | 기본 정보, 활동 지역(현재 위치·주요 역), 운동 종목·실력, 요일×시간대 표로 운동 가능 시간 선택 |

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
| GET | `/api/sports` | 운동 종목 목록 | |
| GET | `/api/users/me` | 내 프로필 | ✅ |
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
| POST | `/api/chat-rooms/{id}/read` | 읽음 처리 | ✅ |

**WebSocket (STOMP)**: `ws://localhost:8081/ws`
- 연결: CONNECT 헤더에 `Authorization: Bearer <accessToken>`
- 구독: `/topic/chat-rooms/{roomId}` (채팅방 멤버만 가능), 에러는 `/user/queue/errors`
- 전송: `/app/chat-rooms/{roomId}/messages` ← `{"content": "..."}`

전체 명세는 서버 실행 후 `http://localhost:8081/swagger-ui.html`에서 확인할 수 있습니다.

### 인증 설계
- **액세스 토큰**: Spring Security OAuth2 Resource Server + HS256 JWT. 직접 만든 필터 대신 표준 구현 사용
- **리프레시 토큰**: JWT가 아닌 랜덤 문자열을 Redis에 저장 (TTL 14일)
  - 원문이 아닌 SHA-256 해시를 키로 저장해 Redis가 유출돼도 토큰을 재사용할 수 없음
  - `GETDEL`로 원자적으로 꺼내고 삭제 → 같은 토큰으로 동시에 재발급을 요청해도 한 번만 성공 (테스트로 검증)
- **로그인 실패**: 계정이 없을 때와 비밀번호가 틀릴 때 같은 에러를 줘서 가입 여부를 노출하지 않음
- **중복 가입**: 사전 검사 + DB 유니크 제약 이중 방어. 같은 이메일 동시 가입 10건 중 1건만 성공 (테스트로 검증)

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

- 계정: `demo01@fitmate.com` ~ `demo30@fitmate.com`, 비밀번호 `password123`
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
