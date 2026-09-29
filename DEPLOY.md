# FitMate 배포 가이드

```
브라우저 ──▶ Vercel (웹, React)
   │
   └──── REST · WebSocket ──▶ Railway (API, Spring Boot · Docker)
                                 ├──▶ Supabase Postgres + PostGIS (DB)
                                 ├──▶ Supabase Storage (사진, S3 호환)
                                 └──▶ Upstash Redis (토큰 · 실시간 메시지 · 접속 상태 · 요청 제한)
```

모든 서비스를 **싱가포르 리전**으로 맞추면 서버끼리 주고받는 지연이 가장 짧습니다 (Railway의 아시아 리전이 싱가포르).

순서: **① Supabase → ② Upstash → ③ Railway(API) → ④ Vercel(웹) → ⑤ CORS 마무리**

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

## ② Upstash (Redis)

1. https://upstash.com 가입 → **Create Database** (Redis)
   - Region: **ap-southeast-1 (Singapore)**, TLS 켜짐(기본값)
2. 데이터베이스 화면의 **Connect → Redis URL** (`rediss://default:...@....upstash.io:6379`) → `REDIS_URL`
   - `rediss://`(s 두 개)는 TLS 연결이라는 뜻입니다

## ③ Railway (API 서버)

1. https://railway.com 에 GitHub 계정으로 가입 → **New Project → Deploy from GitHub repo → `FitMate`**
2. 생성된 서비스 **Settings**
   - **Root Directory**: `/backend`
   - **Config file** (railway.json 자동 인식이 안 되면): `/backend/railway.json`
     → Dockerfile로 빌드하고, `/actuator/health`가 정상이어야 배포가 완료됩니다
   - **Region**: Southeast Asia (Singapore)
   - **Networking → Generate Domain** → `https://fitmate-api-xxxx.up.railway.app` 같은 주소가 생깁니다 (④에서 사용)
   - (선택) **Wait for CI** 켜기: GitHub Actions 테스트가 통과한 커밋만 배포
3. **Variables** 탭 → **Raw Editor**에 아래를 채워 넣기 (값은 ①② 참고)

   ```env
   DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
   DB_USERNAME=postgres.<ref>
   DB_PASSWORD=...
   DB_POOL_SIZE=5
   REDIS_URL=rediss://default:...@....upstash.io:6379
   JWT_SECRET=...            # 아래 명령으로 만든 랜덤 문자열
   CORS_ALLOWED_ORIGINS=http://localhost:5173   # ⑤에서 Vercel 주소로 바꿈
   STORAGE_TYPE=s3
   S3_ENDPOINT=https://<ref>.supabase.co/storage/v1/s3
   S3_REGION=ap-southeast-1
   S3_BUCKET=fitmate
   S3_ACCESS_KEY=...
   S3_SECRET_KEY=...
   S3_PUBLIC_URL=https://<ref>.supabase.co/storage/v1/object/public/fitmate
   S3_PATH_STYLE=true
   DEMO_DATA_ENABLED=true
   ```

   `JWT_SECRET`은 터미널에서 만들 수 있습니다 (PowerShell):
   ```powershell
   [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

4. **Deploy** → 로그에 `Successfully applied 7 migrations`, `데모 사용자 30명을 생성했습니다`, `Started BackendApplication`이 보이면 성공
5. 확인: 브라우저에서 `https://<railway-주소>/actuator/health` → `{"status":"UP"}`,
   `https://<railway-주소>/swagger-ui.html` → API 문서

> Railway는 체험 크레딧이 끝나면 Hobby 플랜(월 $5)이 필요합니다. 무료로 하려면 Render(무료 웹 서비스, 15분 동안 요청이 없으면 잠들었다가 첫 요청에 30초쯤 걸려 깨어남)에 같은 Dockerfile로 올릴 수 있습니다.

## ④ Vercel (웹)

