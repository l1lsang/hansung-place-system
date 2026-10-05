export const seatStatusLabels = {
  free: '예약 가능',
  unavailable: '예약 불가',
  disabled: '운영 중지',
  error: '확인 실패',
  loading: '확인 중',
}
export type SeatStatus = keyof typeof seatStatusLabels
