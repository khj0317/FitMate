import { ArrowRight, MapPin, MessageCircle, Sparkles } from 'lucide-react'
import { useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { Logo } from '../components/Logo'
import { Button, Field, Input } from '../components/ui'
import { ApiError, errorMessage } from '../lib/api'
import { useAuth } from '../providers/AuthProvider'

const FEATURES = [
  { icon: MapPin, title: '내 주변 운동 메이트', text: '활동 지역 반경 안에서 같은 운동을 하는 사람을 찾아요' },
  { icon: Sparkles, title: '매칭 점수 추천', text: '거리·실력·운동 시간·매너를 합쳐 잘 맞는 순서로 보여줘요' },
  { icon: MessageCircle, title: '실시간 채팅', text: '매칭되면 바로 대화하고 운동 약속을 잡아요' },
]

function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      {/* 브랜드 패널 */}
      <section className="relative hidden overflow-hidden bg-linear-to-br from-brand-500 via-brand-600 to-rose-600 p-12 text-white lg:flex lg:flex-col">
        <div className="absolute -top-24 -right-24 size-96 rounded-full bg-white/10 blur-2xl" />
        <div className="absolute -bottom-32 -left-16 size-[28rem] rounded-full bg-amber-300/20 blur-3xl" />
        <Logo light className="relative" />
        <div className="relative my-auto max-w-lg">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-sm font-semibold backdrop-blur">
            🏃 오늘도 같이 뛸 사람?
          </p>
          <h2 className="text-5xl leading-[1.15] font-extrabold tracking-tight">
            혼자 하던 운동,
            <br />
            이제 같이 해요
          </h2>
          <ul className="mt-12 space-y-6">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-4">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-white/15 backdrop-blur">
                  <Icon className="size-5" />
                </div>
                <div>
                  <p className="font-bold">{title}</p>
                  <p className="mt-0.5 text-sm text-white/80">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <div className="relative flex gap-3 text-3xl">
          {['🏋️', '🏃', '🧗', '🎾', '🚴', '🧘'].map((emoji) => (
            <span key={emoji} className="flex size-14 items-center justify-center rounded-2xl bg-white/15 backdrop-blur">
              {emoji}
            </span>
          ))}
        </div>
      </section>

      {/* 폼 */}
      <section className="flex items-center justify-center px-5 py-12">
        <div className="w-full max-w-sm animate-fade-up">
          <Logo className="mb-10 lg:hidden" />
          <h1 className="text-3xl font-extrabold tracking-tight">{title}</h1>
          <p className="mt-2 text-ink-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </div>
  )
}

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="다시 만나서 반가워요 👋" subtitle="로그인하고 오늘의 운동 메이트를 찾아보세요">
      <form onSubmit={submit} className="space-y-4">
        <Field label="이메일">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" required />
        </Field>
        <Field label="비밀번호">
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호" autoComplete="current-password" required />
        </Field>
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-600">{error}</p>}
        <Button type="submit" size="lg" loading={loading} className="w-full">
          로그인 <ArrowRight className="size-4" />
        </Button>
        {import.meta.env.DEV && (
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            onClick={() => {
              setEmail('demo01@fitmate.com')
              setPassword('password123')
            }}
          >
            🧪 데모 계정 입력 (로컬 개발용)
          </Button>
        )}
      </form>
      <p className="mt-8 text-center text-sm text-ink-500">
        아직 계정이 없나요?{' '}
        <Link to="/signup" className="font-bold text-brand-600 hover:underline">
          회원가입
        </Link>
      </p>
    </AuthLayout>
  )
}

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const update = (key: keyof typeof form) => (event: ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setLoading(true)
    try {
      await signup(form.email, form.password, form.nickname)
      navigate('/profile?welcome=1', { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(e.fieldErrors.map(({ field, reason }) => [field, reason])))
      } else {
        setError(errorMessage(e))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="FitMate 시작하기" subtitle="1분이면 가입하고 운동 메이트를 찾을 수 있어요">
      <form onSubmit={submit} className="space-y-4">
        <Field label="이메일" error={fieldErrors.email}>
          <Input type="email" value={form.email} onChange={update('email')} placeholder="you@example.com" autoComplete="email" required />
        </Field>
        <Field label="닉네임" hint="한글, 영문, 숫자, _ 2~20자" error={fieldErrors.nickname}>
          <Input value={form.nickname} onChange={update('nickname')} placeholder="운동하는_곰" autoComplete="nickname" required />
        </Field>
        <Field label="비밀번호" hint="영문과 숫자를 포함해 8자 이상" error={fieldErrors.password}>
          <Input type="password" value={form.password} onChange={update('password')} placeholder="비밀번호" autoComplete="new-password" required />
        </Field>
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-600">{error}</p>}
        <Button type="submit" size="lg" loading={loading} className="w-full">
          가입하고 시작하기 <ArrowRight className="size-4" />
        </Button>
      </form>
      <p className="mt-8 text-center text-sm text-ink-500">
        이미 계정이 있나요?{' '}
        <Link to="/login" className="font-bold text-brand-600 hover:underline">
          로그인
        </Link>
      </p>
    </AuthLayout>
  )
}
