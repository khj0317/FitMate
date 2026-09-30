import clsx from 'clsx'
import { ArrowRight, Check, MapPin, MessageCircle, Sparkles } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { EmailVerificationField } from '../components/EmailVerificationField'
import { BirthDateInput } from '../components/BirthDateInput'
import { LocationSearch } from '../components/LocationSearch'
import { Logo } from '../components/Logo'
import { Button, Field, Input, Segmented } from '../components/ui'
import { ApiError, errorMessage } from '../lib/api'
import type { Gender, LocationInput } from '../lib/types'
import { useAuth } from '../providers/authContext'

const FEATURES = [
  { icon: MapPin, title: '내 주변 운동 메이트', text: '활동 지역 반경 안에서 같은 운동을 하는 사람을 찾아요' },
  { icon: Sparkles, title: '매칭 점수 추천', text: '거리·실력·운동 시간·매너를 합쳐 잘 맞는 순서로 보여줘요' },
  { icon: MessageCircle, title: '실시간 채팅', text: '매칭되면 바로 대화하고 운동 약속을 잡아요' },
]

function AuthLayout({
  title,
  subtitle,
  wide = false,
  children,
}: {
  title: string
  subtitle: string
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
      {/* 브랜드 패널: 폼이 길어져도 화면에 고정 */}
      <section className="relative hidden overflow-hidden bg-linear-to-br from-brand-500 via-brand-600 to-rose-600 p-12 text-white lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col">
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
        <div className={clsx('w-full animate-fade-up', wide ? 'max-w-md' : 'max-w-sm')}>
          <Logo className="mb-10 lg:hidden" />
          <h1 className="text-3xl font-extrabold tracking-tight">{title}</h1>
          <p className="mt-2 text-ink-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
    </div>
  )
}

/** 로컬 개발용 데모 계정 (backend의 local 프로필이 만든 demo01). 배포 빌드에는 버튼이 들어가지 않는다 */
const DEMO_ACCOUNT = { loginId: 'demo01', password: 'password123' }

