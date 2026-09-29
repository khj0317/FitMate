import clsx from 'clsx'
import { CheckCircle2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, errorMessage } from '../lib/api'
import { Button, Input } from './ui'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const RESEND_SECONDS = 60

/**
 * 이메일 입력 + 인증 코드 확인. 인증에 성공하면 서버가 준 토큰을 onToken으로 넘기고,
 * 가입·프로필 저장 요청에 그 토큰(emailVerificationToken)을 함께 보낸다.
 * 인증한 뒤 이메일을 바꾸면 인증이 풀린다.
 * needsVerification이 false면(프로필에서 기존 이메일 그대로) 인증 칸 없이 입력만 보여준다.
 */
export function EmailVerificationField({
  label = '이메일',
  hint,
  email,
  onEmailChange,
  token,
  onToken,
  error,
  onBlur,
  needsVerification = true,
}: {
  label?: string
  hint?: string
  email: string
  onEmailChange: (email: string) => void
  token: string | null
  onToken: (token: string | null) => void
  error?: string
  onBlur?: () => void
  needsVerification?: boolean
}) {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [sending, setSending] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const [message, setMessage] = useState<{ tone: 'error' | 'info'; text: string } | null>(null)

  // 다시 보내기까지 남은 시간
  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((seconds) => seconds - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const trimmed = email.trim()
  const validEmail = EMAIL_PATTERN.test(trimmed)
  const verified = !!token

  const changeEmail = (value: string) => {
    onEmailChange(value)
    if (token) onToken(null)
    if (sentTo && value.trim() !== sentTo) {
      setSentTo(null)
      setCode('')
      setMessage(null)
    }
  }

  const sendCode = async () => {
    setSending(true)
    setMessage(null)
    try {
      await api.post('/api/auth/email-verification/request', { email: trimmed })
      setSentTo(trimmed)
      setCode('')
      setCooldown(RESEND_SECONDS)
      setMessage({ tone: 'info', text: '메일로 보낸 6자리 코드를 10분 안에 입력해 주세요. 메일이 안 보이면 스팸함도 확인해 주세요.' })
    } catch (e) {
      setMessage({ tone: 'error', text: errorMessage(e) })
    } finally {
      setSending(false)
    }
  }

  const confirm = async () => {
    setConfirming(true)
    setMessage(null)
    try {
      const result = await api.post<{ verificationToken: string }>('/api/auth/email-verification/confirm', {
        email: trimmed,
        code: code.trim(),
      })
      onToken(result.verificationToken)
      setMessage(null)
    } catch (e) {
      setMessage({ tone: 'error', text: errorMessage(e) })
    } finally {
      setConfirming(false)
    }
  }

  return (
    <div>
      <p className="mb-1.5 text-sm font-semibold text-ink-700">{label}</p>
      <div className="flex gap-2">
        <Input
          type="email"
          value={email}
          onChange={(e) => changeEmail(e.target.value)}
          onBlur={onBlur}
          placeholder="you@example.com"
          autoComplete="email"
          readOnly={verified}
          aria-label={label}
          className={clsx('flex-1', verified && 'text-ink-500')}
        />
        {needsVerification &&
          (verified ? (
            <span className="flex h-12 shrink-0 items-center gap-1 rounded-xl bg-emerald-50 px-3 text-sm font-semibold text-emerald-700">
              <CheckCircle2 className="size-4" /> 인증 완료
            </span>
          ) : (
            <Button
              type="button"
              variant="secondary"
              className="h-12 shrink-0"
              disabled={!validEmail || cooldown > 0}
              loading={sending}
              onClick={() => void sendCode()}
            >
              {sentTo ? (cooldown > 0 ? `다시 보내기 ${cooldown}초` : '다시 보내기') : '인증번호 받기'}
            </Button>
          ))}
      </div>

      {needsVerification && sentTo && !verified && (
        <div className="mt-2 flex gap-2">
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                if (code.length === 6) void confirm()
              }
            }}
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="인증 코드 6자리"
            aria-label="인증 코드"
            className="flex-1 tracking-[0.3em]"
          />
          <Button type="button" className="h-12 shrink-0" disabled={code.length !== 6} loading={confirming} onClick={() => void confirm()}>
            확인
          </Button>
        </div>
      )}

      {verified && (
        <button
          type="button"
          onClick={() => {
            onToken(null)
            setSentTo(null)
            setCode('')
          }}
          className="mt-1.5 cursor-pointer text-xs text-ink-400 underline-offset-2 hover:text-ink-700 hover:underline"
        >
          다른 이메일로 바꾸기
        </button>
      )}

      {message ? (
        <p className={clsx('mt-1.5 text-xs', message.tone === 'error' ? 'text-red-600' : 'text-ink-500')}>{message.text}</p>
      ) : error ? (
        <p className="mt-1.5 text-xs text-red-600">{error}</p>
      ) : (
        hint && <p className="mt-1.5 text-xs text-ink-400">{hint}</p>
      )}
    </div>
  )
}
