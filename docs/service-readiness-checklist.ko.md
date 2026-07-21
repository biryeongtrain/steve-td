# 서비스 준비 체크리스트

이 문서는 Semion TD를 실제 미니게임으로 열 때 장애나 플레이 불능으로 이어질 수 있는 항목만 추린다.
서비스에 직접 문제를 만들 가능성이 낮은 확장/고도화 작업은 제외한다.

## 2026-07-16 갱신 상태

- 현재 기준은 Minecraft `1.21.8`, Fabric Loader `0.19.2`, Java `21` target이다.
- 단위 테스트와 264개 Fabric GameTest를 배포 전 필수 검사로 사용한다.
- 치장 시스템은 머리와 왼손 슬롯을 지원한다. 120개 내장 Polymer 아이템 중 119개는 머리, `rabbit_body`는 왼손 치장이다.
- 구매 리소스인 `src/main/resources/assets/semion-td/`는 Git에서 제외한다. 운영 JAR을 빌드할 작업 환경에는 별도 원본이 필요하다.
- 2026-05-11 아래 기록은 과거 QA 증거다. 현재 배포 승인에는 새 JAR로 테스트와 실클라이언트 리소스팩·치장 표시 확인을 다시 남긴다.

## P0: 오픈 전 필수 확인

### 1. 실플레이어 수동 QA

GameTest와 콘솔 QA로는 실제 클라이언트의 teleport, HUD mount, DialogUtils 표시를 완전히 검증할 수 없다.

Carpet fake player로 검증 가능한 항목:

- fake player가 `/semiontd ready`를 실행할 수 있다.
- 관리자가 `/semiontd start`로 경기를 시작할 수 있다.
- 진행 중 신규 fake player는 lobby 안내 대상이 되고 `/semiontd spectate`로 관전 전환할 수 있다.
- `/semiontd spectate red|blue|green|yellow`가 active/inactive 팀 규칙을 지킨다.
- 팀 선택 관전 후 fake player의 world/좌표가 바뀐다.
- active participant가 관전 전환을 시도하면 실패한다.
- `/semiontd end` 또는 `/semiontd reset` 후 서버 상태가 `activeGame=false`, `arenaLoaded=false`로 돌아간다.
- `/semiontd ui`, `economy`, `profile`, `job list/current/select`, `summons`가 서버 예외 없이 응답한다.

실클라 전용 확인 항목:

- 실제 클라이언트 접속자가 게임 진행 중이 아니면 lobby로 이동하고 화면 표시가 정상이다.
- spectator HUD가 현재 world의 팀 보스 체력을 실제 화면에 보여준다.
- 월드 이동 후 DisplayHud가 클라이언트 렌더링에서 사라지지 않고 다시 mount된다.
- DialogUtils 창이 실제 클라이언트에서 읽기 좋게 표시된다.
- Polymer resource pack이 접속과 표시를 막지 않는다.

완료 기준:

- 2명 이상 실제 클라이언트로 ready/start/spectate/end/reset 흐름을 통과한다.
- spectator HUD가 팀 선택 관전 이동 후에도 유지된다.
- 치명적인 클라이언트 표시 오류나 서버 예외가 없다.

현재 상태:

- 2026-05-11 콘솔 QA에서 서버 기동과 운영 명령 기본 흐름은 확인했다.
- 2026-05-11 실클라이언트 QA를 위해 서버를 다시 기동했지만 접속자가 없어 ready/start/HUD/mount/관전 시야 항목은 아직 미완료다.
- 2026-05-11 Carpet fake player QA로 4명 NORMAL ready/start, 팀 선택 관전 성공/실패, `ui`/`economy`/`summons` 명령 서버 처리, `end`/`reset` 복구 흐름은 확인했다.
- 2026-05-11 추가 Carpet smoke에서 2명 TEST start, `summon grunt`, `economy`, `ui`, `end`, `reset`, `create`, `reset` 반복 복구를 다시 확인했다.
- 2026-05-11 Carpet tower QA에서 `/semiontd status lanes`의 `towerSample=-26,145,50`으로 fake player를 이동한 뒤 `semiontd tower test` 성공과 `towers=1` 반영을 확인했다.
- Carpet으로 닫은 서버 항목은 통과 처리한다. 실제 클라이언트 HUD 렌더링, DialogUtils 화면 표시, 리소스팩 적용, 관전 시야 품질은 여전히 실클라이언트로 확인해야 한다.

