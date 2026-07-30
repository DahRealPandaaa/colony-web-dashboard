interface Props { size?: number; strokeWidth?: number }

export default function Shield({ size = 20, strokeWidth = 1.8 }: Props) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor"
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l7.5 3v5.7c0 4.5-3.2 8.4-7.5 9.8-4.3-1.4-7.5-5.3-7.5-9.8V6z" />
    </svg>
  )
}
