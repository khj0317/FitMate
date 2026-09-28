import clsx from 'clsx'
import { Crosshair, MapPin, PartyPopper, Thermometer } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { Button, Card, Field, Input, PageHeader, PageLoader, Segmented, Textarea } from '../components/ui'
import { errorMessage } from '../lib/api'
import { DAYS, SKILL_LABEL, SKILL_LEVELS, sportEmoji } from '../lib/format'
import { useMe, useSports, useUpdateProfile } from '../lib/queries'
import type { AvailableTime, DayOfWeek, Gender, MyProfile, SkillLevel } from '../lib/types'
import { useToast } from '../providers/ToastProvider'

export function ProfilePage() {
  const { data: me } = useMe()
  const [params, setParams] = useSearchParams()
  const welcome = params.get('welcome') === '1'

  if (!me) return <PageLoader />

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 md:px-8 md:py-10">
      <PageHeader title="내 프로필" description="정보를 채울수록 잘 맞는 운동 메이트를 추천받아요" />

      {welcome && (
        <div className="mb-6 flex animate-fade-up items-start gap-3 rounded-3xl bg-linear-to-r from-brand-500 to-rose-500 p-5 text-white shadow-lift">
          <PartyPopper className="mt-0.5 size-6 shrink-0" />
          <div className="flex-1">
            <p className="font-bold">가입을 환영해요, {me.nickname}님!</p>
            <p className="mt-1 text-sm text-white/85">활동 지역과 운동 종목만 등록하면 바로 추천을 받을 수 있어요.</p>
          </div>
          <button onClick={() => setParams({})} className="cursor-pointer text-sm font-semibold text-white/80 hover:text-white">
            닫기
          </button>
        </div>
      )}

      <Card className="mb-6 flex items-center gap-4 p-6">
        <Avatar id={me.id} name={me.nickname} imageUrl={me.profileImageUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-xl font-extrabold">{me.nickname}</p>
          <p className="truncate text-sm text-ink-500">{me.email}</p>
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
        <BasicSection me={me} />
        <LocationSection me={me} />
        <SportsSection me={me} />
        <TimesSection me={me} />
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
          <span className={clsx('rounded-full px-2.5 py-1 text-xs font-bold', done ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>
            {done ? '완료' : '필요'}
          </span>
        )}
      </div>
      {children}
    </Card>
  )
}

function useSaveToast() {
  const toast = useToast()
  return async (action: () => Promise<unknown>, message: string) => {
    try {
      await action()
      toast(message)
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }
}

// ---------- 기본 정보 ----------

function BasicSection({ me }: { me: MyProfile }) {
  const { basic } = useUpdateProfile()
  const save = useSaveToast()
  const [nickname, setNickname] = useState(me.nickname)
  const [bio, setBio] = useState(me.bio ?? '')
  const [gender, setGender] = useState<Gender | null>(me.gender)
  const [birthYear, setBirthYear] = useState(me.birthYear?.toString() ?? '')
  const [radius, setRadius] = useState(me.searchRadiusKm)

  const submit = () =>
    save(
      () =>
        basic.mutateAsync({
          nickname: nickname !== me.nickname ? nickname : undefined,
          bio,
          gender: gender ?? undefined,
          birthYear: birthYear ? Number(birthYear) : undefined,
          searchRadiusKm: radius,
        }),
      '기본 정보를 저장했어요',
    )

  return (
    <Section title="기본 정보" description="다른 사람에게는 닉네임, 나이대, 성별, 자기소개만 보여요">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="닉네임">
          <Input value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} />
        </Field>
        <Field label="출생 연도" hint="다른 사람에게는 '20대'처럼 나이대만 보여요">
          <Input type="number" inputMode="numeric" value={birthYear} onChange={(e) => setBirthYear(e.target.value)} placeholder="1998" min={1920} max={2015} />
        </Field>
        <div className="sm:col-span-2">
          <Field label="자기소개">
            <Textarea rows={3} maxLength={500} value={bio} onChange={(e) => setBio(e.target.value)} placeholder="주 3회 헬스, 주말엔 러닝해요. 초보도 환영!" />
          </Field>
        </div>
        <Field label="성별">
          <div>
            <Segmented<Gender>
              options={[
                { value: 'MALE', label: '남성' },
                { value: 'FEMALE', label: '여성' },
              ]}
              value={gender}
              onChange={setGender}
            />
          </div>
        </Field>
        <Field label={`검색 반경 ${radius}km`} hint="추천받을 운동 메이트의 최대 거리">
          <input
            type="range"
            min={1}
            max={50}
            value={radius}
            onChange={(e) => setRadius(Number(e.target.value))}
            className="mt-3 w-full cursor-pointer accent-brand-500"
          />
        </Field>
      </div>
      <div className="mt-6 flex justify-end">
        <Button onClick={submit} loading={basic.isPending}>저장</Button>
      </div>
    </Section>
  )
}

