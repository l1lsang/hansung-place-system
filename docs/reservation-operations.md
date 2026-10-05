# 참고 프로젝트의 예약·운영 기능 연동

[참고 소스](https://github.com/l1lsang/hsp)의 `server-runtime/booking-service.js`,
`ReadingRoomPage.tsx`, `ReservationsPage.tsx`, `AdminPage.tsx`를 기준으로 비교했습니다.
기존 Spring 세션·학번 로그인과 부서별 담당 공간 권한을 유지하며 Firebase는 도입하지 않았습니다.

## 추가 API와 화면

| API | 기능 | 연결 화면 |
| --- | --- | --- |
| GET `/api/spaces/{spaceId}/seats/availability?startTime=...&endTime=...&page=0&size=100` | 요청 시간 전체의 좌석별 예약 가능 여부 | 이미지 좌석 선택 |
| GET `/api/spaces/{spaceId}/seat-status?page=0&size=100` | 서버 현재 시간 기준 즉시 이용 현황 | 집중열람실 → 지금 바로 이용 |
| POST `/api/spaces/{spaceId}/seats/{seatId}/use` | 즉시 이용 시작, 201 + Location | 좌석 클릭 → 이용 시작 확인 |
| POST `/api/reservations/{reservationId}/return` | 본인 좌석 반납, 200 | 내 예약 상세 → 좌석 반납 |
| POST `/api/admin/reservations/{reservationId}/cancel` | 담당 공간 예약 강제 취소·좌석 종료, 200 | 관리자 예약 상세 → 사유 입력·확정 |
| GET `/api/admin/summary?date=YYYY-MM-DD` | KST 날짜별 담당 공간 통계 | 관리자 대시보드 |
| GET `/api/spaces/{spaceId}/booking-rules` | 예약 제한 및 즉시 이용시간 | 예약 안내·달력·시간 선택 |
| PUT `/api/admin/spaces/{spaceId}/booking-rules` | 담당 공간 규칙 전체 저장 | 공간 관리 → 예약 제한·즉시 이용 |

GET 공간 API는 공개입니다. 예약 생성·반납은 로그인, 관리자 API는 ADMIN 역할과 담당 공간 권한이 필요합니다.
모든 POST/PUT은 기존 CSRF 검증을 적용합니다. 좌석 현황은 `Cache-Control: no-store`이며
다른 사용자의 이름·학번·이메일·예약 ID를 공개하지 않습니다.

## 좌석 일괄 현황

응답은 `spaceId`, `startTime`, `endTime`, `bookingEnabled`, `policyConfigured`, `instant`,
`instantUseMinutes`, `userHasSeatUse`, `seats`를 포함합니다.
`seats`는 기존 페이지 형식(`content`, `page`, `size`, `totalPages`, `totalElements`)입니다.

각 좌석은 `id`, `spaceId`, `seatNumber`, `status`, `available`, `availableUntil`,
`occupiedUntil`, `mine`, `reservationId`를 반환합니다. 본인 좌석에만 `mine=true`와 예약 ID가 표시됩니다.
번호를 DB ID로 가정하지 않습니다. 점유·시간 차단·운영시간·시험기간·예약 중지를 함께 반영합니다.
시간 예약에서 `available`은 요청 구간 전체가 가능한지, 즉시 이용에서 `available`은 지금 시작할 수 있는지를 뜻합니다.

프론트는 페이지당 100개를 조회합니다. 현재 162석은 전체 상태 조회에 2개 요청을 사용합니다.
즉시 이용 화면은 30초마다 갱신하고, 날짜·시간 변경이나 로그아웃 시 이전 사용자/구간의 상태를 재사용하지 않습니다.

## 즉시 이용·반납·강제 종료

- 시작 요청은 본문 없이 공간 ID와 좌석 ID만 전달합니다. 시작·종료 시간은 서버가 결정합니다.
- 기본 이용시간은 180분이며 관리자가 변경할 수 있습니다. 운영 종료, 관리 차단, 다음 예약이 먼저 오면 그 직전까지 단축합니다.
- 시험기간 시간표가 자정 전후 연속으로 열려 있으면 다음 날까지 이어서 이용할 수 있습니다.
- 동일 사용자의 겹치는 좌석 이용·좌석 예약은 공간이 달라도 하나만 허용합니다.
- 본인 반납은 현재 진행 중인 SEAT_USE에만 허용하며 재요청은 같은 결과를 반환합니다.
- 관리자 강제 취소 본문은 `{ "reason": "시설 점검" }`입니다. 공백만 있는 사유는 허용하지 않으며 최대 200자입니다.
- 진행 중인 좌석을 반납/강제 종료하면 `COMPLETED`, 미래 예약을 취소하면 `CANCELLED`입니다.
- 원래 `startTime`/`endTime`은 보존하고 `endedAt`에 실제 종료를 기록합니다. 남은 시간은 다시 이용할 수 있습니다.

예약 상세·목록 DTO에 `owner: {name, studentId, email}`, `endedAt`, `actions`를 추가했습니다.
이 응답은 본인 또는 담당 관리자만 읽을 수 있습니다. `actions`에는 처리자 ID, 사유, 시각과
`CANCEL`, `RETURN_SEAT`, `ADMIN_CANCEL`, `ADMIN_END` 동작이 기록됩니다.
예약·참여자 행은 삭제하지 않습니다.

## 예약 규칙

```json
{
  "enabled": true,
  "slotMinutes": 30,
  "minDurationMinutes": 30,
  "maxDurationMinutes": 180,
  "advanceDays": 7,
  "dailyMaxMinutes": 180,
  "usageScope": "VENUE",
  "preventAdjacent": true,
  "purposeRequired": true,
  "instantUseMinutes": 180
}
```

위 값은 참고 프로젝트 그룹스터디실의 예시이며 실제 학교 운영 규칙으로 자동 적용하지 않습니다.
관리 화면의 **기준 불러오기 → 값 확인 → 저장**으로 적용합니다.

- 미설정 시 `configured=false`, `enabled=false`: 기존 시간 예약 제한은 유지됩니다.
- 예약 단위는 15/30/60분, 최소·최대 시간은 해당 단위의 배수여야 합니다.
- `advanceDays=7`은 KST 오늘부터 7일 뒤 날짜까지 포함합니다. 시간 예약은 같은 날짜 안에서만 가능하며 종료 24:00은 허용합니다.
- `dailyMaxMinutes=null`은 일일 합계 제한 없음입니다.
- `SPACE`는 해당 공간, `VENUE`는 동일 시설 코드를 가진 모든 공간의 신청자 이용시간을 합산합니다.
- 취소한 예약은 합산에서 제외하고 좌석을 반납하면 실제 이용한 시간만 합산합니다.
- 연속 예약 제한은 같은 범위에서 기존 예약과 겹치거나 경계가 맞닿은 예약을 막습니다.
- 즉시 이용은 별도 흐름으로 `instantUseMinutes`, 운영시간, 점유, 1인 1좌석을 검사합니다. 시간 예약용 단위·기간·일일 한도는 적용하지 않습니다.
- 기간·일일 한도를 변경해도 이미 저장된 예약은 변경하거나 삭제하지 않습니다.

운영시간 편집기에는 열람실 06:30~23:00/시험기간 24시간 등 참고 시간표를 채우는 버튼도 추가했습니다.
시간표를 불러오는 것만으로는 서버에 저장되거나 활성화되지 않습니다. 시험기간 날짜와 학교 운영 방침을 확인해야 합니다.

동시 요청은 사용자 행 → 공간 행 순으로 잠급니다. 사용자 잠금은 PostgreSQL `FOR NO KEY UPDATE`를 사용해
서로 다른 공간에 동시에 신청해도 일일 한도와 1인 1좌석을 넘을 수 없게 하고, 이력 저장의 외래 키 잠금과 충돌하지 않게 합니다.
좌석/일반 예약·차단·정책 변경은 기존 공간 잠금을 공유합니다.

## 통계

담당 공간에 한해 조회 날짜와 겹치는 취소되지 않은 예약을 집계합니다. 자정 경계와 `endedAt`을 적용해
실제 예약 구간의 분 합계를 계산합니다. 참고 MVP의 `예약 수 × 1.5시간` 추정치는 사용하지 않습니다.
`reservedMinutes`는 예약 시간 합계이며, 반납하지 않은 예약은 예정 종료까지 포함합니다. 출입 센서 기반 실사용 통계가 아닙니다.

## 마이그레이션·검증

`V4__booking_rules_and_reservation_actions.sql`은 `space_booking_rules`, `reservation_actions`와
`reservations.ended_at`만 추가합니다. V1~V3, 기존 테이블·데이터·PostgreSQL 볼륨은 보존합니다.

```sh
cd backend
./gradlew test
# 저장소 루트에서
docker compose up -d --build backend
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
npm run test:api
```

백엔드 테스트 52개와 프론트 테스트 32개, lint·타입 검사·프로덕션 빌드가 통과했습니다.
별도 PostgreSQL Testcontainers에서 권한, CSRF, 동시 신청, 운영시간·시험기간, 반납,
취소 이력, 일일 한도, 실제 통계를 검증했습니다. 실제 Compose에는 V4가 적용되었고
공개 API에서 기존 공간 1개/좌석 162개와 새 API의 응답을 확인했습니다.
계정이 준비되지 않아 운영 DB에 테스트 예약·사용자·정책을 생성하지 않았습니다.
프론트의 로그인 후 이용 시작·반납·관리자 취소는 API 계약에 맞춘 UI 테스트로 검증했습니다.
실제 API에 연결한 데스크톱·모바일 브라우저에서는 이미지 좌석 선택, 즉시 이용 확인창,
로그인 이동과 시간 예약 전환을 확인했습니다.

학번 로그인은 사용자 요청에 따라 유지합니다. 학교 SSO, 가입·비밀번호 재설정, 관리자 좌석 배치 편집,
공휴일 자동 연동, 예약 승인 워크플로는 이번 참고 소스의 예약·운영 API 보완 범위에 포함하지 않았습니다.
Vercel 공개 배포는 별도이며 백엔드 HTTPS 주소 설정은 [배포 안내](vercel-deployment.md)를 따릅니다.
