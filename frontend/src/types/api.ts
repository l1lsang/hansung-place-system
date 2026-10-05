export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
export interface User {
  id: number
  studentId: string
  email: string
  name: string
  role: string
}
export interface Space {
  id: number
  spaceCode: string
  name: string
  type: string
  location: string | null
  minCapacity: number | null
  maxCapacity: number | null
  venue: string
  facilities: string | null
  bookingEnabled: boolean
}
export interface Seat {
  id: number
  spaceId: number
  seatNumber: string
  status: string
}
export interface TimeRange {
  startTime: string
  endTime: string
}
export interface Availability extends TimeRange {
  spaceId: number
  seatId: number | null
  bookingEnabled: boolean
  policyScope: 'OCCUPANCY_ONLY' | 'OPERATING_HOURS_AND_OCCUPANCY'
  available: TimeRange[]
}
export interface Member {
  studentId: string
  name: string
}
export interface ReservationInput extends TimeRange {
  spaceId: number
  seatId: number | null
  purpose: string | null
  kind: 'BOOKING' | 'SEAT_USE'
  members: Member[]
}
export interface Reservation extends ReservationInput {
  id: number
  userId: number
  status: 'UPCOMING' | 'COMPLETED' | 'CANCELLED'
  createdAt: string
  cancelledAt: string | null
}
export interface SpaceInput {
  spaceCode: string
  name: string
  type: string
  venue: string
  location: string | null
  facilities: string | null
  minCapacity: number | null
  maxCapacity: number | null
  bookingEnabled: boolean
}
export interface SpaceBlock extends TimeRange {
  id: number
  spaceId: number
  createdBy: number
  reason: string | null
  createdAt: string
  cancelledAt: string | null
}
export type Period = 'SEMESTER' | 'VACATION' | 'EXAM'
export const weekdays = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const
export type Weekday = (typeof weekdays)[number]
export interface OperatingHours {
  period: Period
  dayOfWeek: Weekday
  closed: boolean
  openTime: string | null
  closeTime: string | null
}
export interface PolicyInput {
  enabled: boolean
  academicPeriod: 'SEMESTER' | 'VACATION'
  examStartDate: string | null
  examEndDate: string | null
  hours: OperatingHours[]
}
export interface OperatingPolicy extends Omit<PolicyInput, 'academicPeriod'> {
  spaceId: number
  configured: boolean
  timeZone: string
  academicPeriod: 'SEMESTER' | 'VACATION' | null
  updatedBy: number | null
  updatedAt: string | null
}
