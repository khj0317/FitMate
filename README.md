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

## 로컬 데모 데이터
`./gradlew bootRun`으로 실행하면 `local` 프로필이 켜지고, 처음 한 번 성수역 주변 8km 안에 데모 사용자 30명이 생성됩니다.

- 계정: `demo01@fitmate.com` ~ `demo30@fitmate.com`, 비밀번호 `password123`
- `demo01`은 성수역에 있고 헬스·러닝을 합니다. 이 계정으로 로그인해서 `GET /api/matching/recommendations`를 호출해 보세요.
- 배포 환경(jar 실행)에서는 생성되지 않습니다.

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
