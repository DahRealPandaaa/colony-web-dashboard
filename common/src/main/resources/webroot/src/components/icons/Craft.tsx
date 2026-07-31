interface Props { size?: number; strokeWidth?: number }

/** The pickaxe-and-plank mark that flags an item a colony worker can craft. */
export default function Craft({ size = 10, strokeWidth = 2.5 }: Props) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor"
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 3l7 7-4 4-7-7z" /><path d="M11 7L3 15v6h6l8-8" />
    </svg>
  )
}
