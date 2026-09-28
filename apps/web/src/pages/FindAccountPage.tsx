import clsx from 'clsx'
import { ArrowLeft, CheckCircle2, KeyRound, Mail, MailCheck, UserSearch } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Logo } from '../components/Logo'
import { Button, Card, Field, Input } from '../components/ui'
import { api, ApiError, errorMessage } from '../lib/api'

type Tab = 'id' | 'password'

const CODE_TTL_SECONDS = 10 * 60
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

export function FindAccountPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'password' ? 'password' : 'id'

  return (
    <div className="flex min-h-dvh flex-col items-center bg-ink-50 px-5 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-between">
          <Link to="/"><Logo /></Link>
          <Link to="/login" className="flex items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-900">
            <ArrowLeft className="size-4" /> 로그인
          </Link>
        </div>

        <h1 className="text-3xl font-extrabold tracking-tight">계정 찾기</h1>
        <p className="mt-2 text-ink-500">가입할 때 등록한 이메일로 찾을 수 있어요</p>

        <div className="mt-8 mb-6 grid grid-cols-2 rounded-2xl bg-ink-100 p-1">
          {([
            ['id', '아이디 찾기', UserSearch],
            ['password', '비밀번호 찾기', KeyRound],
          ] as const).map(([value, label, Icon]) => (
            <button
              key={value}
              onClick={() => setParams({ tab: value })}
              className={clsx(
                'flex cursor-pointer items-center justify-center gap-2 rounded-xl py-2.5 text-[15px] font-bold transition-all',
                tab === value ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-500',
              )}
            >
              <Icon className="size-4" />
              {label}
            </button>
          ))}
        </div>

        <Card className="animate-fade-up p-6" key={tab}>
          {tab === 'id' ? <FindLoginId /> : <ResetPassword />}
        </Card>

        <p className="mt-6 text-center text-sm leading-relaxed text-ink-400">
          예전에 이메일 없이 가입했다면 계정을 찾을 수 없어요.
          <br />
          로그인한 뒤 <b className="text-ink-600">내 프로필</b>에서 이메일을 등록해 두세요.
        </p>
        {import.meta.env.DEV && (
          <p className="mt-4 rounded-xl bg-amber-50 px-4 py-3 text-center text-xs text-amber-800">
            🧪 로컬 개발 중에는 메일이 실제로 발송되지 않고{' '}
            <a href="http://localhost:8025" target="_blank" rel="noreferrer" className="font-bold underline">
              Mailpit (localhost:8025)
            </a>
            에 도착해요
          </p>
        )}
      </div>
    </div>
  )
}

// ---------- 아이디 찾기 ----------

function FindLoginId() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await api.post('/api/auth/find-login-id', { email })
      setSent(true)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  if (sent) {
    return (
      <Done
        icon={<MailCheck className="size-7" />}
        title="메일을 확인해 주세요"
        description={
          <>
            <b className="text-ink-800">{email}</b>(으)로 가입된 계정이 있다면 아이디를 보냈어요.
            <br />
            메일이 오지 않으면 스팸함을 확인하거나 다른 이메일로 시도해 보세요.
          </>
        }
        action={
          <div className="grid grid-cols-2 gap-2">
            <Button variant="secondary" onClick={() => setSent(false)}>다시 입력</Button>
            <Button className="w-full" onClick={() => navigate('/login')}>로그인하기</Button>
          </div>
        }
      />
    )
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="가입할 때 등록한 이메일">
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" required />
      </Field>
      {error && <ErrorBox>{error}</ErrorBox>}
      <Button type="submit" size="lg" className="w-full" loading={loading}>
        <Mail className="size-4" /> 아이디 받기
      </Button>
    </form>
  )
}

// ---------- 비밀번호 찾기 ----------

