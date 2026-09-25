# 밸런스 대시보드 운영 배포

배포일: 2026-09-23 KST

사용자가 게임·웹 배포를 승인하고 운영자 계정과 OAuth 앱 정보를 제공했다. GitHub 공개 사용자 API에서 `biryeongtrain`의 숫자 ID `83863154`를 확인해 허용 목록에 등록했다. 비밀키는 이 문서와 저장소에 기록하지 않는다.

## 반영 내용

- 게임: `/Users/qf/Desktop/SemionTd/mods/semion-td-1.0-SNAPSHOT+1.21.8.jar` 교체. Java 21과 기존 `start.sh`로 기동했다. 배포 직전 게임은 종료 상태였다.
- 웹: `/Users/qf/docker/postgres/compose.yml`의 `web`, `sync`를 빌드하고 `up -d --no-deps web sync`로 교체했다.
- DB: 전체 백업을 격리 DB에 복원한 뒤 `db/schema.sql`, `db/balance-admin.sql`의 추가 마이그레이션을 검증하고 운영에 적용했다. 기존 경기 수는 전후 228건이다. PostgreSQL 컨테이너 ID `4ae8c0312087`은 유지했다.
- Next.js와 `eslint-config-next`는 16.3.6으로 고정했다. [Next.js 보안 공지](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4)를 확인하고 취약한 전이 의존성도 업데이트했다.
- Docker 빌드에서 `outputs`, `tsconfig.tsbuildinfo`를 제외했다. QA 인증 상태와 생성물이 이미지에 들어가지 않게 했다.

기존 관리 대상 JSON의 값은 덮어쓰지 않았다. 게임 로더가 `tower_balance.json`에 누락 항목 11개, `augment_balance.json`에 125개를 추가했다. 기존 키의 변경·삭제는 없었고 다른 관리 JSON 5종은 바이트 단위로 같았다. 운영 밸런스 적용 요청은 0건이다.

## 설정과 접속

- 관리 화면: <https://semiontd.biryeong.kim/admin/balance>
- OAuth 콜백: `https://semiontd.biryeong.kim/api/auth/callback/github`
- 웹 비밀 설정: `/Users/qf/docker/postgres/balance.env`
- 게임 비밀 설정: `/Users/qf/Desktop/SemionTd/.balance.env`
- 두 파일의 권한은 600이다. 게임 시작 스크립트는 `.balance.env`를 읽고, 웹 Compose는 `balance.env`를 읽는다.
- 게임의 관리 서버 ID는 `production`이다. 게임 API는 `127.0.0.1:8091`에서만 수신하며 웹 컨테이너는 `host.docker.internal`로 연결한다. 관리 포트를 인터넷에 추가 공개하지 않았다.

채팅으로 전달한 OAuth Client Secret은 로그인 확인 후 GitHub에서 재발급하고 웹 설정 파일을 갱신해야 한다. 세션키·게임 서명키는 별도로 생성했으며 출력하지 않았다.

## 검증 결과

| 확인 | 결과 |
|---|---|
| Java 21 전체 게이트 | BUILD SUCCESSFUL, GameTest 675개 통과 |
| 웹 테스트 | 격리 PostgreSQL 포함 43개 통과 |
| 웹 타입 검사·빌드·Docker 빌드 | 통과 |
| ESLint | 오류 0, 기존 API 로그아웃 페이지 전체 이동에 대한 새 규칙 경고 1 |
| `npm audit`, `npm audit --omit=dev` | 취약점 보고 0건 |
| 운영 `/api/health` | HTTP 200, DB 정상, 새 동기화 시각 확인 |
| 운영 `/admin/balance` | HTTP 200, GitHub 로그인 버튼 표시 |
| 운영 `/api/auth/providers` | 설정한 HTTPS 로그인·콜백 주소 응답 |
| 비로그인 관리 API | `/session`, `/servers` 모두 HTTP 401 |
| 브라우저 로그인 흐름 | 관리 화면 → NextAuth GitHub 버튼 → `github.com/login` 도착 |
| 컨테이너 → 게임 API | 서명된 읽기 요청 HTTP 200, 대기 요청·쓰기 차단 없음 |

