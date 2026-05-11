# 서비스 준비 체크리스트

이 문서는 Semion TD를 실제 미니게임으로 열 때 장애나 플레이 불능으로 이어질 수 있는 항목만 추린다.
서비스에 직접 문제를 만들 가능성이 낮은 확장/고도화 작업은 제외한다.

## P0: 오픈 전 필수 확인

### 1. 실플레이어 수동 QA

GameTest와 콘솔 QA로는 실제 클라이언트의 teleport, HUD mount, DialogUtils 표시를 완전히 검증할 수 없다.

확인 항목:

- 실제 클라이언트 접속자가 게임 진행 중이 아니면 lobby로 이동한다.
- 플레이어가 `/semiontd ready`를 실행할 수 있다.
- 관리자가 `/semiontd start`로 경기를 시작할 수 있다.
- 진행 중 신규 접속자는 lobby 안내를 받고 `/semiontd spectate`로 관전할 수 있다.
- `/semiontd spectate red|blue|green|yellow`가 원하는 팀 world의 spectator spawn으로 보낸다.
- spectator HUD가 현재 world의 팀 보스 체력을 보여준다.
- 월드 이동 후 HUD가 사라지지 않고 다시 mount된다.
- `/semiontd end` 또는 `/semiontd reset` 후 모든 플레이어가 lobby로 돌아간다.
- `/semiontd ui`, `economy`, `profile`, `job list/current/select`, `summons` 출력이 실제 클라이언트에서 읽기 좋다.

완료 기준:

- 2명 이상 실제 클라이언트로 ready/start/spectate/end/reset 흐름을 통과한다.
- spectator HUD가 팀 선택 관전 이동 후에도 유지된다.
- 치명적인 클라이언트 표시 오류나 서버 예외가 없다.

현재 상태:

- 2026-05-11 콘솔 QA에서 서버 기동과 운영 명령 기본 흐름은 확인했다.
- 실제 클라이언트 접속자는 없어서 ready/start/HUD/mount/관전 시야 항목은 아직 미완료다.

### 2. 맵 템플릿 실사용 QA

맵 템플릿은 GameTest에서 구조 일부를 검증해도 실제 플레이 동선과 시야 문제가 남을 수 있다.

확인 항목:

- lobby spawn이 안전하고 명확하다.
- 각 팀 arena가 정상 로드된다.
- active player spawn이 각 lane 시작 위치에 맞다.
- spectator spawn이 팀 상황을 관전하기 좋은 위치에 있다.
- lane path가 끊기지 않고 몬스터가 진행 가능하다.
- final lane과 boss convergence 지점이 의도한 위치에 모인다.
- boss spawn이 팀별로 올바르게 생성된다.
- tower placement 가능 영역과 실제 길이 충돌하지 않는다.
- `/semiontd reset` 후 lobby/arena 상태가 다시 정상화된다.

완료 기준:

- RED/BLUE/GREEN/YELLOW arena 로드와 boss spawn이 모두 확인된다.
- 최소 한 라운드 진행 중 몬스터 경로, 보스 도달, 관전자 시야에 치명 문제가 없다.
- reset 후 재생성이 가능하다.

현재 상태:

- 2026-05-11 `./gradlew runServer --console=plain` 기동 성공.
- `semiontd create` 후 `arenaLoaded=4/4` 확인.
- `semiontd status teams`에서 RED/BLUE/GREEN/YELLOW 모두 `arenaLoaded=true`, `boss=1000/1000` 확인.
- `semiontd reset` 후 `activeGame=false`, `arenaLoaded=false` 복구 확인.
- 실제 클라이언트 시야, lane path 진행, final lane, boss convergence 체감 QA는 아직 미완료다.

## 2026-05-11 콘솔 QA 기록

실행 명령:

```text
./gradlew runServer --console=plain
semiontd status
semiontd create
semiontd status
semiontd status teams
semiontd status players
list
semiontd start
semiontd spectate red
semiontd reset
semiontd status
stop
```

확인 결과:

- 서버가 `*:25565`로 정상 기동했다.
- `Semion TD initialized.` 로그가 출력됐다.
- Polymer resource pack 생성이 성공했다.
- 초기 status는 `activeGame=false`, `lobbyLoaded=true`, `arenaLoaded=false`였다.
- create 후 status는 `activeGame=true`, `phase=WAITING`, `round=1`, `ready=0`, `activeParticipants=0`, `spectators=0`, `arenaLoaded=4/4`였다.
- team status는 네 팀 모두 arena 로드와 기본 boss 체력을 출력했다.
- player status는 `참가자 없음`, `관전자 없음`이었다.
- 접속자는 `0 of a max of 20 players online`이었다.
- 준비 인원 없이 `semiontd start`는 정상 실패했다.
- 진행 중 게임 없이 `semiontd spectate red`는 정상 실패했다.
- reset 후 status가 `activeGame=false`, `arenaLoaded=false`로 돌아왔다.
- `stop`으로 서버가 정상 종료됐다.

## P1: 오픈 전 권장 확인

### 3. 프로덕션 타워 배치/카탈로그 정리

현재 플레이어-facing 타워 흐름이 테스트 타워 중심이면 실제 서비스 게임플레이로는 부족하다.

확인 항목:

- 실제 플레이어가 사용할 기본 타워 목록이 있다.
- 배치 명령 또는 UI 흐름이 운영자가 아닌 플레이어 기준으로 자연스럽다.
- 업그레이드 경로가 최소 1개 이상 실전에서 작동한다.

### 4. 소환/타워/경제 밸런스 1차 패스

완벽한 밸런스가 아니라 실제 한 판이 성립하는 최소값을 확인한다.

확인 항목:

- 초반 자원으로 기본 행동이 가능하다.
- 소환 비용과 수입 증가가 과도하게 빠르거나 막히지 않는다.
- 라운드 보상과 보스 체력이 초반 플레이를 망가뜨리지 않는다.
- 기본 타워 DPS가 웨이브를 완전히 무력화하거나 전혀 막지 못하는 상태가 아니다.

### 5. 운영 중단/복구 플로우 확인

운영 중 경기가 꼬였을 때 되돌릴 수 있어야 한다.

확인 항목:

- `/semiontd end`가 경기 종료와 lobby 이동을 안정적으로 처리한다.
- `/semiontd reset`이 active game과 arena 상태를 정리한다.
- 서버 재시작 후 status가 정상적으로 복구 가능한 상태를 보여준다.
- 진행 중 신규 접속자 안내와 관전 전환이 예외 없이 동작한다.

### 6. Polymer/DialogUtils 리소스팩 경고의 실클라 영향 확인

GameTest에서는 known noisy warning으로 통과하지만 실제 클라이언트 표시 영향은 별도로 본다.

확인 항목:

- HUD가 보인다.
- DialogUtils 창이 열린다.
- 리소스팩 경고가 실제 접속/표시를 막지 않는다.

## 이번 단계에서 제외한 항목

- ELO 기반 팀 분배
- match result UI 레이아웃 고도화
- job catalog 확장과 선택 UI 고도화
- T4/T5 전용 Blockbench 모델 추가
- status 출력 추가 정리
