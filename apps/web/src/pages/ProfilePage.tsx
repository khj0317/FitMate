import clsx from 'clsx'
import { AtSign, LogOut, PartyPopper, RotateCcw, Save, Thermometer } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { BirthDateInput } from '../components/BirthDateInput'
import { LocationSearch } from '../components/LocationSearch'
import { Button, Card, Field, Input, PageHeader, PageLoader, Segmented, Textarea } from '../components/ui'
import { ApiError, errorMessage } from '../lib/api'
import { DAYS, SKILL_LABEL, SKILL_LEVELS, sportEmoji } from '../lib/format'
import { useMe, useSaveProfile, useSports } from '../lib/queries'
import type { AvailableTime, DayOfWeek, Gender, LocationInput, MyProfile, SkillLevel } from '../lib/types'
import { useAuth } from '../providers/AuthProvider'
import { useToast } from '../providers/ToastProvider'

const MAX_SPORTS = 5

// ---------- 운동 가능 시간: 요일 × 시간대 칸 ----------

const BLOCKS = [
  { start: '06:00', end: '09:00', label: '아침', range: '6-9시' },
  { start: '09:00', end: '12:00', label: '오전', range: '9-12시' },
  { start: '12:00', end: '15:00', label: '점심', range: '12-15시' },
  { start: '15:00', end: '18:00', label: '오후', range: '15-18시' },
  { start: '18:00', end: '21:00', label: '저녁', range: '18-21시' },
  { start: '21:00', end: '23:59', label: '밤', range: '21-24시' },
]

const cellKey = (day: DayOfWeek, blockIndex: number) => `${day}-${blockIndex}`

/** 저장된 시간대와 조금이라도 겹치는 칸을 선택된 것으로 표시한다. */
function toCells(times: AvailableTime[]) {
  const cells: string[] = []
  for (const time of times) {
    BLOCKS.forEach((block, index) => {
      if (time.startTime < block.end && block.start < time.endTime) cells.push(cellKey(time.dayOfWeek, index))
    })
  }
  return [...new Set(cells)].sort()
}

/** 같은 요일에 이어진 칸은 하나의 시간대로 합쳐서 저장한다. (서버는 겹치는 시간대를 거부함) */
function toAvailableTimes(cells: string[]): AvailableTime[] {
  const selected = new Set(cells)
  const result: AvailableTime[] = []
  for (const { value: day } of DAYS) {
    let start = -1
    for (let index = 0; index <= BLOCKS.length; index++) {
      const on = index < BLOCKS.length && selected.has(cellKey(day, index))
      if (on && start < 0) start = index
      if (!on && start >= 0) {
        result.push({ dayOfWeek: day, startTime: BLOCKS[start].start, endTime: BLOCKS[index - 1].end })
        start = -1
      }
    }
  }
  return result
}

// ---------- 폼 상태 ----------

interface Draft {
  nickname: string
  email: string
  bio: string
  gender: Gender | null
  birthDate: string
  searchRadiusKm: number
  location: LocationInput | null
  sports: [number, SkillLevel][]
  cells: string[]
}

function draftFrom(me: MyProfile): Draft {
  return {
    nickname: me.nickname,
    email: me.email ?? '',
    bio: me.bio ?? '',
    gender: me.gender,
    birthDate: me.birthDate ?? '',
    searchRadiusKm: me.searchRadiusKm,
    location: me.location,
    sports: me.sports.map((sport) => [sport.sportId, sport.skillLevel]),
    cells: toCells(me.availableTimes),
  }
}

type DraftErrors = Partial<Record<keyof Draft, string>>

function validate(draft: Draft): DraftErrors {
  const errors: DraftErrors = {}
  if (draft.nickname.trim().length < 2) errors.nickname = '닉네임은 2자 이상이어야 해요'
  if (!draft.email.trim()) errors.email = '이메일을 입력해 주세요 (아이디·비밀번호 찾기에 사용돼요)'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.email.trim())) errors.email = '올바른 이메일 형식이 아니에요'
  if (!draft.birthDate) errors.birthDate = '생년월일을 입력해 주세요'
  if (!draft.gender) errors.gender = '성별을 선택해 주세요'
  if (!draft.location) errors.location = '목록에서 활동 지역을 선택해 주세요'
  return errors
}

