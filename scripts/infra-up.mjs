// DB(PostgreSQL), Redis, Mailpit 컨테이너를 켠다.
// Docker Desktop이 꺼져 있으면 알아보기 어려운 에러 대신 한국어로 안내한다.
import { spawnSync } from 'node:child_process'

const docker = spawnSync('docker', ['info'], { stdio: 'ignore' })
if (docker.error || docker.status !== 0) {
  console.error('\n❌ Docker가 실행 중이 아니에요. Docker Desktop을 먼저 켜고, 시작이 끝나면 다시 실행해 주세요.\n')
  process.exit(1)
}

console.log('🐳 DB · Redis · 메일 서버(Mailpit)를 켜는 중...')
const up = spawnSync('docker', ['compose', 'up', '-d', '--wait'], { stdio: 'inherit' })
if (up.status !== 0) process.exit(up.status ?? 1)
console.log('✅ 준비 완료 (메일 확인: http://localhost:8025)\n')
