import type { ReactNode } from 'react'

interface Props {
  label: string
  value: ReactNode
  /** Extra classes on the headline number, for the good/warn/bad colouring. */
  valueClass?: string
  sub?: ReactNode
  subClass?: string
  /** A meter or anything else that belongs under the number. */
  children?: ReactNode
}

export default function StatTile({ label, value, valueClass, sub, subClass, children }: Props) {
  return (
    <div className="tile">
      <div className="tile-label">{label}</div>
      <div className={`tile-value${valueClass ? ` ${valueClass}` : ''}`}>{value}</div>
      {sub !== undefined && sub !== null && (
        <div className={`tile-sub${subClass ? ` ${subClass}` : ''}`}>{sub}</div>
      )}
      {children}
    </div>
  )
}
