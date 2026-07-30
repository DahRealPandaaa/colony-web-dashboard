const KEYS: { swatch: string; label: string }[] = [
  { swatch: 'centre', label: 'Town hall' },
  { swatch: 'build', label: 'Being built' },
  { swatch: 'worker', label: 'Worker' },
  { swatch: 'idle', label: 'Unemployed' },
  { swatch: 'child', label: 'Child' },
  { swatch: 'asleep', label: 'Not loaded' },
]

/** What every marker colour on the map means. */
export default function MapLegend() {
  return (
    <div className="map-legend">
      {KEYS.map(k => (
        <span key={k.swatch} className="map-key">
          <i className={`map-swatch ${k.swatch}`} />{k.label}
        </span>
      ))}
    </div>
  )
}
