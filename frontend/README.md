# HSP 프론트엔드

React 19 + TypeScript + Vite + Tailwind CSS 4. 데이터와 인증은 Spring Boot API만 사용합니다.
실제 `.env`는 Git에서 제외되며, DB 비밀번호를 프론트 환경변수에 넣지 않습니다.

## 실행

Node.js 22.12 이상과 실행 중인 backend가 필요합니다. 기존 `.env`는 덮어쓰지 마세요.

```sh
cd frontend
# .env가 없는 새 환경에서만 .env.example을 .env로 복사
npm ci
npm run dev
```

http://localhost:5173 에 접속합니다. 백엔드 CORS에 맞춰 개발 포트를 5173으로 고정했습니다.
`127.0.0.1`로 접속하려면 별도로 CORS origin을 허용해야 합니다.

```sh
npm run lint
npm run typecheck
npm test
npm run build
npm run test:api
```

`test:api`는 현재 `.env`의 실제 공개 API를 읽기만 합니다. 계정·예약·정책을 만들지 않습니다.
`npm run preview`는 `dist`를 localhost:5173에서 확인합니다. dev server와 동시에 실행하지 않습니다.
Vercel 설정은 [배포 안내](../docs/vercel-deployment.md)를 참고하세요.

## 1. 기존 MVP에서 참고한 UX

