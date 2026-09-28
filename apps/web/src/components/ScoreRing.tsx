/** 매칭 점수(0~100)를 원형 게이지로 보여준다. 점수가 높을수록 진한 브랜드 색. */
export function ScoreRing({ score, size = 64 }: { score: number; size?: number }) {
  const stroke = 6
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - Math.min(score, 100) / 100)
  const gradientId = `score-${score}-${size}`

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }} aria-label={`매칭 점수 ${score}점`}>
      <svg width={size} height={size} className="-rotate-90">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={score >= 60 ? '#ff7a45' : '#ffc9aa'} />
            <stop offset="100%" stopColor={score >= 60 ? '#f03e0a' : '#ff7a45'} />
          </linearGradient>
        </defs>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#f0eeeb" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={`url(#${gradientId})`}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 0.8s ease-out' }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center leading-none">
        <span className="text-lg font-extrabold text-ink-900">{score}</span>
        <span className="mt-0.5 text-[10px] font-semibold text-ink-400">점</span>
      </div>
    </div>
  )
}
