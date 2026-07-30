import Search from '../icons/Search'

interface Props {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  className?: string
  iconSize?: number
}

export default function SearchInput({ value, onChange, placeholder = 'Search', className, iconSize = 15 }: Props) {
  return (
    <div className={`searchbox${className ? ` ${className}` : ''}`}>
      <Search size={iconSize} />
      <input
        type="search"
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
      />
    </div>
  )
}