function ResetPassword() {
  const navigate = useNavigate()
  const [step, setStep] = useState<'request' | 'confirm' | 'done'>('request')
  const [loginId, setLoginId] = useState('')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [sentAt, setSentAt] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const remaining = useCountdown(sentAt, CODE_TTL_SECONDS)

  const requestCode = async (event?: FormEvent) => {
    event?.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await api.post('/api/auth/password-reset/request', { loginId, email })
      setSentAt(Date.now())
      setCode('')
      setStep('confirm')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const passwordError =
    password && !PASSWORD_PATTERN.test(password) ? '영문과 숫자를 포함해 8자 이상이어야 해요' : undefined
  const confirmError = passwordConfirm && passwordConfirm !== password ? '비밀번호가 일치하지 않아요' : undefined

  const confirm = async (event: FormEvent) => {
    event.preventDefault()
    if (passwordError || confirmError || code.length !== 6) return
    setError(null)
    setLoading(true)
    try {
      await api.post('/api/auth/password-reset/confirm', {
        loginId,
        code,
        newPassword: password,
        newPasswordConfirm: passwordConfirm,
      })
      setStep('done')
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'INVALID_VERIFICATION_CODE'
        ? '인증 코드가 올바르지 않거나 만료됐어요. 5번 틀리면 코드를 다시 받아야 해요.'
        : errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  if (step === 'done') {
    return (
      <Done
        icon={<CheckCircle2 className="size-7" />}
        title="비밀번호를 바꿨어요"
        description="보안을 위해 다른 기기에서는 모두 로그아웃됐어요. 새 비밀번호로 로그인해 주세요."
        action={<Button size="lg" className="w-full" onClick={() => navigate('/login')}>로그인하기</Button>}
      />
    )
  }

  if (step === 'request') {
    return (
      <form onSubmit={requestCode} className="space-y-4">
        <Field label="아이디">
          <Input value={loginId} onChange={(e) => setLoginId(e.target.value.toLowerCase())} placeholder="아이디" autoComplete="username" autoCapitalize="none" required />
        </Field>
        <Field label="가입할 때 등록한 이메일">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" required />
        </Field>
        {error && <ErrorBox>{error}</ErrorBox>}
        <Button type="submit" size="lg" className="w-full" loading={loading}>
          <Mail className="size-4" /> 인증 코드 받기
        </Button>
      </form>
    )
  }

  const minutes = Math.floor(remaining / 60)
  const seconds = String(remaining % 60).padStart(2, '0')

  return (
    <form onSubmit={confirm} className="space-y-4">
      <div className="rounded-2xl bg-brand-50 px-4 py-3 text-sm leading-relaxed text-brand-800">
        아이디와 이메일이 일치하면 <b>{email}</b>(으)로 6자리 인증 코드를 보냈어요.
      </div>
      <Field label="인증 코드">
        <div className="relative">
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            placeholder="000000"
            inputMode="numeric"
            autoComplete="one-time-code"
            className="pr-20 text-center font-mono text-xl tracking-[0.5em]"
            required
          />
          <span className={clsx('absolute top-1/2 right-4 -translate-y-1/2 text-sm font-semibold tabular-nums', remaining > 60 ? 'text-ink-400' : 'text-red-500')}>
            {remaining > 0 ? `${minutes}:${seconds}` : '만료'}
          </span>
        </div>
      </Field>
      <Field label="새 비밀번호" hint="영문과 숫자를 포함해 8자 이상" error={passwordError}>
        <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="새 비밀번호" autoComplete="new-password" required />
      </Field>
      <Field label="새 비밀번호 확인" error={confirmError}>
        <Input type="password" value={passwordConfirm} onChange={(e) => setPasswordConfirm(e.target.value)} placeholder="새 비밀번호를 한 번 더 입력" autoComplete="new-password" required />
      </Field>
      {error && <ErrorBox>{error}</ErrorBox>}
      <Button type="submit" size="lg" className="w-full" loading={loading} disabled={code.length !== 6 || remaining === 0}>
        <KeyRound className="size-4" /> 비밀번호 바꾸기
      </Button>
      <div className="flex justify-between text-sm">
        <button type="button" onClick={() => setStep('request')} className="cursor-pointer font-semibold text-ink-500 hover:text-ink-900">
          ← 아이디·이메일 다시 입력
        </button>
        <button type="button" onClick={() => void requestCode()} disabled={loading} className="cursor-pointer font-semibold text-brand-600 hover:underline disabled:opacity-50">
          코드 다시 받기
        </button>
      </div>
    </form>
  )
}

// ---------- 공통 ----------

function useCountdown(startedAt: number, seconds: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!startedAt) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [startedAt])
  if (!startedAt) return seconds
  return Math.max(0, seconds - Math.floor((now - startedAt) / 1000))
}

function Done({ icon, title, description, action }: { icon: ReactNode; title: string; description: ReactNode; action: ReactNode }) {
  return (
    <div className="text-center">
      <div className="mx-auto mb-4 flex size-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">{icon}</div>
      <p className="text-lg font-bold">{title}</p>
      <p className="mt-2 text-sm leading-relaxed text-ink-500">{description}</p>
      <div className="mt-6">{action}</div>
    </div>
  )
}

function ErrorBox({ children }: { children: ReactNode }) {
  return <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-600">{children}</p>
}