### 2. 맵 템플릿 실사용 QA

맵 템플릿은 GameTest에서 구조 일부를 검증해도 실제 플레이 동선과 시야 문제가 남을 수 있다.

Carpet fake player로 검증 가능한 항목:

- lobby spawn이 안전하고 명확하다.
- 각 팀 arena가 정상 로드된다.
- active player spawn이 각 lane 시작 위치에 맞다.
- spectator spawn이 팀 runtime world의 spectator spawn 좌표로 이동한다.
- lane path가 서버 로직상 끊기지 않고 몬스터가 진행 가능하다.
- final lane과 boss convergence 지점이 의도한 위치에 모인다.
- boss spawn이 팀별로 올바르게 생성된다.
- tower placement 가능 영역은 GameTest와 fake player 좌표 이동으로 검증할 수 있다.
- `/semiontd reset` 후 lobby/arena 상태가 다시 정상화된다.

실클라 전용 확인 항목:

- spectator spawn이 실제 관전 시야로 충분한지 확인한다.
- lane path, final lane, boss convergence가 플레이어 시야에서 이해 가능한지 확인한다.
- tower placement 영역이 실제 플레이 중 눈으로 구분 가능한지 확인한다.

완료 기준:

- RED/BLUE/GREEN/YELLOW arena 로드와 boss spawn이 모두 확인된다.
- 최소 한 라운드 진행 중 몬스터 경로, 보스 도달, 관전자 시야에 치명 문제가 없다.
- reset 후 재생성이 가능하다.

현재 상태:

- 2026-05-11 `./gradlew runServer --console=plain` 기동 성공.
- `semiontd create` 후 `arenaLoaded=5/5` 확인.
- `semiontd status teams`에서 RED/BLUE/GREEN/YELLOW 모두 `arenaLoaded=true`, `boss=1000/1000` 확인.
- `semiontd reset` 후 `activeGame=false`, `arenaLoaded=false` 복구 확인.
- 2026-05-11 재기동 smoke에서도 `arenaLoaded=5/5`, 네 팀 arena/boss 상태, reset 복구가 다시 확인됐다.
- 2026-05-11 Carpet fake player QA에서 RED/BLUE active team 배정, fake player 위치 이동, RED/BLUE 팀 선택 관전 이동을 확인했다.
- 2026-05-11 추가 Carpet smoke에서 TEST 2인 `summon grunt`가 상대 active team lane으로 큐잉되고, `end`/`reset` 후 `create`/`reset` 반복 복구가 성공했다.
- 2026-05-11 Carpet tower QA에서 active spawn 위치의 `tower test` 정상 실패와, `status lanes`의 laneArea 중심 `towerSample` 이동 후 `tower test` 성공을 모두 확인했다.
- 실제 클라이언트 시야, final lane, boss convergence 체감 QA는 접속자 부재로 아직 미완료다.
- 반복 실행 절차는 [Carpet QA Runbook](carpet-qa-runbook.ko.md)에 분리했다.

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
- create 후 status는 `activeGame=true`, `phase=WAITING`, `round=1`, `ready=0`, `activeParticipants=0`, `spectators=0`, `arenaLoaded=5/5`였다.
- team status는 네 팀 모두 arena 로드와 기본 boss 체력을 출력했다.
- player status는 `참가자 없음`, `관전자 없음`이었다.
- 접속자는 `0 of a max of 20 players online`이었다.
- 준비 인원 없이 `semiontd start`는 정상 실패했다.
- 진행 중 게임 없이 `semiontd spectate red`는 정상 실패했다.
- reset 후 status가 `activeGame=false`, `arenaLoaded=false`로 돌아왔다.
- `stop`으로 서버가 정상 종료됐다.