[참고 저장소](https://github.com/l1lsang/hsp), 분석 시점 commit `ebd1de5cb9eac02372509a6ce77db38b51977004`.
[참고 사이트](https://hsp-ashy.vercel.app/)의 로그인 화면도 직접 확인했습니다.

- VenueSelectPage의 이미지 중심 시설 선택 → 공간 → 달력 → 시간 → 참여자 → 예약 확인 흐름
- ReadingRoomPage의 좌석 번호와 공간 배치도, ReservationsPage의 상태별 안내
- Sidebar의 학생/관리자 메뉴 분리, AdminPage의 공간 운영과 차단 일정
- 학기·방학·시험기간의 다른 시간표 개념을 새 운영 정책 API에 반영

참고 사이트는 비로그인 상태에서 로그인 화면만 열립니다. 이후 화면은 저장소의 실제 소스를 함께 분석했습니다.

## 2. 가져오지 않은 구조

Firebase Auth/Firestore/Vercel Functions, 거대한 App.tsx의 통합 상태, 하드코딩한 공간·점유·예약 정책은 사용하지 않습니다.
학교 Google SSO를 구현한 것처럼 표시하지 않고 현재 백엔드의 학번/비밀번호 로그인을 제공합니다.
API에 없는 승인·거절·타인 예약 강제 취소 버튼도 만들지 않았습니다.

## 3. 디렉터리

```text
frontend/
  src/
    api/          client, auth, spaces, reservations, admin
    assets/       제공된 이미지와 공간을 연결하는 spaceMedia.ts
    components/   인증, 달력, 예약 폼, 좌석, 배치도, 정책·차단 편집, 공통 UI
    hooks/        인증 context, 중복 제출 방지, 현재 시각
    layouts/      AppLayout
    pages/        라우트별 화면
    styles/       디자인 토큰, 레이아웃, 공통 컴포넌트, 한성 캠퍼스 테마
    test/         API 계약을 따르는 테스트 전용 fixture와 UI 흐름 테스트
    types/        백엔드 DTO 타입
    utils/        KST 시각, 예약 가능 구간, 화면용 라벨
    App.tsx       라우트와 lazy loading
    main.tsx      앱 초기화
  scripts/smoke-public.mjs
  vercel.json
  .env.example
```

## 4. 생성·수정한 파일과 이미지

빈 frontend에 위 소스, package.json/package-lock.json, TypeScript/Vite/ESLint/Prettier 설정,
index.html, vercel.json, 테스트와 이 문서를 추가했습니다. 기존 frontend/.env는 유지했습니다.
이미지는 사용자가 넣은 `src/components/`의 원본을 그대로 사용합니다.
집중열람실은 요청에 따라 번호가 있는 `reading-room-map.png`로 통일했습니다.

| 파일                  | 사용 위치                                     |
| --------------------- | --------------------------------------------- |
| library-hero.jpg      | 캠퍼스 배너, 로그인, 학술정보관 카드          |
| readingroom_2.png     | 이전 배치도 원본 보관, 현재 화면에서는 미사용 |
| reading-room-map.png  | 집중열람실 카드·이미지 위 좌석 선택·확대 보기 |
| sangsang-base-map.png | 상상베이스 안내 카드·관련 공간 상세           |
| sansang-base-plus.png | 상상파크 플러스 안내 카드·관련 공간 상세      |

시설 안내 카드는 정적 안내 자료입니다. 예약 운영 공간 수와 이동 링크는 실제 API 데이터로 결정됩니다.
DB에 없는 공간이나 좌석을 예약 가능한 것처럼 만들지 않습니다. 이미지 자체에는 점유 정보가 없습니다.
집중열람실은 이미지 위에 API의 실제 좌석과 시간별 예약 가능 여부를 겹쳐 표시합니다.
시설 코드가 새로 생기면 `assets/spaceMedia.ts`의 매핑을 추가하면 됩니다.

### 집중열람실 이미지 예약

1. 날짜와 시작·종료 시간을 선택합니다.
2. 이미지 위의 예약 가능한 좌석 번호를 클릭합니다. 선택한 좌석은 한성 블루와 체크로 표시됩니다.
3. `예약 내용 확인` 링크로 요약을 확인하고 로그인 후 예약을 확정합니다.

`ReadingRoomSeatMap.tsx`가 이미지와 버튼을 표시하고, `assets/readingRoomLayout.ts`가
원본 이미지(2262×998)의 좌석 위치를 관리합니다. 위치는 API의 `seatNumber`와 연결하며
예약 요청에는 실제 `seat.id`를 사용합니다. ID가 좌석 번호와 같다고 가정하지 않습니다.
001~162번 중 API에 존재하는 좌석만 버튼으로 표시하고, 배치도에 없는 새 번호는 별도 목록으로 제공합니다.
`전체 보기`, `확대 보기`, 좌우 이동, 큰 창 선택을 지원합니다. 확대 시 좌석 버튼은 약 45×45px입니다.
예약 불가·운영 중지·조회 실패·조회 중인 좌석은 선택할 수 없습니다.
시간 변경 시 상태를 다시 조회하고, 최종 예약 시 서버가 가용성을 다시 검사합니다.

한성대학교 [공식 한성UI](https://www.hansung.ac.kr/hansung/6188/subview.do)의
[웹 색상표 CSS](https://www.hansung.ac.kr/sites/hansung/style/css/w_sub.css)를 직접 확인했습니다.
`styles/index.css`에 Sky Blue `#048DDB`, Blue `#0A4DA1`, Dark Blue `#032E6E`, Grey `#626466`을 토큰으로 정의했습니다.
주 버튼은 Blue, 사이드바는 Dark Blue, 강조는 Sky Blue를 사용합니다. 성공/오류 색은 별도 의미 색입니다.

## 5. 구현 페이지

| 경로                               | 기능                                                                 |
| ---------------------------------- | -------------------------------------------------------------------- |
| /login                             | 로그인, 실패 안내, 원래 예약 화면 복귀                               |
| /spaces                            | 시설 안내, 공간 검색, 시설·종류·인원·운영 필터, 페이지 이동          |
| /spaces/:spaceId                   | 공간 상세, 달력·시간표, 정책 안내, 배치도 확대, 좌석 선택, 예약 확인 |
| /reservations                      | 본인 예약 목록, 예정·종료·취소 표시                                  |
| /reservations/:reservationId       | 예약 상세·참여자·취소 확인                                           |
| /admin                             | 담당 공간 목록                                                       |
| /admin/spaces/new                  | 공간 등록                                                            |
| /admin/spaces/:spaceId             | 정보·활성화 수정, 운영 정책, 차단 등록·해제                          |
| /admin/reservations                | 담당 공간 예약 목록 및 공간별 조회                                   |
| /admin/reservations/:reservationId | 담당 공간 예약 상세                                                  |

목록의 상태는 실제 status와 종료 시간을 함께 사용합니다. 과거 UPCOMING을 서버의 COMPLETED로 변경하지 않습니다.

## 6. 실제 연결 API

| 모듈         | 연결한 API                                                                                                        |
| ------------ | ----------------------------------------------------------------------------------------------------------------- |
| auth         | GET /api/auth/csrf, POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout                                 |
| spaces       | GET /api/spaces, /api/spaces/{id}, /api/spaces/{id}/seats, /api/spaces/{id}/availability, /api/spaces/{id}/policy |
| reservations | POST·GET /api/reservations, GET·DELETE /api/reservations/{id}                                                     |
| admin        | GET·POST /api/admin/spaces, PATCH /api/admin/spaces/{id}                                                          |
| admin 예약   | GET /api/admin/reservations, /api/admin/reservations/{id}                                                         |
| admin 차단   | GET·POST /api/admin/spaces/{id}/blocks, DELETE /api/admin/space-blocks/{id}                                       |
| admin 정책   | PUT /api/admin/spaces/{id}/policy                                                                                 |

페이지에 fetch를 흩어놓지 않고 api 모듈에서 DTO를 처리합니다.
TanStack Query가 조회 캐시·중복 제거·오류·재조회를 관리하고, 페이지별 lazy loading을 적용했습니다.
집중열람실은 좌석 목록의 모든 페이지를 100개씩 읽어 전체 좌석표에 표시합니다.
좌석 일괄 availability API가 없어 최대 6개 요청씩 병렬 조회합니다. 다른 공간의 일반 좌석 목록은 36개씩 표시합니다.
날짜·시간 변경 시 이전 요청은 취소합니다. AVAILABLE만 보고 빈 좌석으로 판단하지 않습니다.
캠퍼스 시간대는 항상 Asia/Seoul입니다. 예약 확인 후 제출 직전 availability와 서버 예약 트랜잭션에서 다시 검사합니다.

## 7. 인증

- 모든 fetch에 `credentials: include`: HttpOnly JSESSIONID 세션 쿠키를 브라우저가 관리
- 새로고침 시 `/auth/me`로 복원; 로컬스토리지에 계정·토큰·비밀번호를 저장하지 않음
- 변경 요청 전 `/auth/csrf`의 headerName/token을 사용; 로그인/로그아웃 후 메모리 토큰 폐기
- CSRF_INVALID 응답에만 토큰을 갱신해 한 번 재시도
- 로그인 여부와 ADMIN 역할의 route guard; 관리자 편집 화면에서 담당 공간 API 권한도 확인
- 실제 권한과 충돌 검사는 최종적으로 Spring Security 및 서비스 계층에서 수행

## 8. 상태와 오류

백엔드 `{code,message}`를 공통 ApiError로 처리합니다. 400/401/403/404/409/500, 네트워크 오류,
잘못된 JSON 응답에 안내를 제공합니다. 조회 화면은 loading/empty/error/retry, 변경 화면은 pending/error/success를 표시합니다.
useAction의 ref 잠금으로 같은 프레임의 연속 클릭도 막습니다. 응답이 유실된 변경 요청은 자동 재전송하지 않습니다.
네트워크 오류 시 처리 여부가 불명확하므로 목록 확인을 안내합니다.

## 9. 반응형·접근성

데스크톱 사이드바와 예약 요약 패널, 태블릿의 단일 예약 흐름, 모바일 메뉴·한 열 카드·이미지 좌석표를 제공합니다.
label, 실제 button, aria-pressed, focus-visible, skip link, modal dialog의 포커스 관리와 Escape 닫기를 적용했습니다.
모바일 메뉴는 배경을 inert 처리하고 포커스 이동을 제한합니다. 좌석 상태는 색·기호·접근성 이름과 선택 안내로 표시합니다.
큰 배치도는 확대 창에서 스크롤할 수 있고 원본 이미지는 변경하지 않습니다.

## 10. 검증과 한계

프론트 테스트는 API 클라이언트, KST/가용시간 계산, 권한 가드, 로그인 실패, 예약자 제외 참여자,
중복 제출, 좌석 예약 409, 예약 취소, 운영 정책 저장을 확인합니다.
이미지 좌석 선택은 목록 전체 페이지 조회, 번호와 ID가 다른 좌석, 예약 불가/오류 차단,
시간 변경 중 클릭 차단, 확대 창 선택, 이미지 클릭→예약 확인→실제 DTO 제출까지 테스트합니다.
프론트 24개 테스트와 lint/typecheck/build가 통과했습니다. mock은 테스트 파일에서만 사용합니다.
백엔드 테스트는 별도 PostgreSQL Testcontainers에서 실행합니다.

실제 Docker API에서는 공간 1개/좌석 162개, 상세·availability·미설정 정책·비로그인 관리자 차단을 확인했습니다.
브라우저에서 공간 목록→상세→이미지 좌석/시간 선택→로그인 복귀 경로와 모바일/태블릿 화면을 확인했습니다.
실제 162개 좌석 버튼, 161·162번 선택 시 예약 요약 반영, 모바일 확대·가로 스크롤을 확인했습니다.
테스트 계정이 없어 실제 운영 DB에 로그인·예약 생성/취소·관리자 저장은 실행하지 않았습니다.
이 범위는 프론트 UI 테스트와 별도 DB 백엔드 통합 테스트로 검증합니다.

## 11. 아직 제공하지 않는 기능

학교 SSO/회원가입/비밀번호 재설정, 예약 승인·거절, 타인 예약 강제 취소, 관리자 좌석 배치 편집,
좌석 일괄 점유 조회, 공휴일 자동 연동, 7일 예약 제한·사용자별 하루 누적 제한은 API가 없습니다.
기존 MVP의 하드코딩 정책을 실제 학교 정책으로 가정하지 않았습니다.
운영시간·학기/방학·시험기간 예외는 이번에 실제 API와 관리 화면으로 추가했습니다.

## 12. 운영 정책·Vercel 실행 안내

[운영 정책 API](../docs/operating-policy.md), [Vercel 배포](../docs/vercel-deployment.md)를 참고하세요.
백엔드 공개 주소가 미정이라 실제 Vercel 배포는 하지 않았습니다.

## 13. 공부할 때 읽을 순서

1. `types/api.ts`: 백엔드가 주고받는 데이터
2. `api/client.ts` → `api/auth.ts`: 쿠키, CSRF, 오류 처리
3. `AuthProvider.tsx` → `RouteGuard.tsx`: 인증 상태와 접근 제어
4. `main.tsx` → `App.tsx` → `AppLayout.tsx`: 앱과 라우팅 구조
5. `SpacesPage.tsx` → `SpaceDetailPage.tsx`: 조회와 검색, 상태 분리
6. `MonthCalendar.tsx` → `SeatPicker.tsx` → `ReadingRoomSeatMap.tsx` → `BookingForm.tsx`: 예약 흐름
7. 예약 페이지 → 관리자 페이지 → `PolicyEditor.tsx`
8. `styles/index.css`와 `campus.css`: 공식 색상 토큰과 이미지 레이아웃
9. `test/flows.test.tsx`: API 계약을 UI에서 검증하는 방법
