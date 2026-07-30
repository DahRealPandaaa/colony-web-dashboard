import { textureUrl } from '../../api'
import { buildingArt, postIconFallback } from '../../hooks/useIcons'
import type { Post } from '../../types/combat'

interface Props {
  post: Post
  status: 'ok' | 'deliver' | 'missing'
}

export default function GuardPostCard({ post: p, status }: Props) {
  return (
    <div className="card p-3! flex items-center gap-3">
      <img
        className="w-11 h-11 object-contain pixelated rounded-lg bg-black/25 border border-line p-0.5 shrink-0"
        loading="lazy"
        src={buildingArt(p) || textureUrl(p.blockId || 'minecolonies:blockhutguardtower')}
        alt={p.name}
        onError={e => postIconFallback(e.currentTarget, p)}
      />
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-[14.5px] truncate">{p.name}</div>
        <div className="text-[12.5px] text-slate-400">
          Level {p.level} · {p.assigned}/{p.capacity} staffed
        </div>
      </div>
      <span className={`sdot shrink-0 ${status}`}><i /></span>
    </div>
  )
}
