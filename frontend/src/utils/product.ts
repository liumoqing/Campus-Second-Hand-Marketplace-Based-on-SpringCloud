export function conditionLabel(level?: number | null) {
  if (!level) return ''
  return level >= 10 ? '全新' : `${level}成新`
}
