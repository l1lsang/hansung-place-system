# 공간 운영시간 및 시험기간 API

V3는 `space_operating_policies`, `space_operating_hours` 두 테이블만 추가합니다.
V1/V2와 기존 `system_settings`, users, spaces, seats, reservations 데이터는 수정하지 않습니다.
system_settings는 전역 학기/시험 여부만 표현하므로 공간별 시간표·기간을 저장하는 용도로 임의 전환하지 않았습니다.

| API | 권한 | 의미 |
| --- | --- | --- |
| GET /api/spaces/{spaceId}/policy | 공개 | 정책 조회. 없으면 configured=false, enabled=false, hours=[] |
| PUT /api/admin/spaces/{spaceId}/policy | ADMIN + 담당 공간 + CSRF | 정책 전체 저장. 최초 등록과 교체 모두 200 |

## 시간 규칙

- 모든 운영시간은 Asia/Seoul 기준이며 요일은 MONDAY~SUNDAY입니다.
- SEMESTER, VACATION, EXAM 각각 7일: 정확히 21개 규칙이 필요합니다.
- `academicPeriod`는 SEMESTER 또는 VACATION입니다.
- 시험기간 시작/종료 날짜를 모두 지정하면 양 끝 날짜를 포함하여 EXAM 시간표로 대체합니다.
- 두 날짜를 null로 저장하면 시험기간 지정이 해제됩니다. 일별 기본 시간표로 복귀합니다.
- 운영일은 `closed=false`, `openTime=HH:mm`, `closeTime=HH:mm` 또는 `24:00`입니다.
- 휴무일은 `closed=true`, 두 시간은 null입니다. `00:00~24:00`은 종일 운영입니다.
- 자정을 넘는 운영은 두 요일에 나눠 설정합니다. 예약이 날짜 경계를 넘으면 각 날짜의 정책을 모두 검사합니다.
- 정책이 없거나 enabled=false면 이전 예약 동작을 유지합니다. 기존 공간에 기본 시간표를 임의로 심지 않습니다.
- 공휴일 자동 판단과 기간별 여러 시험기간 목록은 아직 없으며 한 공간당 한 시험기간을 지정합니다.

## 요청 예시 구성

아래는 API 형식을 설명하는 예시이며 학교 확정 운영시간이 아닙니다.

```js
const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const body = {
  enabled: true,
  academicPeriod: 'SEMESTER',
  examStartDate: '2030-06-01',
  examEndDate: '2030-06-07',
  hours: ['SEMESTER', 'VACATION', 'EXAM'].flatMap(period =>
    days.map(dayOfWeek => ({
      period,
      dayOfWeek,
      closed: false,
      openTime: period === 'EXAM' ? '00:00' : '09:00',
      closeTime: period === 'EXAM' ? '24:00' : '18:00',
    })),
  ),
}
```

관리 화면 `/admin/spaces/{id}` → 운영시간·시험기간에서 같은 구조를 편집할 수 있습니다.
원래 PUT은 전체 교체이므로 다른 관리자와 동시에 편집하면 나중에 저장된 내용이 최종 정책이 됩니다.

## 예약과 availability에 미치는 영향

정책 저장과 예약 생성은 기존 공간 행 잠금을 공유합니다. 저장된 정책을 새 예약 트랜잭션에서 다시 읽습니다.
운영시간 밖의 예약은 409 `OUTSIDE_OPERATING_HOURS`로 거절합니다.
기존 예약은 취소하거나 시간을 변경하지 않으며 정상 조회/취소할 수 있습니다.

availability는 운영시간 구간과 요청 구간의 교집합에서 기존 예약·차단 시간을 뺍니다.
활성 정책이면 `policyScope=OPERATING_HOURS_AND_OCCUPANCY`, 그 외에는 `OCCUPANCY_ONLY`입니다.
자정 경계에서 연속된 available 구간이 여러 개일 수 있으므로 클라이언트는 연결된 구간을 합쳐 판정합니다.
조회 시점의 스냅샷이므로 이후 예약 성공을 보장하지 않습니다.

형식 오류·중복 요일·역전 시간·일부만 지정된 시험기간은 400입니다.
사용자 권한 검증은 기존 AdminAccessService, CSRF는 Spring Security가 수행합니다.

## 검증

`backend/gradlew.bat test`는 별도 PostgreSQL 17 Testcontainers를 사용합니다.
운영 DB를 테스트 데이터로 사용하지 않습니다. 기본/시험기간 경계, 24:00, 휴무일, 긴 구간,
잘못된 시간표, 권한·CSRF, availability 필터, 기존 예약 보존, 정책 비활성화를 검증했습니다.

`docker compose up -d --build backend`로 실제 환경에 V3를 적용했습니다.
로그에서 `Successfully applied 1 migration ... now at version v3`와 Tomcat/앱 시작을 확인했습니다.
실제 정책은 관리자 계정으로 정한 값을 저장할 때 적용됩니다. 기존 공간은 미설정 상태를 유지했습니다.
