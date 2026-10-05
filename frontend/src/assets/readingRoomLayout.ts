// Image geometry only: seat IDs, status and availability always come from the API.
// Coordinates refer to the supplied 2262 × 998 reading-room-map.png.
const sixRows = [70, 151, 232, 313, 394, 474]
const fourRows = [203, 283, 364, 444]
const lowerFourRows = [692, 773, 895, 976]
const lowerThreeRows = [692, 774, 857]
function column(x: number, numbers: number[], rows: number[]) {
  return numbers.map((number, index) => ({
    number,
    left: (x / 2262) * 100,
    // The printed number is near the bottom of each desk; target its whole surface.
    top: ((rows[index] - 22) / 998) * 100,
  }))
}
function sequence(first: number, count: number) {
  return Array.from({ length: count }, (_, i) => first + i)
}
const positions = [
  ...column(47, sequence(1, 4), fourRows),
  ...column(158, sequence(5, 4), fourRows),
  ...column(277, sequence(9, 4), fourRows),
  ...column(386, sequence(13, 4), fourRows),
  ...column(494, sequence(17, 4), fourRows),
  ...column(606, sequence(21, 4), fourRows),
  ...column(725, sequence(25, 6), sixRows),
  ...column(832, sequence(31, 6), sixRows),
  ...column(949, sequence(37, 6), sixRows),
  ...column(1059, sequence(43, 6), sixRows),
  ...column(1159, sequence(49, 6), sixRows),
  ...column(1266, sequence(55, 6), sixRows),
  ...column(1396, sequence(61, 4), fourRows),
  ...column(1494, sequence(65, 4), fourRows),
  ...column(1607, sequence(69, 6), sixRows),
  ...column(1717, sequence(75, 6), sixRows),
  ...column(1853, sequence(81, 6), sixRows),
  ...column(1962, sequence(87, 6), sixRows),
  ...column(2128, [93, 95], [443, 567]),
  ...column(2206, [94, 96], [443, 567]),
  ...column(35, [97, 98, 101, 102], lowerFourRows),
  ...column(169, [99, 100, 103, 104], lowerFourRows),
  ...column(271, [105, 106, 109, 110], lowerFourRows),
  ...column(393, [107, 108, 111, 112], lowerFourRows),
  ...column(490, [113, 114, 115], lowerThreeRows),
  ...column(600, [116, 117, 118], lowerThreeRows),
  ...column(727, [119, 120, 123, 124], lowerFourRows),
  ...column(846, [121, 122, 125, 126], lowerFourRows),
  ...column(948, [127, 128, 131, 132], lowerFourRows),
  ...column(1066, [129, 130, 133, 134], lowerFourRows),
  ...column(1151, [135, 136, 139, 140], lowerFourRows),
  ...column(1290, [137, 138, 141, 142], lowerFourRows),
  ...column(1383, [143, 144, 145], lowerThreeRows),
  ...column(1493, [146, 147, 148], lowerThreeRows),
  ...column(1603, [149, 150, 153, 154], lowerFourRows),
  ...column(1733, [151, 152, 155, 156], lowerFourRows),
  ...column(1837, [157, 158, 159], [800, 881, 963]),
  ...column(1975, [160, 161, 162], [800, 881, 963]),
]
export const readingRoomPositions = new Map(
  positions.map((position) => [position.number, position]),
)
export function readingRoomPosition(seatNumber: string) {
  return /^\d+$/.test(seatNumber) ? readingRoomPositions.get(Number(seatNumber)) : undefined
}