export function LoginPage() {
  const { login } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const loginAs = async (id: string, pw: string) => {
    setError(null)
    setLoading(true)
    try {
      await login(id, pw) // 이동은 GuestOnly가 처리
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    void loginAs(loginId, password)
  }

  return (
    <AuthLayout title="다시 만나서 반가워요 👋" subtitle="로그인하고 오늘의 운동 메이트를 찾아보세요">
      <form onSubmit={submit} className="space-y-4">
        <Field label="아이디">
          <Input
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            placeholder="아이디"
            autoComplete="username"
            autoCapitalize="none"
            required
          />
        </Field>
        <Field label="비밀번호">
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            autoComplete="current-password"
            required
          />
        </Field>
        {error && <p className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-600">{error}</p>}
        <Button type="submit" size="lg" loading={loading} className="w-full">
          로그인 <ArrowRight className="size-4" />
        </Button>
        <div className="flex justify-center gap-3 text-sm text-ink-500">
          <Link to="/find-account?tab=id" className="hover:text-ink-900 hover:underline">아이디 찾기</Link>
          <span className="text-ink-300">|</span>
          <Link to="/find-account?tab=password" className="hover:text-ink-900 hover:underline">비밀번호 찾기</Link>
        </div>
        {import.meta.env.DEV && (
          <Button
            type="button"
            variant="secondary"
            className="w-full"
            loading={loading}
            onClick={() => void loginAs(DEMO_ACCOUNT.loginId, DEMO_ACCOUNT.password)}
          >
            🧪 데모 계정으로 로그인 (로컬 개발용)
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

// ---------- 회원가입 ----------

const LOGIN_ID_PATTERN = /^[a-z0-9_]{4,20}$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 서버 에러 코드를 해당 입력 칸에 표시한다 */
const ERROR_FIELD: Record<string, keyof SignupForm> = {
  DUPLICATE_LOGIN_ID: 'loginId',
  DUPLICATE_EMAIL: 'email',
  EMAIL_NOT_VERIFIED: 'email',
  DUPLICATE_NICKNAME: 'nickname',
  PASSWORD_MISMATCH: 'passwordConfirm',
  INVALID_BIRTH_DATE: 'birthDate',
}

interface SignupForm {
  loginId: string
  password: string
  passwordConfirm: string
  nickname: string
  email: string
  /** 이메일 인증을 마치면 서버가 준 토큰 */
  emailToken: string | null
  birthDate: string
  gender: Gender | null
  location: LocationInput | null
}

type FieldErrors = Partial<Record<keyof SignupForm, string>>

function validate(form: SignupForm): FieldErrors {
  const errors: FieldErrors = {}
  if (!LOGIN_ID_PATTERN.test(form.loginId)) errors.loginId = '영문 소문자, 숫자, _로 4~20자여야 해요'
  if (!PASSWORD_PATTERN.test(form.password)) errors.password = '영문과 숫자를 포함해 8자 이상이어야 해요'
  if (!form.passwordConfirm || form.passwordConfirm !== form.password) errors.passwordConfirm = '비밀번호가 일치하지 않아요'
  if (form.nickname.trim().length < 2) errors.nickname = '닉네임은 2자 이상이어야 해요'
  if (!form.email.trim()) errors.email = '이메일을 입력해 주세요'
  else if (!EMAIL_PATTERN.test(form.email.trim())) errors.email = '올바른 이메일 형식이 아니에요'
  else if (!form.emailToken) errors.email = '이메일 인증을 완료해 주세요'
  if (!form.birthDate) errors.birthDate = '생년월일을 입력해 주세요'
  if (!form.gender) errors.gender = '성별을 선택해 주세요'
  if (!form.location) errors.location = '목록에서 활동 지역을 선택해 주세요'
  return errors
}

const EMPTY_FORM: SignupForm = {
  loginId: '',
  password: '',
  passwordConfirm: '',
  nickname: '',
  email: '',
  emailToken: null,
  birthDate: '',
  gender: null,
  location: null,
}

export function SignupPage() {
  const { signup } = useAuth()
  const [form, setForm] = useState<SignupForm>(EMPTY_FORM)
  const [touched, setTouched] = useState<Partial<Record<keyof SignupForm, boolean>>>({})
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const clientErrors = validate(form)
  // 한 번 건드린 칸만 즉시 검증 결과를 보여주고, 서버 에러(중복 등)가 있으면 우선 표시한다
  const fieldError = (key: keyof SignupForm) => serverErrors[key] ?? (touched[key] ? clientErrors[key] : undefined)

  const set = <K extends keyof SignupForm>(key: K, value: SignupForm[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setServerErrors(({ [key]: _removed, ...rest }) => rest)
  }
  const touch = (key: keyof SignupForm) => () => setTouched((current) => ({ ...current, [key]: true }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setTouched(Object.fromEntries(Object.keys(EMPTY_FORM).map((key) => [key, true])))
    if (Object.keys(clientErrors).length > 0 || !form.gender || !form.location) return

    setLoading(true)
    try {
      await signup({
        loginId: form.loginId,
        password: form.password,
        passwordConfirm: form.passwordConfirm,
        nickname: form.nickname.trim(),
        email: form.email.trim(),
        emailVerificationToken: form.emailToken,
        birthDate: form.birthDate,
        gender: form.gender,
        location: form.location,
      }) // 가입 후 프로필 화면 이동은 GuestOnly가 afterLoginPath로 처리
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) {
        setServerErrors(Object.fromEntries(e.fieldErrors.map(({ field, reason }) => [field, reason])))
      } else if (e instanceof ApiError && ERROR_FIELD[e.code]) {
        // 인증 토큰이 만료됐으면 다시 인증하게 한다
        if (e.code === 'EMAIL_NOT_VERIFIED') set('emailToken', null)
        setServerErrors({ [ERROR_FIELD[e.code]]: e.message })
      } else {
        setError(errorMessage(e))
      }
    } finally {
      setLoading(false)
    }
  }

  const passwordsMatch = form.passwordConfirm.length > 0 && form.passwordConfirm === form.password

  return (
    <AuthLayout title="FitMate 시작하기" subtitle="정보를 입력하면 바로 운동 메이트를 추천해 드려요" wide>
      <form onSubmit={submit} noValidate className="space-y-8">
        <FormGroup step={1} title="계정 정보">
          <Field label="아이디" hint="영문 소문자, 숫자, _ 4~20자" error={fieldError('loginId')}>
            <Input
              value={form.loginId}
              onChange={(e) => set('loginId', e.target.value.toLowerCase())}
              onBlur={touch('loginId')}
              placeholder="fitmate_runner"
              autoComplete="username"
              autoCapitalize="none"
              maxLength={20}
            />
          </Field>
          <Field label="비밀번호" hint="영문과 숫자를 포함해 8자 이상" error={fieldError('password')}>
            <Input
              type="password"
              value={form.password}
              onChange={(e) => set('password', e.target.value)}
              onBlur={touch('password')}
              placeholder="비밀번호"
              autoComplete="new-password"
            />
          </Field>
          <Field label="비밀번호 확인" error={fieldError('passwordConfirm')}>
            <div className="relative">
              <Input
                type="password"
                value={form.passwordConfirm}
                onChange={(e) => set('passwordConfirm', e.target.value)}
                onBlur={touch('passwordConfirm')}
                placeholder="비밀번호를 한 번 더 입력"
                autoComplete="new-password"
                className="pr-11"
              />
              {passwordsMatch && (
                <Check className="absolute top-1/2 right-4 size-5 -translate-y-1/2 text-emerald-500" aria-label="비밀번호 일치" />
              )}
            </div>
          </Field>
        </FormGroup>

        <FormGroup step={2} title="프로필">
          <Field label="닉네임" hint="한글, 영문, 숫자, _ 2~20자" error={fieldError('nickname')}>
            <Input
              value={form.nickname}
              onChange={(e) => set('nickname', e.target.value)}
              onBlur={touch('nickname')}
              placeholder="운동하는_곰"
              maxLength={20}
            />
          </Field>
          <EmailVerificationField
            hint="아이디·비밀번호를 잊었을 때 찾는 데 사용돼요"
            email={form.email}
            onEmailChange={(value) => set('email', value)}
            token={form.emailToken}
            onToken={(token) => set('emailToken', token)}
            onBlur={touch('email')}
            error={fieldError('email')}
          />
          <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
            <Field label="생년월일">
              <BirthDateInput
                value={form.birthDate}
                onChange={(iso) => set('birthDate', iso)}
                onBlur={touch('birthDate')}
                error={fieldError('birthDate')}
              />
            </Field>
            <Field label="성별" group error={fieldError('gender')}>
              <div className="flex h-12 items-center">
                <Segmented<Gender>
                  options={[
                    { value: 'MALE', label: '남성' },
                    { value: 'FEMALE', label: '여성' },
                  ]}
                  value={form.gender}
                  onChange={(value) => set('gender', value)}
                />
              </div>
            </Field>
          </div>
        </FormGroup>

        <FormGroup step={3} title="활동 지역">
          <LocationSearch value={form.location} onChange={(location) => set('location', location)} error={fieldError('location')} />
        </FormGroup>

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

function FormGroup({ step, title, children }: { step: number; title: string; children: ReactNode }) {
  return (
    <fieldset className="space-y-4">
      <legend className="mb-4 flex items-center gap-2 text-sm font-bold text-ink-900">
        <span className="flex size-6 items-center justify-center rounded-full bg-brand-500 text-xs text-white">{step}</span>
        {title}
      </legend>
      {children}
    </fieldset>
  )
}