실제 GitHub 계정 로그인, 앱 동의와 콜백 후 관리자 화면은 사용자가 확인한다. 운영 수치를 바꾸는 쓰기 시험은 하지 않았다. 웹 교체 중 일시적인 502 이후 정상 응답을 확인했다.

최초 배포 JAR SHA-256:
`5924bfba06006aeb156072e365c9309d8a966c667fbb2201e1c2bdaa8c176369`

최초 운영 밸런스 버전:
`20018eecc3012554a9271d91aa56aa252759a0ed0cde300718666788a90bbc9e`

공개 도감 동기화 버전:
`3e325ab3f88efd7bddafa2f17b7e8236f542f8cab9fc7fa0fcd265f68f125af1`

## 백업과 복구 경계

- 게임 백업: `/Users/qf/Desktop/SemionTd/backups/balance-20260923/`. 이전 JAR, 시작 스크립트, 설정 디렉터리 압축본을 보관한다.
- 웹·DB 백업: `/Users/qf/docker/postgres/backups/balance-20260923/`. 이전 Compose와 PostgreSQL 전체 덤프를 보관한다. 이 덤프의 격리 복원을 확인했다.
- 이전 이미지 태그: `postgres-web:before-balance-20260923`, `postgres-sync:before-balance-20260923`.

웹만 복구할 때는 이전 이미지를 사용하고 추가 DB 컬럼·테이블은 유지할 수 있다. 게임 관리 저장소가 생긴 뒤에는 JAR만 임의로 교체하거나 저장소를 일부 삭제하지 않는다. 복구가 필요하면 서버를 멈추고 배포 후 경기·패치 발생 여부를 확인한 뒤 이전 설정과 새 관리 저장소를 함께 보존해 복구한다. 운영 DB 전체 덤프 복원은 배포 이후 데이터를 잃을 수 있으므로 별도 판단 없이 실행하지 않는다.

작업 브랜치의 변경은 커밋·푸시하지 않았다. 임시 검증 DB와 브라우저만 종료하며 운영 서비스는 유지한다.

## 10:34 KST 증강·특성 편집 확대 배포

사용자가 게임 JAR 교체와 재시작을 승인했다. 종료 직전 서명된 읽기 요청으로 `game: null`, `pending: null`, `writeBlocked: null`을 확인했다. 부팅 이후 플레이어 접속 기록은 0건이었다. 별도의 Minecraft 상태 핑은 시간 초과가 발생해 접속 인원 확인 근거로 사용하지 않았다.

- 10:33:44 KST 정상 종료와 플레이어·월드 저장 완료를 확인했다. 기존 시작 루프를 일시 정지해 교체 전에 다시 켜지지 않도록 했다.
- 이전 JAR·시작 스크립트·로그와 종료 후 설정 압축본을 `/Users/qf/Desktop/SemionTd/backups/balance-parameters-20260923-eNMM94/`에 보관했다.
- 검증한 JAR을 교체하고 기존 시작 루프를 재개했다. 10:34:41 KST `Done (1.211s)!` 로그와 게임 포트 25578·관리 포트 `127.0.0.1:8091`을 확인했다.
- 새 JAR SHA-256: `d04bb52aef09f6616f0c4afbd139ba590ce955f9cf3a12e9204f1f29d07c1213`.
- 운영 관리 API의 증강 433개·특성 32개가 모두 `editable: true`, `modes: [NEXT_MATCH]`로 응답했다. 증강·특성의 ID별 값 해시는 교체 전후 같았다. 특성의 한글 이름도 확인했다.
- 관리 JSON 7종과 `balance-management/index.json`은 백업과 바이트 단위로 같았다. 활성 밸런스 버전과 공개 도감 버전도 유지했다. 수치를 바꾸는 API 요청은 보내지 않았다.
- 웹·동기화·PostgreSQL 컨테이너 ID는 각각 `104ca0a823bd`, `5fdfd64f744a`, `4ae8c0312087`로 유지했다. 운영 웹 상태와 관리 페이지는 HTTP 200, 비로그인 관리 세션은 HTTP 401이었다.

이번 검증은 운영 API 응답과 서버 기동 확인이다. 브라우저 로그인 후 관리자 화면과 게임 클라이언트 화면은 다시 확인하지 않았다.