const SERVER_ERROR_FIELD: Record<string, keyof Draft> = {
  DUPLICATE_NICKNAME: 'nickname',
  DUPLICATE_EMAIL: 'email',
  EMAIL_REQUIRED: 'email',
  INVALID_BIRTH_DATE: 'birthDate',
}

// ---------- 페이지 ----------

export function ProfilePage() {
  const { data: me } = useMe()
  if (!me) return <PageLoader />
  // 저장하면 me가 바뀌므로 key로 폼을 서버 값 기준으로 다시 만든다
  return <ProfileForm key={JSON.stringify(me)} me={me} />
}

function ProfileForm({ me }: { me: MyProfile }) {
  const toast = useToast()
  const { logout } = useAuth()
  const saveProfile = useSaveProfile()
  const { data: allSports } = useSports()
  const [params, setParams] = useSearchParams()
  const welcome = params.get('welcome') === '1'

  const initial = useMemo(() => draftFrom(me), [me])
  const [draft, setDraft] = useState<Draft>(initial)
  const [showErrors, setShowErrors] = useState(false)
  const [serverErrors, setServerErrors] = useState<DraftErrors>({})

  const errors = { ...(showErrors ? validate(draft) : {}), ...serverErrors }
  const dirty = JSON.stringify(draft) !== JSON.stringify(initial)

  /** 항상 최신 상태를 기준으로 바꾼다. (렌더링 시점의 draft를 쓰면 연달아 누를 때 앞의 변경이 사라진다) */
  const update = <K extends keyof Draft>(key: K, next: (current: Draft[K]) => Draft[K]) => {
    setDraft((current) => ({ ...current, [key]: next(current[key]) }))
    setServerErrors(({ [key]: _removed, ...rest }) => rest)
  }
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => update(key, () => value)

  const toggleSport = (sportId: number) => {
    if (!draft.sports.some(([id]) => id === sportId) && draft.sports.length >= MAX_SPORTS) {
      toast(`운동 종목은 최대 ${MAX_SPORTS}개까지 고를 수 있어요`, 'error')
      return
    }
    update('sports', (sports) => {
      if (sports.some(([id]) => id === sportId)) return sports.filter(([id]) => id !== sportId)
      return sports.length >= MAX_SPORTS ? sports : [...sports, [sportId, 'BEGINNER']]
    })
  }

  const toggleCell = (key: string) =>
    update('cells', (cells) => (cells.includes(key) ? cells.filter((cell) => cell !== key) : [...cells, key].sort()))

  const save = async () => {
    setShowErrors(true)
    if (Object.keys(validate(draft)).length > 0 || !draft.gender || !draft.location) {
      toast('입력하지 않은 항목이 있어요', 'error')
      return
    }
    try {
      await saveProfile.mutateAsync({
        nickname: draft.nickname.trim(),
        email: draft.email.trim(),
        bio: draft.bio,
        gender: draft.gender,
        birthDate: draft.birthDate,
        searchRadiusKm: draft.searchRadiusKm,
        location: draft.location,
        sports: draft.sports.map(([sportId, skillLevel]) => ({ sportId, skillLevel })),
        availableTimes: toAvailableTimes(draft.cells),
      })
      toast('프로필을 저장했어요')
      if (welcome) setParams({})
    } catch (e) {
      if (e instanceof ApiError && SERVER_ERROR_FIELD[e.code]) {
        setServerErrors({ [SERVER_ERROR_FIELD[e.code]]: e.message })
      }
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 pb-36 md:px-8 md:py-10 md:pb-32">
      <PageHeader title="내 프로필" description="정보를 채울수록 잘 맞는 운동 메이트를 추천받아요" />

      {welcome && (
        <div className="mb-6 flex animate-fade-up items-start gap-3 rounded-3xl bg-linear-to-r from-brand-500 to-rose-500 p-5 text-white shadow-lift">
          <PartyPopper className="mt-0.5 size-6 shrink-0" />
          <div className="flex-1">
            <p className="font-bold">가입을 환영해요, {me.nickname}님!</p>
            <p className="mt-1 text-sm text-white/85">운동 종목과 운동 가능 시간까지 등록하면 더 잘 맞는 메이트를 추천받을 수 있어요.</p>
          </div>
          <button onClick={() => setParams({})} className="cursor-pointer text-sm font-semibold text-white/80 hover:text-white">
            닫기
          </button>
        </div>
      )}

      <Card className="mb-6 flex items-center gap-4 p-6">
        <Avatar id={me.id} name={draft.nickname || me.nickname} imageUrl={me.profileImageUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-xl font-extrabold">{draft.nickname || me.nickname}</p>
          <p className="flex items-center gap-1 truncate text-sm text-ink-500">
            <AtSign className="size-3.5" />
            {me.loginId}
          </p>
        </div>
        <div className="text-center">
          <div className="flex items-center gap-1 text-2xl font-extrabold text-brand-600">
            <Thermometer className="size-5" />
            {me.mannerScore}°
          </div>
          <p className="text-xs font-medium text-ink-400">매너 온도</p>
        </div>
      </Card>

      <div className="space-y-6">
        <Section title="기본 정보" description="다른 사람에게는 닉네임, 나이대, 성별, 자기소개만 보여요">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="아이디" hint="아이디는 바꿀 수 없어요">
              <Input value={me.loginId} disabled className="cursor-not-allowed text-ink-500" />
            </Field>
            <Field
              label="이메일"
              hint={me.email ? '아이디·비밀번호 찾기에 사용돼요' : '⚠️ 이메일을 등록해야 아이디·비밀번호를 찾을 수 있어요'}
              error={errors.email}
            >
              <Input type="email" value={draft.email} onChange={(e) => set('email', e.target.value)} placeholder="you@example.com" />
            </Field>
            <Field label="닉네임" error={errors.nickname}>
              <Input value={draft.nickname} onChange={(e) => set('nickname', e.target.value)} maxLength={20} />
            </Field>
            <Field label="생년월일">
              <BirthDateInput value={draft.birthDate} onChange={(iso) => set('birthDate', iso)} error={errors.birthDate} />
            </Field>
            <div className="sm:col-span-2">
              <Field label="자기소개">
                <Textarea
                  rows={3}
                  maxLength={500}
                  value={draft.bio}
                  onChange={(e) => set('bio', e.target.value)}
                  placeholder="주 3회 헬스, 주말엔 러닝해요. 초보도 환영!"
                />
              </Field>
            </div>
            <Field label="성별" error={errors.gender}>
              <div>
                <Segmented<Gender>
                  options={[
                    { value: 'MALE', label: '남성' },
                    { value: 'FEMALE', label: '여성' },
                  ]}
                  value={draft.gender}
                  onChange={(value) => set('gender', value)}
                />
              </div>
            </Field>
          </div>
        </Section>

        <Section title="활동 지역" description="정확한 위치는 공개되지 않고, 거리는 0.5km 단위로만 보여요" done={!!draft.location}>
          <LocationSearch value={draft.location} onChange={(location) => set('location', location)} error={errors.location} />
          <div className="mt-6">
            <Field label={`검색 반경 ${draft.searchRadiusKm}km`} hint="추천받을 운동 메이트의 최대 거리">
              <input
                type="range"
                min={1}
                max={50}
                value={draft.searchRadiusKm}
                onChange={(e) => set('searchRadiusKm', Number(e.target.value))}
                className="mt-2 w-full cursor-pointer accent-brand-500"
              />
            </Field>
          </div>
        </Section>

        <Section title="운동 종목" description={`하는 운동과 실력을 골라 주세요 (최대 ${MAX_SPORTS}개)`} done={draft.sports.length > 0}>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
            {allSports?.map((sport) => {
              const active = draft.sports.some(([id]) => id === sport.id)
              return (
                <button
                  key={sport.id}
                  type="button"
                  onClick={() => toggleSport(sport.id)}
                  aria-pressed={active}
                  className={clsx(
                    'flex cursor-pointer flex-col items-center gap-1 rounded-2xl py-3 text-sm font-semibold transition-all',
                    active ? 'bg-brand-50 text-brand-700 ring-2 ring-brand-400' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-100 hover:ring-ink-300',
                  )}
                >
                  <span className="text-2xl">{sportEmoji(sport.code)}</span>
                  {sport.name}
                </button>
              )
            })}
          </div>

          {draft.sports.length > 0 && (
            <div className="mt-5 space-y-2">
              {draft.sports.map(([sportId, level]) => {
                const sport = allSports?.find((s) => s.id === sportId)
                return (
                  <div key={sportId} className="flex items-center justify-between gap-3 rounded-2xl bg-ink-50 px-4 py-2.5">
                    <span className="font-semibold">
                      {sport && sportEmoji(sport.code)} {sport?.name}
                    </span>
                    <Segmented<SkillLevel>
                      size="sm"
                      options={SKILL_LEVELS.map((value) => ({ value, label: SKILL_LABEL[value] }))}
                      value={level}
                      onChange={(value) => update('sports', (sports) => sports.map(([id, l]) => [id, id === sportId ? value : l]))}
                    />
                  </div>
                )
              })}
            </div>
          )}
        </Section>

        <Section title="운동 가능 시간" description="시간이 많이 겹칠수록 매칭 점수가 올라가요" done={draft.cells.length > 0}>
          <div className="-mx-2 overflow-x-auto px-2">
            <div className="grid min-w-[480px] grid-cols-[64px_repeat(7,1fr)] gap-1.5">
              <div />
              {DAYS.map((day) => (
                <div
                  key={day.value}
                  className={clsx(
                    'pb-1 text-center text-sm font-bold',
                    day.value === 'SATURDAY' && 'text-sky-600',
                    day.value === 'SUNDAY' && 'text-rose-500',
                  )}
                >
                  {day.short}
                </div>
              ))}
              {BLOCKS.map((block, index) => (
                <div key={block.start} className="contents">
                  <div className="flex flex-col justify-center pr-1 text-right">
                    <span className="text-xs font-bold text-ink-700">{block.label}</span>
                    <span className="text-[10px] text-ink-400">{block.range}</span>
                  </div>
                  {DAYS.map((day) => {
                    const key = cellKey(day.value, index)
                    const on = draft.cells.includes(key)
                    return (
                      <button
                        key={key}
                        type="button"
                        onClick={() => toggleCell(key)}
                        aria-pressed={on}
                        aria-label={`${day.short}요일 ${block.label}`}
                        className={clsx(
                          'h-10 cursor-pointer rounded-xl transition-all',
                          on ? 'bg-linear-to-br from-brand-400 to-brand-500 shadow-sm' : 'bg-ink-100 hover:bg-ink-200',
                        )}
                      />
                    )
                  })}
                </div>
              ))}
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <span className="text-sm text-ink-500">{draft.cells.length}칸 선택됨</span>
            {draft.cells.length > 0 && (
              <Button variant="ghost" size="sm" onClick={() => set('cells', [])}>
                모두 지우기
              </Button>
            )}
          </div>
        </Section>
      </div>

      {/* 모바일에는 사이드바가 없어서 여기서 로그아웃한다 */}
      <div className="mt-8 flex justify-center md:hidden">
        <Button variant="ghost" onClick={() => void logout()}>
          <LogOut className="size-4" /> 로그아웃
        </Button>
      </div>

      {/* 전체 저장 바: 바뀐 내용이 있을 때만 떠오른다 */}
      <div
        className={clsx(
          'fixed inset-x-0 bottom-20 z-30 px-4 transition-all duration-300 md:bottom-6 md:left-64',
          dirty ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-4 opacity-0',
        )}
      >
        <div className="mx-auto flex max-w-3xl items-center gap-3 rounded-2xl bg-ink-900 p-3 pl-5 text-white shadow-lift">
          <span className="flex-1 text-sm font-medium">저장하지 않은 변경사항이 있어요</span>
          <Button
            variant="ghost"
            size="sm"
            className="text-white/80 hover:bg-white/10 hover:text-white"
            onClick={() => {
              setDraft(initial)
              setServerErrors({})
              setShowErrors(false)
            }}
          >
            <RotateCcw className="size-4" /> 되돌리기
          </Button>
          <Button size="sm" onClick={save} loading={saveProfile.isPending}>
            <Save className="size-4" /> 저장하기
          </Button>
        </div>
      </div>
    </div>
  )
}

function Section({ title, description, done, children }: { title: string; description: string; done?: boolean; children: ReactNode }) {
  return (
    <Card className="p-6">
      <div className="mb-5 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold">{title}</h2>
          <p className="mt-0.5 text-sm text-ink-500">{description}</p>
        </div>
        {done !== undefined && (
          <span className={clsx('shrink-0 rounded-full px-2.5 py-1 text-xs font-bold', done ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
            {done ? '완료' : '필요'}
          </span>
        )}
      </div>
      {children}
    </Card>
  )
}
