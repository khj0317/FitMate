# FitMate 배포 가이드

```
브라우저 ──▶ Vercel (웹, React)
   │
   └──── REST · WebSocket ──▶ Render (API, Spring Boot · Docker, 무료 512MB)
                                 ├──▶ Supabase Postgres + PostGIS (DB)
                                 ├──▶ Supabase Storage (사진, S3 호환)
                                 └──▶ Upstash Redis (토큰 · 실시간 메시지 · 접속 상태 · 요청 제한)
```

모든 서비스를 **싱가포르 리전**으로 맞추면 서버끼리 주고받는 지연이 가장 짧습니다 (Render·Railway의 아시아 리전이 싱가포르). 전부 무료 플랜으로 운영할 수 있습니다.

순서: **① Supabase → ② Upstash → ③ Render(API, 무료) → ④ Vercel(웹) → ⑤ CORS 마무리**

> 비밀번호·키는 각 서비스 대시보드의 환경 변수에만 넣고, 코드나 채팅에 붙여 넣지 마세요.
> 필요한 환경 변수 전체 목록은 [`backend/.env.example`](backend/.env.example), [`apps/web/.env.example`](apps/web/.env.example)에 있습니다.

---

## ① Supabase (DB + 사진 저장소)

1. https://supabase.com 가입 → **New project**
   - Region: **Southeast Asia (Singapore)**
   - Database password: 생성 후 따로 저장 (→ `DB_PASSWORD`)
2. **DB 연결 주소**: 상단 **Connect** 버튼 → **Session pooler** 탭
   - `postgresql://postgres.<ref>:[PASSWORD]@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres` 형태
   - 이것을 아래처럼 나눠서 씁니다
     - `DB_URL` = `jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require`
     - `DB_USERNAME` = `postgres.<ref>`
     - `DB_PASSWORD` = 1에서 만든 비밀번호
   - **Direct connection이 아니라 Session pooler**를 쓰는 이유: Direct는 IPv6 전용이라 Railway에서 접속이 안 될 수 있음. Transaction pooler(6543)는 JDBC Prepared Statement와 맞지 않음
   - PostGIS는 따로 켤 필요 없습니다. 서버가 처음 뜰 때 Flyway 마이그레이션이 `CREATE EXTENSION postgis`까지 실행합니다
3. **사진 저장소**: 왼쪽 **Storage** → **New bucket**
   - 이름 `fitmate`, **Public bucket 켜기** (사진 URL을 로그인 없이 보여 주기 위해. 파일명은 추측할 수 없는 UUID)
4. **S3 키**: **Project Settings → Storage → S3 Connection**
   - Endpoint → `S3_ENDPOINT` (`https://<ref>.supabase.co/storage/v1/s3`)
   - Region → `S3_REGION` (`ap-southeast-1`)
   - **New access key** → Access key ID → `S3_ACCESS_KEY`, Secret → `S3_SECRET_KEY` (한 번만 보여 줌)
   - `S3_PUBLIC_URL` = `https://<ref>.supabase.co/storage/v1/object/public/fitmate`

> 무료 플랜은 1주일 동안 요청이 없으면 프로젝트가 일시 정지됩니다. 대시보드에서 Restore를 누르면 다시 켜집니다.

## ①-2 Brevo (메일 발송, 무료 하루 300통)

Render 무료 플랜은 메일 포트(SMTP)를 막아서, 가입 이메일 인증·비밀번호 찾기 메일은 Brevo API로 보냅니다.
1. https://www.brevo.com 무료 가입
2. **Senders, Domains & Dedicated IPs → Senders → Add a sender**: 이름 `FitMate`, 이메일 = 내 Gmail → 그 Gmail로 온 인증 메일에서 확인
3. **SMTP & API → API Keys → Generate a new API key** → `BREVO_API_KEY`
4. Render 환경 변수: `BREVO_API_KEY`, `MAIL_FROM=FitMate <인증한 Gmail 주소>`

> 도메인 없이 Gmail 주소로 보내면 받는 쪽에서 스팸함으로 갈 수 있어요. 자체 도메인이 생기면 Brevo에 도메인을 인증(SPF·DKIM)하고 `MAIL_FROM`을 그 도메인 주소로 바꾸면 됩니다.

## ② Upstash (Redis)

1. https://upstash.com 가입 → **Create Database** (Redis)
   - Region: **ap-southeast-1 (Singapore)**, TLS 켜짐(기본값)