// ---------- 활동 지역 ----------

const PRESETS = [
  { label: '성수역', latitude: 37.5446, longitude: 127.0559, areaName: '서울 성동구 성수동' },
  { label: '강남역', latitude: 37.4979, longitude: 127.0276, areaName: '서울 강남구 역삼동' },
  { label: '홍대입구', latitude: 37.5572, longitude: 126.9245, areaName: '서울 마포구 서교동' },
  { label: '잠실', latitude: 37.5133, longitude: 127.1001, areaName: '서울 송파구 잠실동' },
  { label: '여의도', latitude: 37.5219, longitude: 126.9245, areaName: '서울 영등포구 여의도동' },
]

function LocationSection({ me }: { me: MyProfile }) {
  const { location } = useUpdateProfile()
  const save = useSaveToast()
  const toast = useToast()
  const [point, setPoint] = useState(me.location ? { latitude: me.location.latitude, longitude: me.location.longitude } : null)
  const [areaName, setAreaName] = useState(me.location?.areaName ?? '')
  const [locating, setLocating] = useState(false)

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      toast('이 브라우저는 위치 정보를 지원하지 않아요', 'error')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setPoint({ latitude: Number(coords.latitude.toFixed(6)), longitude: Number(coords.longitude.toFixed(6)) })
        if (!areaName) setAreaName('내 동네')
        setLocating(false)
        toast('현재 위치를 가져왔어요. 동네 이름을 확인하고 저장해 주세요')
      },
      () => {
        setLocating(false)
        toast('위치 권한을 허용하거나 아래 주요 지역을 선택해 주세요', 'error')
      },
      { enableHighAccuracy: false, timeout: 10_000 },
    )
  }

  const submit = () => {
    if (!point || !areaName.trim()) return
    void save(() => location.mutateAsync({ ...point, areaName: areaName.trim() }), '활동 지역을 저장했어요')
  }

  return (
    <Section title="활동 지역" description="정확한 위치는 공개되지 않고, 거리는 0.5km 단위로만 보여요" done={!!me.location}>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={useCurrentLocation} loading={locating}>
          <Crosshair className="size-4" /> 현재 위치 사용
        </Button>
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            onClick={() => {
              setPoint({ latitude: preset.latitude, longitude: preset.longitude })
              setAreaName(preset.areaName)
            }}
            className={clsx(
              'cursor-pointer rounded-xl px-3 py-2 text-sm font-semibold transition-colors',
              point?.latitude === preset.latitude && point.longitude === preset.longitude
                ? 'bg-brand-500 text-white'
                : 'bg-ink-100 text-ink-600 hover:bg-ink-200',
            )}
          >
            {preset.label}
          </button>
        ))}
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
        <Field label="동네 이름" hint="다른 사람에게 보이는 활동 지역 이름">
          <Input value={areaName} onChange={(e) => setAreaName(e.target.value)} placeholder="서울 성동구 성수동" maxLength={100} />
        </Field>
        <Button onClick={submit} loading={location.isPending} disabled={!point || !areaName.trim()} className="sm:mb-[22px]">
          저장
        </Button>
      </div>
      {point && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-ink-400">
          <MapPin className="size-3.5" />
          {point.latitude.toFixed(4)}, {point.longitude.toFixed(4)}
        </p>
      )}
    </Section>
  )
}

// ---------- 운동 종목 ----------

const MAX_SPORTS = 5

