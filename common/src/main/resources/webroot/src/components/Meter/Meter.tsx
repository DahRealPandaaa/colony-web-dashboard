/**
 * The two bar primitives from the old stylesheet.
 *
 * `.progress` is the blue build/research bar; `.meter` is the tinted stat bar whose colour comes
 * from a variant class (hp, food, happy, xp, done).
 */

interface ProgressProps {
  /** 0-100. */
  pct: number
  big?: boolean
  className?: string
}

export function Progress({ pct, big, className }: ProgressProps) {
  return (
    <div className={`progress${big ? ' big' : ''}${className ? ` ${className}` : ''}`}>
      <span style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
    </div>
  )
}

interface MeterProps {
  /** 0-100. */
  pct: number
  variant: 'hp' | 'food' | 'happy' | 'xp' | 'done'
  className?: string
}

export function Meter({ pct, variant, className }: MeterProps) {
  return (
    <div className={`meter ${variant}${className ? ` ${className}` : ''}`}>
      <span style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
    </div>
  )
}