2. 데이터베이스 화면의 **Connect → Redis URL** (`rediss://default:...@....upstash.io:6379`) → `REDIS_URL`
   - `rediss://`(s 두 개)는 TLS 연결이라는 뜻입니다

## ③ Render (API 서버, 무료)

> 유료(월 $5)여도 괜찮다면 Railway를 써도 됩니다 → 맨 아래 [Railway로 배포하기](#railway로-배포하기-유료) 참고

무료 플랜의 특징
- 메모리 512MB: 이 값에 맞춘 설정이 `render.yaml`에 들어 있습니다 (512MB로 제한한 컨테이너에 사진 업로드 부하를 걸어 검증)
- **15분 동안 요청이 없으면 잠들고**, 다음 첫 요청에 30~60초 걸려 깨어납니다 → 아래 "잠들지 않게 하기"로 해결
- 한 달 750시간 무료 (서비스 1개를 한 달 내내 켜 둬도 744시간이라 충분)

1. https://render.com → **Get Started** → **GitHub**로 가입
2. 대시보드 오른쪽 위 **+ New** → **Blueprint**
   - GitHub 연결 화면이 나오면 **Only select repositories → FitMate** 허용
   - 저장소 목록에서 **khj0317/FitMate** → **Connect**
3. Render가 저장소의 `render.yaml`을 읽어서 **fitmate-api** 서비스(Free, Singapore)를 보여 줍니다
   - Blueprint Name: `fitmate`
   - 아래에 비밀 값 입력 칸이 나옵니다. `backend/.env.railway`에서 같은 이름의 값을 복사해 넣으세요

     | 칸 | 값 |
     |---|---|
     | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `.env.railway` 그대로 |
     | `REDIS_URL` | `.env.railway` 그대로 |
     | `JWT_SECRET` | `.env.railway` 그대로 |
     | `CORS_ALLOWED_ORIGINS` | 지금은 `http://localhost:5173` (⑤에서 Vercel 주소로 바꿈) |
     | `S3_ENDPOINT`, `S3_PUBLIC_URL`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | `.env.railway` 그대로 |

   - 나머지(메모리 설정, 저장소 종류 등)는 `render.yaml`에 이미 들어 있어서 입력하지 않아도 됩니다
4. **Apply** (또는 Deploy Blueprint) → 첫 빌드 5~10분
5. 왼쪽 **fitmate-api** → **Logs**에서 아래가 보이면 성공
   - `Successfully applied 7 migrations`
   - `데모 사용자 30명을 생성했습니다`
   - `Started BackendApplication`
6. 서비스 화면 위쪽의 주소(`https://fitmate-api-xxxx.onrender.com`)를 복사 → ④에서 사용
   - 확인: `https://<주소>/actuator/health` → `{"status":"UP"}`

> Blueprint에서 결제 수단을 요구하면: **+ New → Web Service** → FitMate 선택 →
> Language **Docker**, Root Directory `backend`, Region **Singapore**, Instance Type **Free**,
> Health Check Path `/actuator/health`, Environment Variables에 `.env.railway` 내용 + 아래 값 추가
> ```env
> JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=40 -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=40m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul
> MALLOC_ARENA_MAX=2
> SERVER_TOMCAT_THREADS_MAX=30
> FITMATE_IMAGE_MAX_CONCURRENCY=1
> ```
> (Environment Variables 화면의 **Add from .env** 버튼으로 한 번에 붙여 넣을 수 있습니다)

### 잠들지 않게 하기 (선택, 무료)
포트폴리오를 보는 사람이 첫 화면에서 1분 가까이 기다리지 않게, 5분마다 서버를 두드려 깨워 둡니다.
1. https://uptimerobot.com 무료 가입 → **+ New monitor**
2. Monitor Type **HTTP(s)**, URL `https://<Render 주소>/actuator/health`, Interval **5 minutes** → Create

## ④ Vercel (웹)

1. https://vercel.com 에 GitHub 계정으로 가입 → **Add New → Project → `FitMate` Import**
2. 설정
   - **Root Directory**: `apps/web` (Framework: Vite 자동 인식, 빌드 설정은 `apps/web/vercel.json`에 있음)
   - **Environment Variables**
     - `VITE_API_URL` = ③에서 만든 API 주소 (예: `https://fitmate-api-xxxx.onrender.com`, 끝에 `/` 없이)
3. **Deploy** → `https://fitmate-xxxx.vercel.app` 같은 주소가 생깁니다

## ⑤ 마무리: CORS

Render(fitmate-api → **Environment**) 또는 Railway(**Variables**)의 `CORS_ALLOWED_ORIGINS`를 ④의 Vercel 주소로 바꾸고 저장하면 자동으로 다시 배포됩니다.

```env
CORS_ALLOWED_ORIGINS=https://fitmate-xxxx.vercel.app
```

- 여러 개는 쉼표로 구분, Vercel 미리보기 배포까지 허용하려면 패턴 사용:
  `https://fitmate-xxxx.vercel.app,https://fitmate-*-<vercel-계정>.vercel.app`
- 이 값이 틀리면 로그인은 되는데 **CORS 에러**가 나거나, 채팅 **WebSocket 연결이 403**으로 거절됩니다

## 배포 후 확인할 것

- [ ] 웹 주소 접속 → 회원가입 후 로그인
- [ ] 운동 메이트 추천, 모임 목록·지도, 커뮤니티 글이 보임
- [ ] 채팅방에서 메시지를 보내면 바로 표시 (사이드바에 "실시간 연결됨")
- [ ] 커뮤니티 글쓰기에 사진 첨부 → 사진 주소가 `supabase.co/storage/...`로 시작
- [ ] 새로고침해도 로그인이 유지되고, 다른 주소(`/community/1` 등)로 바로 들어가도 404가 아님

## 자주 나는 문제

| 증상 | 원인 · 해결 |
|---|---|
| API 배포가 health check에서 실패 | Variables의 DB·Redis 값 확인. 로그에 `Connection refused`/`password authentication failed`가 보이면 `DB_URL`이 Session pooler 주소인지, `DB_USERNAME`이 `postgres.<ref>` 형태인지 확인 |
| 브라우저 콘솔에 CORS 에러 | `CORS_ALLOWED_ORIGINS`가 Vercel 주소와 정확히 같은지 (https 포함, 끝에 `/` 없이) |
| 채팅이 "연결 중..."에서 멈춤 | 위와 같은 CORS 설정 (WebSocket도 같은 값을 씀) |
| 사진 업로드 500 | S3 키·버킷 이름, 버킷이 Public인지 |
| 사진이 안 보임 (403) | 버킷이 Public이 아님 |
| 아이디·비밀번호 찾기 메일이 안 옴 | 메일은 선택 사항. `backend/.env.example`의 `MAIL_*`(예: Gmail 앱 비밀번호)을 넣어야 발송됨 |

## 운영 설정 요약

- **도배 방지**: 글·댓글·채팅·매칭 요청·신고·회원가입 등에 요청 횟수 제한 (Redis, 서버가 여러 대여도 합산). 넘으면 429 + `Retry-After`
- **데모 데이터**: 배포에서는 만들지 않음 (`DEMO_DATA_ENABLED=false`). 로컬 개발(`local` 프로필)에서만 생성
- **서버 깨우는 중 안내**: API 응답이 4초 넘게 없으면 웹 화면 위에 안내를 띄워서, 무료 서버가 깨어나는 동안 멈춘 것처럼 보이지 않게 함
- **헬스 체크**: `/actuator/health` (DB·Redis 포함)
- **자동 배포**: `main`에 푸시 → GitHub Actions(백엔드 테스트, 웹 빌드, Docker 이미지 빌드) → Render(백엔드가 바뀐 경우)·Vercel이 각각 자동 배포
- **작은 서버 대응**: 사진은 결과 크기의 2배까지만 줄여 읽고(subsampling), 동시에 처리하는 장수를 제한. 512MB 컨테이너에서 6명이 사진 4장씩 동시에 올려도 최대 452MB, 헬스 체크 지연 없음

## Railway로 배포하기 (유료)

Render 대신 Railway(체험 후 월 $5, 잠들지 않음)를 쓰려면:
1. https://railway.com → GitHub로 가입 → **New Project → GitHub Repository → khj0317/FitMate**
2. 서비스 **Settings**: Root Directory `/backend`, Config file `/backend/railway.json`, Region Singapore,
   Networking → **Generate Domain** (포트 8081)
3. **Variables → Raw Editor**에 `backend/.env.railway` 내용을 그대로 붙여넣기 → Deploy
