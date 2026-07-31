interface Props {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}

export default function ToggleSwitch({ label, checked, onChange }: Props) {
  return (
    <label className="switch">
      <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} />
      <span className="track" />
      <span className="switch-label">{label}</span>
    </label>
  )
}