function SportsSection({ me }: { me: MyProfile }) {
  const { data: sports } = useSports()
  const { sports: saveSports } = useUpdateProfile()
  const save = useSaveToast()
  const toast = useToast()
  const [selected, setSelected] = useState<Map<number, SkillLevel>>(
    () => new Map(me.sports.map((sport) => [sport.sportId, sport.skillLevel])),
  )

  const toggle = (sportId: number) => {
    setSelected((current) => {
      const next = new Map(current)
      if (next.has(sportId)) {
        next.delete(sportId)
      } else if (next.size >= MAX_SPORTS) {
        toast(`운동 종목은 최대 ${MAX_SPORTS}개까지 고를 수 있어요`, 'error')
      } else {
        next.set(sportId, 'BEGINNER')
      }
      return next
    })
  }

  const submit = () =>
    save(
      () => saveSports.mutateAsync([...selected].map(([sportId, skillLevel]) => ({ sportId, skillLevel }))),
      '운동 종목을 저장했어요',
    )

  return (
    <Section title="운동 종목" description={`하는 운동과 실력을 골라 주세요 (최대 ${MAX_SPORTS}개)`} done={me.sports.length > 0}>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
        {sports?.map((sport) => {
          const active = selected.has(sport.id)
          return (
            <button
              key={sport.id}
              onClick={() => toggle(sport.id)}
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

      {selected.size > 0 && (
        <div className="mt-5 space-y-2">
          {[...selected].map(([sportId, level]) => {
            const sport = sports?.find((s) => s.id === sportId)
            return (
              <div key={sportId} className="flex items-center justify-between gap-3 rounded-2xl bg-ink-50 px-4 py-2.5">
                <span className="font-semibold">
                  {sport && sportEmoji(sport.code)} {sport?.name}
                </span>
                <Segmented<SkillLevel>
                  size="sm"
                  options={SKILL_LEVELS.map((value) => ({ value, label: SKILL_LABEL[value] }))}
                  value={level}
                  onChange={(value) => setSelected((current) => new Map(current).set(sportId, value))}
                />
              </div>
            )
          })}
        </div>
      )}

      <div className="mt-6 flex justify-end">
        <Button onClick={submit} loading={saveSports.isPending}>저장</Button>
      </div>
    </Section>
  )
}

// ---------- 운동 가능 시간 ----------

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
function initialCells(times: AvailableTime[]) {
  const cells = new Set<string>()
  for (const time of times) {
    BLOCKS.forEach((block, index) => {
      if (time.startTime < block.end && block.start < time.endTime) cells.add(cellKey(time.dayOfWeek, index))
    })
  }
  return cells
}

/** 같은 요일에 이어진 칸은 하나의 시간대로 합쳐서 저장한다. (서버는 겹치는 시간대를 거부함) */
function toAvailableTimes(cells: Set<string>): AvailableTime[] {
  const result: AvailableTime[] = []
  for (const { value: day } of DAYS) {
    let start: number | null = null
    BLOCKS.forEach((_, index) => {
      const on = cells.has(cellKey(day, index))
      if (on && start === null) start = index
      const isLast = index === BLOCKS.length - 1
      if (start !== null && (!on || isLast)) {
        const endIndex = on && isLast ? index : index - 1
        result.push({ dayOfWeek: day, startTime: BLOCKS[start].start, endTime: BLOCKS[endIndex].end })
        start = null
      }
    })
  }
  return result
}

function TimesSection({ me }: { me: MyProfile }) {
  const { times } = useUpdateProfile()
  const save = useSaveToast()
  const [cells, setCells] = useState(() => initialCells(me.availableTimes))

  const toggle = (key: string) =>
    setCells((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })

  const submit = () => save(() => times.mutateAsync(toAvailableTimes(cells)), '운동 가능 시간을 저장했어요')

  return (
    <Section title="운동 가능 시간" description="시간이 많이 겹칠수록 매칭 점수가 올라가요" done={me.availableTimes.length > 0}>
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
                const on = cells.has(key)
                return (
                  <button
                    key={key}
                    onClick={() => toggle(key)}
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
      <div className="mt-6 flex items-center justify-between gap-3">
        <span className="text-sm text-ink-500">{cells.size}칸 선택됨</span>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => setCells(new Set())}>초기화</Button>
          <Button onClick={submit} loading={times.isPending}>저장</Button>
        </div>
      </div>
    </Section>
  )
}
