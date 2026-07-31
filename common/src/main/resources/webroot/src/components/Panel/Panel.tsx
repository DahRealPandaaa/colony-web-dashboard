import type { ReactNode } from 'react'

interface Props {
  title: string
  subtitle?: string
  count?: number
  icon?: ReactNode
  actions?: ReactNode
  className?: string
  children: ReactNode
}

/** Reusable panel section — reduces the repeated panel-head / panel-body markup. */
export default function Panel({ title, subtitle, count, icon, actions, className, children }: Props) {
  return (
    <section className={`panel${className ? ` ${className}` : ''}`}>
      <div className="panel-head">
        <div>
          <h2 className="panel-title">
            {icon}
            {title}
          </h2>
          {subtitle && <p className="panel-sub">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {actions}
          {count !== undefined && <span className="chip">{count}</span>}
        </div>
      </div>
      <div className="panel-body">
        {children}
      </div>
    </section>
  )
}