## 2026-05-11 실클라이언트 QA 시도 기록

실행 명령:

```text
./gradlew runServer --console=plain
list
list
list
semiontd status
semiontd create
semiontd status
semiontd status teams
semiontd reset
semiontd status
stop
```

확인 결과:

- 서버가 `*:25565`로 정상 기동했다.
- `Semion TD initialized.` 로그가 출력됐다.
- Polymer resource pack 생성이 성공했다.
- 접속 대기 중 `list` 결과가 계속 `0 of a max of 20 players online`이었다.
- 실제 클라이언트 접속자가 없어 2인 ready/start/spectate/HUD/mount 검증은 진행하지 못했다.
- 초기 status는 `activeGame=false`, `lobbyLoaded=true`, `arenaLoaded=false`였다.
- create 후 status는 `activeGame=true`, `phase=WAITING`, `round=1`, `ready=0`, `activeParticipants=0`, `spectators=0`, `arenaLoaded=5/5`였다.
- team status는 네 팀 모두 `arenaLoaded=true`, `boss=1000/1000`을 출력했다.
- reset 후 status가 `activeGame=false`, `arenaLoaded=false`로 돌아왔다.
- `stop`으로 서버가 정상 종료됐고, 25565 리스너가 남지 않았다.

## 2026-05-11 Carpet fake player QA 기록

Carpet은 운영 배포물에 포함하지 않고 로컬 QA 런타임 의존성으로만 둔다.

실행 흐름:

```text
./gradlew runServer --console=plain
semiontd create
player qared spawn
player qablue spawn
player qagreen spawn
player qayellow spawn
execute as qared run semiontd ready
execute as qablue run semiontd ready
execute as qagreen run semiontd ready
execute as qayellow run semiontd ready
semiontd start
semiontd status
semiontd status teams
semiontd status players
execute as qared run semiontd spectate blue
player qaspec spawn
execute as qaspec run semiontd spectate red
execute as qaspec run semiontd spectate green
execute as qaspec run semiontd spectate blue
execute as qared run semiontd economy
execute as qared run semiontd summons
execute as qared run semiontd ui
execute as qaspec run semiontd ui
semiontd end
semiontd reset
stop
```

확인 결과:

- Carpet `player ... spawn`으로 `qared`, `qablue`, `qagreen`, `qayellow` 4명을 투입했다.
- 4명 ready 후 NORMAL 모드 start가 성공했고, status가 `activeParticipants=4`, `spectators=0`, `arenaLoaded=5/5`를 출력했다.
- `status teams`에서 RED/BLUE는 active player 2명과 lane 2개, GREEN/YELLOW는 inactive로 출력됐다.
- active participant가 `/semiontd spectate blue`를 실행하면 `현재 상태에서는 관전으로 전환할 수 없습니다.`로 실패했다.
- 신규 fake player `qaspec`은 `/semiontd spectate red`와 `/semiontd spectate blue`에 성공했고, inactive GREEN 관전은 `현재 관전할 수 없는 팀입니다: GREEN`으로 실패했다.
- `semiontd economy`, `semiontd summons`, `semiontd ui`가 fake player 실행에서 서버 예외 없이 응답했다.
- Carpet fake player는 cross-dimension lobby teleport 중 내부 NPE를 낼 수 있어, reset/end는 해당 플레이어만 disconnect 처리하고 전체 reset을 계속 진행하도록 보강했다.
- 보강 후 `semiontd end`와 `semiontd reset`이 모두 성공했고, 최종 status가 `activeGame=false`, `arenaLoaded=false`로 복구됐다.
- fake player 프로필 조회 경고와 Polymer/DialogUtils 경고는 이 QA 흐름에서 치명 오류가 아니었다.

## 2026-05-11 Carpet fake player 추가 QA 기록

실행 흐름:

