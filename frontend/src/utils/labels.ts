import type { Space } from '../types/api'
const names: Record<string, string> = {
  READING_ROOM: '집중열람실',
  STUDY_ROOM: '스터디룸',
  MEETING_ROOM: '회의실',
  SANGSANG_BASE: '상상베이스',
  SANGSANG_PARK_PLUS: '상상파크 플러스',
  LIBRARY: '학술정보관',
  STUDENT_CENTER: '학생회관',
}
export const label = (value: string) => names[value] ?? value
export function capacity(space: Space) {
  if (space.minCapacity && space.maxCapacity) return `${space.minCapacity}~${space.maxCapacity}명`
  if (space.maxCapacity) return `최대 ${space.maxCapacity}명`
  if (space.minCapacity) return `${space.minCapacity}명 이상`
  return '인원 안내 확인'
}
