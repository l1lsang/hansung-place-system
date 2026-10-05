# Vercel 프론트엔드 배포

Vercel에는 React 정적 프론트엔드만 배포합니다. Spring Boot/PostgreSQL은 기존 Docker 환경을 유지합니다.
백엔드의 공개 HTTPS 주소가 정해지면 아래 값을 설정합니다. 현재 프로젝트에서 외부 배포는 실행하지 않았습니다.

## 프로젝트 설정

| 항목 | 값 |
| --- | --- |
| Root Directory | frontend |
| Framework Preset | Vite |
| Install Command | npm ci |
| Build Command | npm run build |
| Output Directory | dist |
| Node.js | 프로젝트 engines를 만족하는 22.12 이상 버전 |
| 환경변수 | VITE_API_BASE_URL = 실제 백엔드 HTTPS origin |

API 주소 예: `https://api.example.edu` (예시 도메인). `/api`나 마지막 경로를 붙이지 않습니다.
환경변수는 Production/Preview에 각각 설정합니다. VITE_ 값은 브라우저에 공개되므로 비밀키를 넣지 않습니다.
값을 변경하면 재배포해야 번들에 반영됩니다.

`frontend/vercel.json`은 SPA 경로를 index.html로 연결해 상세 페이지 직접 접속·새로고침을 지원합니다.
프로젝트 연결 정보 `.vercel/`, `.env*`, node_modules와 dist는 Git에 올리지 않습니다.
`package-lock.json`, `vercel.json`, `.env.example`은 Git에 포함합니다.
Vercel 환경에서 API 주소가 없거나 localhost/비 HTTPS/경로 포함 값이면 빌드를 중단해 잘못된 배포를 방지합니다.

## 백엔드 연결과 쿠키

실제 프론트 도메인을 backend의 `CORS_ALLOWED_ORIGINS`에 정확한 origin으로 설정합니다.
예: `https://hsp.example.edu`. 여러 개면 쉼표로 구분하며 인증 요청에 wildcard를 사용하지 않습니다.
백엔드는 GET/POST/PUT/PATCH/DELETE와 Content-Type, X-XSRF-TOKEN을 허용합니다.

권장 구성은 같은 등록 도메인의 HTTPS 서브도메인입니다.

```text
프론트: https://hsp.example.edu (Vercel custom domain)
API:    https://api.example.edu (Spring Boot HTTPS reverse proxy)
CORS_ALLOWED_ORIGINS=https://hsp.example.edu
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
```

`*.vercel.app`과 별도 도메인 API를 직접 연결하는 cross-site 구성이라면:

```text
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
CORS_ALLOWED_ORIGINS=https://실제프로젝트.vercel.app
```

SameSite/Secure는 JSESSIONID와 XSRF-TOKEN 모두에 적용됩니다. localhost 개발 기본값은 lax/false입니다.
브라우저의 서드파티 쿠키 차단은 SameSite=None만으로 해제되지 않습니다.
로그인 안정성을 위해 같은 사이트의 custom domain 구성을 권장하며, 필요하면 백엔드 도메인 확정 후
같은 origin의 reverse proxy를 설계할 수 있습니다. 현재 가짜 API 주소를 rewrite 대상으로 넣지 않았습니다.

Vercel은 로컬 PC의 localhost:8080에 접근할 수 없습니다. 실제 외부 HTTPS 백엔드가 필요합니다.
Preview URL도 인증 테스트하려면 해당 정확한 origin을 CORS에 추가해야 합니다.

## 배포 후 확인

1. `/spaces/실제ID` 직접 접속 및 새로고침, 이미지 로딩을 확인합니다.
2. 공개 공간/좌석/정책/availability 요청과 CORS 응답을 확인합니다.
3. 테스트 계정으로 로그인 → 새로고침 후 me → 예약 → 조회 → 취소 → 로그아웃을 확인합니다.
4. 관리자 담당 공간 밖 접근, 정책 저장, 차단 등록/해제를 확인합니다.
5. Chrome뿐 아니라 Safari 등 실제 이용 브라우저에서 세션 쿠키 유지도 확인합니다.

참고: [Vercel Vite SPA 설정](https://vercel.com/docs/frameworks/frontend/vite),
[쿠키 SameSite·Secure](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie).