```text
./gradlew runServer --console=plain
semiontd create
player qared spawn
player qablue spawn
player qagreen spawn
player qayellow spawn
execute as qared run semiontd ready
execute as qablue run semiontd ready
execute as qagreen run semiontd ready
execute as qayellow run semiontd ready
semiontd start
execute as qared run semiontd summon grunt
execute as qared run semiontd economy
execute as qared run semiontd ui
player qaspec spawn
execute as qaspec run semiontd spectate red
execute as qaspec run semiontd spectate green
execute as qaspec run semiontd spectate blue
semiontd end
semiontd create
semiontd testmode true
player qatower1 spawn
player qatower2 spawn
execute as qatower1 run semiontd ready
execute as qatower2 run semiontd ready
semiontd start
semiontd status lanes
execute as qatower1 run tp @s -26 145 50
execute as qatower1 run data get entity @s Pos
execute as qatower1 run semiontd tower test
execute as qatower1 run semiontd tower upgrades
semiontd status lanes
execute as qatower1 run semiontd summon grunt
semiontd end
semiontd reset
semiontd create
semiontd status
semiontd reset
semiontd status
stop
```

확인 결과:

- NORMAL 4인 ready/start는 유지됐다.
- 진행 중 신규 `qaspec`의 RED/BLUE 팀 선택 관전은 성공했고, inactive GREEN 관전은 실패했다.
- `summon grunt`, `economy`, `ui`는 fake player 실행에서 서버 예외 없이 응답했다.
- TEST 2인 ready/start가 성공했고, `summon grunt`는 상대 active team lane으로 큐잉됐다.
- active spawn에서 `tower test`는 `lane_path 영역 안에서 실행하세요`로 실패했다. 이 실패는 active spawn이 tower placement 영역이 아님을 보여주는 정상 방어 동작이다.
- `/semiontd status lanes`가 `towerSample=-26,145,50`, `laneArea=-47,145,47..-5,145,53`를 출력했고, `qatower1`을 해당 좌표로 이동시킨 뒤 `tower test`가 `테스트 타워를 설치했습니다: BlockPos{x=-26, y=145, z=50}`로 성공했다.
- 직후 `/semiontd status lanes`에서 RED 라인이 `towers=1`로 갱신됐다.
- `end`/`reset`/`create`/`reset` 반복 후 최종 status가 `activeGame=false`, `arenaLoaded=false`로 복구됐다.

## P1: 오픈 전 권장 확인

### 3. 프로덕션 타워 배치/카탈로그 정리

프로덕션 타워 등록 방식은 [프로덕션 타워 카탈로그](production-tower-catalog.ko.md)에 정리되어 있다. 현재 built-in reload는 주민, 주민 ADV, 언데드, 동물, 흑마법사, 무리, 무블룸, 우민 카탈로그와 빌더별 허용 타워를 등록한다.
엔티티를 가진 타워 런타임은 `EntityBackedTower`/`SemionTowerEntity`로 공용화되어 있으며, `ProductionTower`는 `TestTower`와 독립적인 형제 계열이다.

확인 항목:

- 실제 플레이어가 사용할 기본 타워 목록은 존재한다. 오픈 전에는 직업별 starter 노출, 업그레이드 비용, 특수 효과 밸런스를 수동 QA로 확인해야 한다.
- 배치 명령 또는 UI 흐름이 운영자가 아닌 플레이어 기준으로 자연스럽다. 2026-05-13 기준 `/semiontd tower list`, `/semiontd tower build <id>`를 추가했다.
- 업그레이드 경로가 최소 1개 이상 실전에서 작동한다. 기존 test tower evolution은 유지되지만, 프로덕션 타워 전용 upgrade tree는 다음 밸런스 패스에서 별도로 확장한다.
- 새 프로덕션 타워가 기본 `ProductionTower::new` factory 또는 명시적인 `EntityBackedTower` 구현 factory로 등록되어 `semion-td:tower` 엔티티를 생성한다.

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
- job 선택 UI 고도화
- T4/T5 전용 Blockbench 모델 추가
- status 출력 추가 정리
