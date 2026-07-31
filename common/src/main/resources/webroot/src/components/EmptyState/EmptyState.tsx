import type { ReactNode } from 'react'

interface Props {
  title?: string
  className?: string
  children?: ReactNode
}

/** The shared "nothing here" block, used for both empty data and empty filter results. */
export default function EmptyState({ title, className, children }: Props) {
  return (
    <div className={`empty${className ? ` ${className}` : ''}`}>
      {title && <p className="empty-title">{title}</p>}
      {children}
    </div>
  )
}
