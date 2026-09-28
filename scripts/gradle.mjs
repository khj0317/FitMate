// 운영체제에 맞는 Gradle 래퍼로 backend 작업을 실행한다 (Windows: gradlew.bat, macOS/Linux: ./gradlew).
// npm 스크립트는 Windows에서 cmd로 실행돼 ./gradlew 를 쓸 수 없어서 이 스크립트를 거친다.
// 예) node scripts/gradle.mjs bootRun   /   node scripts/gradle.mjs test
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const isWindows = process.platform === 'win32'
const backendDir = fileURLToPath(new URL('../backend/', import.meta.url))
// 보안 설정(NoDefaultCurrentDirectoryInExePath)으로 현재 폴더의 실행 파일을 찾지 못하는 PC가 있어 전체 경로로 실행한다
const wrapper = path.join(backendDir, isWindows ? 'gradlew.bat' : 'gradlew')
const args = ['--console=plain', ...process.argv.slice(2)]

// Windows의 .bat 파일은 셸로만 실행할 수 있어서 명령 한 줄로 넘긴다 (인자는 이 스크립트가 정한 값뿐)
const child = isWindows
  ? spawn(`"${wrapper}" ${args.join(' ')}`, { cwd: backendDir, stdio: 'inherit', shell: true })
  : spawn(wrapper, args, { cwd: backendDir, stdio: 'inherit' })

child.on('exit', (code) => process.exit(code ?? 0))
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