1. https://vercel.com 에 GitHub 계정으로 가입 → **Add New → Project → `FitMate` Import**
2. 설정
   - **Root Directory**: `apps/web` (Framework: Vite 자동 인식, 빌드 설정은 `apps/web/vercel.json`에 있음)
   - **Environment Variables**
     - `VITE_API_URL` = ③에서 만든 Railway 주소 (예: `https://fitmate-api-xxxx.up.railway.app`, 끝에 `/` 없이)
     - `VITE_DEMO_LOGIN` = `true` (로그인 화면에 "체험 계정으로 둘러보기" 버튼)
3. **Deploy** → `https://fitmate-xxxx.vercel.app` 같은 주소가 생깁니다

## ⑤ 마무리: CORS

Railway **Variables**의 `CORS_ALLOWED_ORIGINS`를 ④의 Vercel 주소로 바꾸고 저장하면 자동으로 다시 배포됩니다.

```env
CORS_ALLOWED_ORIGINS=https://fitmate-xxxx.vercel.app
```

- 여러 개는 쉼표로 구분, Vercel 미리보기 배포까지 허용하려면 패턴 사용:
  `https://fitmate-xxxx.vercel.app,https://fitmate-*-<vercel-계정>.vercel.app`
- 이 값이 틀리면 로그인은 되는데 **CORS 에러**가 나거나, 채팅 **WebSocket 연결이 403**으로 거절됩니다

## 배포 후 확인할 것

- [ ] 웹 주소 접속 → **체험 계정으로 둘러보기**로 로그인
- [ ] 운동 메이트 추천, 모임 목록·지도, 커뮤니티 글이 보임
- [ ] 채팅방에서 메시지를 보내면 바로 표시 (사이드바에 "실시간 연결됨")
- [ ] 커뮤니티 글쓰기에 사진 첨부 → 사진 주소가 `supabase.co/storage/...`로 시작
- [ ] 새로고침해도 로그인이 유지되고, 다른 주소(`/community/1` 등)로 바로 들어가도 404가 아님

## 자주 나는 문제

| 증상 | 원인 · 해결 |
|---|---|
| Railway 배포가 health check에서 실패 | Variables의 DB·Redis 값 확인. 로그에 `Connection refused`/`password authentication failed`가 보이면 `DB_URL`이 Session pooler 주소인지, `DB_USERNAME`이 `postgres.<ref>` 형태인지 확인 |
| 브라우저 콘솔에 CORS 에러 | `CORS_ALLOWED_ORIGINS`가 Vercel 주소와 정확히 같은지 (https 포함, 끝에 `/` 없이) |
| 채팅이 "연결 중..."에서 멈춤 | 위와 같은 CORS 설정 (WebSocket도 같은 값을 씀) |
| 사진 업로드 500 | S3 키·버킷 이름, 버킷이 Public인지 |
| 사진이 안 보임 (403) | 버킷이 Public이 아님 |
| 아이디·비밀번호 찾기 메일이 안 옴 | 메일은 선택 사항. `backend/.env.example`의 `MAIL_*`(예: Gmail 앱 비밀번호)을 넣어야 발송됨 |

## 운영 설정 요약

- **도배 방지**: 글·댓글·채팅·매칭 요청·신고·회원가입 등에 요청 횟수 제한 (Redis, 서버가 여러 대여도 합산). 넘으면 429 + `Retry-After`
- **체험 계정 보호**: `DEMO_DATA_ENABLED=true`이면 `demo01~demo30`은 탈퇴할 수 없고, 누가 지워도 재시작 시 복구. 다가오는 데모 모임이 없으면(시간이 지나 모두 끝나면) 재시작할 때 새로 만듦
- **헬스 체크**: `/actuator/health` (DB·Redis 포함)
- **자동 배포**: `main`에 푸시 → GitHub Actions(백엔드 테스트, 웹 빌드, Docker 이미지 빌드) → Railway·Vercel이 각각 자동 배포
