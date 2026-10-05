import libraryPhoto from '../components/library-hero.jpg'
import readingPlan from '../components/reading-room-map.png'
import basePlan from '../components/sangsang-base-map.png'
import parkPlan from '../components/sansang-base-plus.png'
import type { Space } from '../types/api'

// Display media provided by the project owner, never a source of booking policy or occupancy.
export const campusPhoto = libraryPhoto
export const facilities = [
  {
    id: 'base',
    title: '상상베이스',
    english: 'SANGSANG BASE',
    image: basePlan,
    plan: basePlan,
    description: '생각을 나누고, 함께 만들어가는 공간',
    alt: '상상베이스 프로젝트룸과 세미나실 배치도',
  },
  {
    id: 'park',
    title: '상상파크 플러스',
    english: 'SANGSANG PARK PLUS',
    image: parkPlan,
    plan: parkPlan,
    description: '새로운 아이디어가 만나는 소모임 공간',
    alt: '상상파크 플러스 소모임 공간 배치도',
  },
  {
    id: 'reading',
    title: '집중열람실',
    english: 'FOCUS READING ROOM',
    image: readingPlan,
    plan: readingPlan,
    description: '오롯이 나의 배움에 집중하는 시간',
    alt: '001번부터 162번까지 표시된 집중열람실 좌석 배치도',
  },
  {
    id: 'library',
    title: '학술정보관',
    english: 'ACADEMIC INFORMATION CENTER',
    image: libraryPhoto,
    plan: null,
    description: '함께 배우고 성장하는 학습 공간',
    alt: '한성대학교 학술정보관 건물 전경',
  },
] as const
export function facilityFor(space: Space) {
  const key = `${space.venue} ${space.spaceCode} ${space.name}`.toUpperCase().replaceAll('-', '_')
  if (key.includes('READING') || key.includes('집중열람실')) return facilities[2]
  if (key.includes('PARK') || key.includes('PLUS') || key.includes('상상파크')) return facilities[1]
  if (
    key.includes('SANGSANG_BASE') ||
    key.includes('상상베이스') ||
    space.venue.toUpperCase() === 'BASE'
  )
    return facilities[0]
  if (key.includes('LIBRARY') || key.includes('학술정보관')) return facilities[3]
  return undefined
}
