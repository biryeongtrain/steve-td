# 팀장 타깃 유도 시스템 구현 계획

> **For Hermes:** 구현 단계에서는 `subagent-driven-development`, `test-driven-development`, `minecraft-testing`, `karpathy-coding-guidelines`를 로드하고, 작업 단위마다 테스트를 먼저 작성한다.

**Goal:** 각 팀에 팀장을 두고, 팀장이 전용 아이템을 우클릭해서 이후 팀원이 구매하는 견제 유닛의 도착 팀을 3턴 쿨타임으로 지정한다.

**Architecture:** 팀장과 타깃 유도 상태를 `SemionTeam`의 매치 중 상태로 둔다. 소환 경로는 `SemionGame.summonMonster`의 기존 랜덤 타깃 선택 직전에 팀 상태를 조회한다. UI는 기존 핫바 도구와 Dialog 버튼 방식을 재사용한다.

**Tech Stack:** Java, Fabric API `UseItemCallback`, Minecraft Dialog API, JUnit, Fabric GameTest, CodeGraph.

---

## 0. 현재 코드 사실

- 기준 브랜치: `master`
- 기준 커밋: `7241126`
- 워킹트리: clean
- CodeGraph 상태: 303 files, 6,713 nodes, 18,441 edges, up to date

관련 코드:

- `src/main/java/kim/biryeong/semiontd/game/SemionGame.java:357-428`
  - `summonMonster`가 견제 유닛 구매, 비용 차감, 인컴 증가, 타깃 팀/라인 선택, 큐 적재, 빌드가이드 기록을 한 번에 처리한다.
  - 현재 타깃 팀은 `randomTargetTeam(player.teamId())`가 정한다.
  - `LANE_WAVE` 중 구매한 유닛은 `currentRound + 1`에 예약된다.
- `src/main/java/kim/biryeong/semiontd/game/SemionGame.java:739-755`
  - `randomTargetTeam`은 살아 있는 자기 팀 외 후보 중 무작위 선택만 지원한다.
  - `randomTargetLane`은 타깃 팀의 라인 중 무작위 선택이다.
- `src/main/java/kim/biryeong/semiontd/game/SemionGame.java:631-650`
  - 라운드 종료 후 `currentRound++`가 되고 `startPreparePhase`가 다음 턴을 연다.
  - 3턴 쿨타임은 이 라운드 증가 시점에서 차감하는 편이 가장 단순하다.
- `src/main/java/kim/biryeong/semiontd/game/SemionTeam.java:12-102`
  - 팀은 활성/탈락/멤버/라인 그룹만 가진다.
  - 팀장 UUID, 유도 타깃, 쿨타임 필드는 없다.
- `src/main/java/kim/biryeong/semiontd/ui/SemionHotbarService.java:17-110`
  - 매치 시작 시 참가자 인벤토리를 비우고 `타워 관리` 나침반, `견제 소환` 에코 조각을 지급한다.
  - 우클릭 처리는 `UseItemCallback` 하나에서 아이템 이름으로 구분한다.
- `src/main/java/kim/biryeong/semiontd/ui/SemionDialogService.java:483-510`
  - 견제 소환 UI는 Dialog 버튼에서 `/semiontd summon <id>`를 실행한다.
- `src/main/java/kim/biryeong/semiontd/game/PlayerLane.java:321-409`
  - 큐에서 몬스터가 실제 스폰될 때 방어자 `ownLaneIncomingThreat`, 인컴 여부, 누수 성공 `incomeAttackSuccessThreat`가 기록된다.
- `src/main/java/kim/biryeong/semiontd/rating/RatingContributionCalculator.java:40-86`
  - ELO 개인 기여도는 방어 부담, 보낸 인컴 위협, 성공한 인컴 위협, 경제 기여를 사용한다.
- `src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java:57-72`
  - 승패 기반 델타에 개인 기여 multiplier를 곱한다.
- `src/main/java/kim/biryeong/semiontd/rating/RatingEligibilityPolicy.java:24-40`
  - 현재 ELO는 정확히 두 참가 팀, 단일 승리 팀만 평가한다.

## 1. 여파 분석

### 1.1 소환 라우팅

팀장 유도는 `SemionGame.summonMonster`의 타깃 팀 선택을 바꾼다. 기존에는 구매자 팀을 제외한 생존 팀 중 랜덤이었다. 새 로직은 다음 순서로 처리한다.

1. 구매자의 팀을 찾는다.
2. 그 팀에 활성 유도 타깃이 있는지 확인한다.
3. 유도 타깃이 생존 중이고 자기 팀이 아니면 그 팀을 사용한다.
4. 타깃이 사라졌거나 탈락했으면 유도 상태를 지우고 기존 랜덤 로직으로 되돌린다.

`이 효과는 사용 이후부터 유효`라는 요구 때문에 이미 큐에 들어간 견제 유닛은 건드리지 않는다. `PREPARE_AND_SUMMON`에서 이미 구매해 현재 라운드 큐에 들어간 몬스터, `LANE_WAVE`에서 이미 다음 라운드 큐에 들어간 몬스터 모두 기존 타깃을 유지한다.

### 1.2 턴과 쿨타임

현재 게임은 `currentRound`를 턴으로 쓴다. 한 턴은 `PREPARE_AND_SUMMON -> LANE_WAVE -> ROUND_PAYOUT` 흐름이다. 쿨타임은 “사용 이후 3턴”으로 해석한다.

권장 규칙:

- 사용 성공 시 `cooldownRemainingRounds = 3`을 저장한다.
- `tickPayout`에서 다음 라운드로 넘어갈 때 생존 팀의 쿨타임을 1 감소한다.
- `cooldownRemainingRounds == 0`일 때 다시 사용할 수 있다.
- 같은 라운드에서 재사용할 수 없다.

이 규칙이면 5라운드 준비 시간에 사용한 팀장은 6, 7, 8라운드가 쿨타임이고 9라운드부터 다시 쓸 수 있다. 기획 의도가 “사용한 라운드 포함 3턴”이면 이 값만 2로 바꾸면 된다. 구현 전 사용자에게 확인할 만한 유일한 규칙이다.

### 1.3 팀장 선정

현재 참가자 배정에는 팀 내 순서와 라인 번호가 있다. 별도 선출 UI가 없다. 첫 구현은 팀의 가장 낮은 `laneId` 참가자를 팀장으로 삼는 방식이 안전하다.

장점:

- 새 로비 UI를 만들 필요가 없다.
- 테스트가 쉽다.
- 팀당 한 명이라는 불변식을 `SemionGame.start` 과정에서 확정할 수 있다.

나중에 선출/양도 기능을 넣고 싶으면 `SemionTeam.setLeader(UUID)`만 유지하고 선정 경로를 바꾸면 된다.

### 1.4 아이템과 UI

`SemionHotbarService`가 매치 도구 지급과 우클릭 라우팅을 이미 맡는다. 팀장용 아이템도 이 파일에 추가하는 편이 맞다.

권장 UX:

- 팀장에게만 `지휘 깃발` 아이템을 지급한다. 예: `Items.GOAT_HORN` 또는 `Items.ENDER_EYE`.
- 우클릭 시 `SemionDialogService.showLeaderTargetControl(player, game)`를 연다.
- UI는 Minecraft Dialog로 만든다. 별도 화면이나 채팅 선택지는 만들지 않는다.
- Dialog는 생존 중인 자기 팀 외 팀 버튼을 보여준다. 최대 팀 수가 5개라서 버튼은 최대 4개다.
- 타깃 버튼 4개가 한 줄에 들어가도록 버튼 폭을 `TEAM_TARGET_BUTTON_WIDTH` 같은 별도 상수로 둔다. 기존 `COMPACT_BUTTON_WIDTH = 118`은 4개 한 줄에 쓰기엔 크다.
- 버튼 텍스트는 각 팀 색을 쓴다. 예: RED=`<red>RED</red>`, BLUE=`<blue>BLUE</blue>`, GREEN=`<green>GREEN</green>`, YELLOW=`<yellow>YELLOW</yellow>`, PURPLE=`<light_purple>PURPLE</light_purple>`.
- 버튼은 `/semiontd leader target <team>`을 실행한다.
- 쿨타임 중에는 버튼 대신 남은 턴을 표시한다.
- 팀장이 아니면 아이템을 사용할 수 없고, 메시지로 거절한다.

아이템 식별은 현재 코드처럼 이름 문자열만 비교하면 복제/개명에 약하다. 이 작업에서는 `DataComponents.CUSTOM_DATA`나 Persistent Data를 써서 Semion 도구 식별자를 넣는 편이 좋다. 단, 기존 타워/소환 도구까지 한 번에 바꾸면 범위가 커진다. 팀장 아이템에만 안정적인 식별자를 넣고 기존 도구는 유지한다.

### 1.5 명령과 Dialog

Dialog 버튼은 서버 명령을 실행한다. 따라서 `/semiontd leader target <team>` 명령을 추가해야 한다.

명령은 `SemionGame.useLeaderTargetAbility(UUID leaderId, TeamId targetTeam)` 같은 도메인 메서드로 위임한다. 명령 레이어가 팀장 여부, 쿨타임, 타깃 유효성, 상태 갱신을 직접 처리하면 테스트가 어려워진다.

### 1.6 ELO 영향

이 기능은 ELO 산식 자체를 바꾸지 않는다. 하지만 ELO 입력 통계의 분포를 바꾼다.

현재 ELO 개인 기여도는 다음 값에 민감하다.

- 방어자: `ownLaneIncomingThreat`, `incomingIncomeThreat`, `ownLaneLeakedThreat`
- 인컴 유닛을 보낸 플레이어: `sentIncomeThreat`, `incomeAttackSuccessThreat`, `incomeGenerated`
- 팀 내 경제/방어 평균

팀장 유도 이후 특정 팀이 의도적으로 집중 공격을 받을 수 있다. 그러면 다음 효과가 생긴다.

1. 타깃 팀의 방어자들은 `incomingIncomeThreat`가 오른다.
2. 타깃 팀이 잘 막으면 방어 기여도 보너스를 더 받는다.
3. 타깃 팀이 뚫리면 `ownLaneLeakedThreat`가 올라 방어 기여도가 내려간다.
4. 공격 팀의 구매자들은 성공 시 `incomeAttackSuccessThreat`를 더 안정적으로 얻는다.
5. 팀장이 타깃을 골랐지만 구매자는 일반 팀원일 수 있다. 현재 통계는 구매자에게만 압박 기여를 준다.

핵심 위험은 “팀장의 전략 선택”과 “구매자의 자원 지출”이 섞이는 지점이다. 현재 ELO는 팀장 행동을 별도 이벤트로 보지 않는다. 팀장이 좋은 타깃을 골라도 팀장 개인에게 직접 압박 기여가 붙지 않는다. 반대로 팀장이 나쁜 타깃을 골라도 팀장 개인에게 직접 패널티가 없다.

권장 방침:

- 이번 패치에서는 ELO 산식을 바꾸지 않는다.
- 팀장 사용 이벤트를 매치 통계에 최소한으로 남긴다. 예: 팀별 사용 횟수, 타깃 팀, 사용 라운드.
- ELO에는 당장 반영하지 않는다.
- 나중에 개인 기여도에 팀장 전략 지표를 넣을 때 rating policy version을 올린다.

이 방침은 기존 메모리의 ELO 정책과 맞는다. 배치/승패를 primary signal로 두고, 플레이 특성이 영향을 주면 원시 이벤트를 남긴 뒤 정규화와 버전 관리를 거쳐 반영한다.

### 1.7 빌드가이드 영향

`BuildGuideService.recordSummon`은 실제 타깃 팀과 라인을 저장한다. 타깃 유도가 적용되면 빌드가이드에는 바뀐 결과가 그대로 기록된다. 별도 변경 없이도 “구매 결과”는 맞다.

다만 유도 사용 자체를 빌드가이드 액션으로 남기고 싶으면 `BuildAction`에 leader target 액션을 추가해야 한다. 이번 목표에는 필수로 보이지 않는다.

### 1.8 밸런스와 악용 가능성

- 살아 있는 팀이 2개뿐이면 유도와 랜덤 결과가 같다. UI에는 그래도 사용할 수 있게 둘지, “효과 없음”으로 막을지 정해야 한다.
- 타깃 팀이 탈락하면 유도 상태를 자동 해제해야 한다.
- 팀장이 탈락하거나 관전자가 되면 그 팀도 탈락 상태이므로 사용 불가다.
- 팀장 아이템은 참가자 인벤토리 초기화 이후 지급해야 한다.
- 팀장 접속 끊김을 고려하면 `/semiontd leader target <team>` 명령은 온라인 상태에서만 쓸 수 있지만, 이미 설정된 유도 상태는 팀 단위라 유지된다.

## 2. 구현 설계

### 새 도메인 타입

- `src/main/java/kim/biryeong/semiontd/game/LeaderTargetingState.java`
  - `UUID leaderPlayerId`
  - `TeamId targetTeamId`
  - `int cooldownRemainingRounds`
  - `int lastUsedRound`
  - `boolean canUse()`
  - `void use(TeamId targetTeamId, int currentRound, int cooldownRounds)`
  - `void tickRoundCooldown()`
  - `void clearTarget()`

단순하게 하려면 별도 타입 없이 `SemionTeam` 필드로 시작해도 된다. 하지만 상태가 4개 이상이라 별도 타입이 테스트와 가독성에 유리하다.

### `SemionTeam` 변경

- 팀장 UUID를 저장한다.
- `activate` 또는 `deactivate` 때 팀장/타깃 상태를 초기화한다.
- `addPlayer` 이후 팀장 선정은 `SemionGame.activateParticipant`에서 모든 참가자 추가가 끝난 뒤 처리한다. `SemionTeam.addPlayer` 안에서 첫 플레이어를 팀장으로 잡으면 참가자 순서에 의존한다.

### `SemionGame` 변경

- `assignTeamLeaders()`를 `start`의 참가자 활성화 직후 호출한다.
- `leaderTargetingState(TeamId)` 조회 메서드를 제공한다.
- `useLeaderTargetAbility(UUID playerId, TeamId targetTeam)`를 추가한다.
- `summonMonster`에서 `randomTargetTeam` 대신 `targetTeamForSummon(senderTeam)`를 호출한다.
- `tickPayout`에서 생존 팀 쿨타임을 감소한다.
- 팀 탈락 처리 시 다른 팀의 유도 타깃이 탈락 팀을 가리키면 지운다.

### `SemionHotbarService` 변경

- 팀장 아이템 슬롯을 추가한다. 예: slot 2.
- `grantMatchTools(ServerPlayer player)`는 현재 `SemionGame`을 받지 않아서 팀장 여부를 모른다. 선택지는 두 가지다.
  - A안: `SemionGame.placeActivePlayer`에서 `grantMatchTools(player)` 이후 팀장일 때 `grantLeaderTool(player)` 호출.
  - B안: `grantMatchTools(player, game)`로 시그니처 변경.
- A안이 덜 침습적이다.
- `clearMatchTools`에서 팀장 아이템도 제거한다.
- 우클릭 시 `showLeaderTargetControl`을 연다.

### `SemionDialogService` 변경

- `showLeaderTargetControl(ServerPlayer player, SemionGame game)` 추가.
- 본문에 현재 팀, 팀장 여부, 현재 유도 타깃, 남은 쿨타임을 표시한다.
- 자기 팀과 탈락 팀은 버튼에서 제외한다.
- 타깃 버튼은 한 줄에 최대 4개가 들어가도록 `showActions(..., columns = 4)`로 표시한다.
- 버튼 폭은 4개가 한 행에 들어가는 값으로 새 상수를 둔다. 기존 소환 버튼 폭 `SUMMON_BUTTON_WIDTH = 82`를 재사용하거나, 팀 이름 길이에 맞춰 `TEAM_TARGET_BUTTON_WIDTH = 72~82` 범위에서 정한다.
- 버튼 label은 `teamMarkup(teamId)`나 새 `teamButtonLabel(teamId)` helper를 써서 팀 색 MiniMessage로 렌더링한다.
- 쿨타임 중이면 타깃 버튼을 비활성화할 수 없으므로 버튼을 만들지 않고 안내만 보여준다.

### `SemionCommands` 변경

- `/semiontd leader target <team>` 추가.
- 한국어 별칭을 원하면 `/팀장 타깃 <팀>`을 추가한다. 필수는 아니다.
- 성공 시 팀 전체 또는 서버에 안내 메시지를 보낸다.
- 실패 메시지: 팀장 아님, 쿨타임, 잘못된 팀, 자기 팀, 탈락 팀, 진행 중 게임 없음.

### 테스트 전략

- JVM 테스트: 순수 상태와 `SemionGame.summonMonster` 라우팅을 검증한다.
- GameTest: 팀장 아이템 지급/우클릭 Dialog 또는 런타임 소환 라우팅을 검증한다.
- ELO 테스트: 기존 산식이 바뀌지 않고, 라우팅 결과가 현재 기여 통계에 반영되는지 확인한다.

## 3. 작업 계획

### Task 1: 팀장 상태 모델 추가

**Objective:** 팀장 UUID, 현재 유도 타깃, 쿨타임을 한 곳에 둔다.

**Files:**
- Create: `src/main/java/kim/biryeong/semiontd/game/LeaderTargetingState.java`
- Create: `src/test/java/kim/biryeong/semiontd/game/LeaderTargetingStateTest.java`

**Step 1: Write failing test**

테스트 케이스:

- 새 상태는 타깃이 없고 쿨타임 0이다.
- `use(BLUE, 5, 3)` 후 타깃이 BLUE, 쿨타임 3, lastUsedRound 5가 된다.
- `tickRoundCooldown`을 세 번 호출하면 다시 사용 가능하다.
- 음수 쿨타임은 0으로 보정한다.

**Step 2: Verify RED**

Run:

```bash
./gradlew test --tests '*LeaderTargetingStateTest'
```

Expected: `LeaderTargetingState`가 없어 실패.

**Step 3: Implement**

불변식은 클래스 내부에서 지킨다. 외부에서 필드를 직접 만지지 않는다.

**Step 4: Verify GREEN**

Run:

```bash
./gradlew test --tests '*LeaderTargetingStateTest'
```

Expected: pass.

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/LeaderTargetingState.java src/test/java/kim/biryeong/semiontd/game/LeaderTargetingStateTest.java
git commit -m "feat: add team leader targeting state"
```

### Task 2: `SemionTeam`에 팀장 상태 연결

**Objective:** 팀 단위로 팀장과 유도 상태를 보관한다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionTeam.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionTeamLeaderTest.java`

**Step 1: Write failing test**

테스트 케이스:

- `setLeader(playerId)` 후 `leaderPlayerId()`가 값을 반환한다.
- `deactivate()`가 팀장과 유도 상태를 지운다.
- `eliminate()` 후 상태를 사용할 수 없다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionTeamLeaderTest'
```

**Step 3: Implement**

`SemionTeam`에 `LeaderTargetingState leaderTargeting` 필드를 추가하고, `deactivate`와 `eliminate`에서 초기화한다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionTeamLeaderTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/SemionTeam.java src/test/java/kim/biryeong/semiontd/game/SemionTeamLeaderTest.java
git commit -m "feat: track team leader state"
```

### Task 3: 매치 시작 시 팀장 선정

**Objective:** 각 활성 팀의 가장 낮은 라인 번호 참가자를 팀장으로 지정한다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionGameTeamLeaderTest.java`

**Step 1: Write failing test**

테스트 케이스:

- 한 팀에 lane 2, lane 1 참가자가 있으면 lane 1 참가자가 팀장이다.
- 활성 팀마다 한 명만 팀장이다.
- 관전자는 팀장이 될 수 없다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionGameTeamLeaderTest'
```

**Step 3: Implement**

`SemionGame.start`에서 모든 `activateParticipant` 호출이 끝난 뒤 `assignTeamLeaders()`를 호출한다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionGameTeamLeaderTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/SemionGame.java src/test/java/kim/biryeong/semiontd/game/SemionGameTeamLeaderTest.java
git commit -m "feat: assign team leaders at match start"
```

### Task 4: 팀장 능력 도메인 메서드 추가

**Objective:** 팀장 여부, 쿨타임, 타깃 유효성을 도메인 레이어에서 검증한다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Create: `src/main/java/kim/biryeong/semiontd/game/LeaderTargetResult.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderTargetAbilityTest.java`

**Step 1: Write failing test**

테스트 케이스:

- 팀장이 생존 중인 다른 팀을 타깃으로 지정하면 성공한다.
- 일반 팀원은 실패한다.
- 자기 팀 타깃은 실패한다.
- 탈락 팀 타깃은 실패한다.
- 쿨타임 중 재사용은 실패한다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionGameLeaderTargetAbilityTest'
```

**Step 3: Implement**

`LeaderTargetResult` enum을 둔다. 예: `SUCCESS`, `NO_ACTIVE_GAME_STATE`, `PLAYER_NOT_IN_GAME`, `NOT_TEAM_LEADER`, `INVALID_TARGET_TEAM`, `TARGET_SELF_TEAM`, `TARGET_TEAM_NOT_ALIVE`, `COOLDOWN_ACTIVE`.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionGameLeaderTargetAbilityTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/SemionGame.java src/main/java/kim/biryeong/semiontd/game/LeaderTargetResult.java src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderTargetAbilityTest.java
git commit -m "feat: add team leader target ability"
```

### Task 5: 소환 타깃 라우팅 변경

**Objective:** 팀 유도 상태가 활성일 때 이후 구매한 견제 유닛만 지정 팀으로 보낸다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderSummonRoutingTest.java`

**Step 1: Write failing test**

테스트 케이스:

- RED 팀장이 BLUE를 지정한 뒤 RED 팀원이 소환하면 `SummonResult.targetTeam()`이 BLUE다.
- 지정 전에 이미 소환된 몬스터의 `teamId`는 바뀌지 않는다.
- 타깃 팀이 탈락하면 다음 소환은 기존 랜덤 후보를 사용하고 유도 타깃을 지운다.
- `LANE_WAVE` 중 구매한 다음 라운드 예약 몬스터도 현재 유도 타깃을 따른다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionGameLeaderSummonRoutingTest'
```

**Step 3: Implement**

`randomTargetTeam`을 감싸는 `targetTeamForSummon(TeamId senderTeam)`를 만든다. 성공한 유도만 사용하고 유효하지 않은 유도는 지운다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionGameLeaderSummonRoutingTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/SemionGame.java src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderSummonRoutingTest.java
git commit -m "feat: route income units by team leader target"
```

### Task 6: 쿨타임 턴 차감

**Objective:** 3턴 쿨타임을 라운드 진행에 맞춰 감소시킨다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderCooldownTest.java`

**Step 1: Write failing test**

테스트 케이스:

- 사용 직후 남은 쿨타임은 3이다.
- `ROUND_PAYOUT`로 다음 라운드가 시작될 때마다 1 감소한다.
- 3번 감소한 뒤 재사용할 수 있다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionGameLeaderCooldownTest'
```

**Step 3: Implement**

`tickPayout`에서 `currentRound++` 직후 생존 팀 상태를 순회하며 `tickRoundCooldown()`을 호출한다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionGameLeaderCooldownTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/game/SemionGame.java src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderCooldownTest.java
git commit -m "feat: apply team leader cooldown by round"
```

### Task 7: 팀장 아이템 지급과 우클릭 UI 연결

**Objective:** 팀장이 전용 아이템을 우클릭하면 타깃 선택 Dialog가 열린다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/ui/SemionHotbarService.java`
- Modify: `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
- Modify: `src/main/java/kim/biryeong/semiontd/ui/SemionDialogService.java`
- Test: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionLeaderGameTest.java`

**Step 1: Write failing GameTest**

테스트 케이스:

- 팀장 플레이어에게 팀장 아이템이 지급된다.
- 일반 팀원에게는 지급되지 않는다.
- 팀장 아이템 우클릭 핸들러가 `SUCCESS`를 반환한다.
- 타깃 선택 Dialog는 생존 중인 자기 팀 외 팀 버튼을 최대 4개 만든다.
- Dialog는 `columns = 4`를 사용해 네 버튼을 한 줄에 배치한다.
- 각 버튼 label은 `TeamId`별 색상 MiniMessage를 포함한다.

**Step 2: Verify RED**

```bash
./gradlew compileGametestJava
```

GameTest runtime task는 작업 전 확인한다.

```bash
./gradlew tasks --all | grep -i gametest | cat
```

**Step 3: Implement**

`SemionHotbarService.grantLeaderTool(ServerPlayer)`를 추가한다. `SemionGame.placeActivePlayer`에서 팀장일 때 호출한다. 우클릭 처리에는 `isLeaderTool` 분기를 추가한다. `SemionDialogService`에는 `TEAM_TARGET_BUTTON_WIDTH`와 `teamButtonLabel(TeamId)`를 추가하고, `showLeaderTargetControl`은 `showActions(..., columns = 4)`로 타깃 버튼을 렌더링한다.

**Step 4: Verify GREEN**

```bash
./gradlew compileGametestJava
./gradlew runGameTest
```

실제 task 이름이 다르면 `tasks --all` 결과에 맞춘다.

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/ui/SemionHotbarService.java src/main/java/kim/biryeong/semiontd/game/SemionGame.java src/main/java/kim/biryeong/semiontd/ui/SemionDialogService.java src/gametest/java/kim/biryeong/semiontd/gametest/SemionLeaderGameTest.java
git commit -m "feat: add team leader target tool"
```

### Task 8: 명령 추가

**Objective:** Dialog 버튼이 호출할 서버 명령으로 팀장 타깃을 설정한다.

**Files:**
- Modify: `src/main/java/kim/biryeong/semiontd/command/SemionCommands.java`
- Test: `src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderTargetAbilityTest.java`

**Step 1: Write failing test**

명령 자체는 Brigadier/Fabric 런타임 의존이 크다. 도메인 메서드 테스트를 먼저 통과시킨 뒤, 실패 메시지 매핑은 작은 private/static helper로 분리해서 테스트한다.

**Step 2: Verify RED**

```bash
./gradlew test --tests '*SemionGameLeaderTargetAbilityTest'
```

**Step 3: Implement**

`/semiontd leader target <team>`를 추가하고 `LeaderTargetResult`를 한국어 메시지로 변환한다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*SemionGameLeaderTargetAbilityTest'
```

**Step 5: Commit**

```bash
git add src/main/java/kim/biryeong/semiontd/command/SemionCommands.java src/test/java/kim/biryeong/semiontd/game/SemionGameLeaderTargetAbilityTest.java
git commit -m "feat: add team leader target command"
```

### Task 9: ELO 회귀 테스트

**Objective:** 팀장 유도 기능이 ELO 산식을 직접 바꾸지 않으며, 기존 기여 통계 흐름은 유지됨을 검증한다.

**Files:**
- Modify: `src/test/java/kim/biryeong/semiontd/rating/RatingContributionCalculatorTest.java`
- Modify: `src/gametest/java/kim/biryeong/semiontd/gametest/SemionRatingGameTest.java`

**Step 1: Write failing or protective tests**

테스트 케이스:

- 타깃 유도 후 스폰된 인컴 유닛은 타깃 팀 라인 소유자의 `incomingIncomeThreat`를 올린다.
- 인컴 유닛을 보낸 플레이어는 기존처럼 `sentIncomeThreat`와 누수 성공 시 `incomeAttackSuccessThreat`를 얻는다.
- 팀장 개인에게 별도 ELO 기여를 주지 않는다.

**Step 2: Verify RED/Protective**

기존 코드가 일부 통과할 수 있다. 새 라우팅 경로를 포함해야 한다.

```bash
./gradlew test --tests '*RatingContributionCalculatorTest' --tests '*PlayerMatchStatsAttributionTest'
./gradlew compileGametestJava
```

**Step 3: Implement missing assertions**

라우팅 변경으로 깨진 부분만 수정한다. ELO 산식을 바꾸지 않는다.

**Step 4: Verify GREEN**

```bash
./gradlew test --tests '*RatingContributionCalculatorTest' --tests '*PlayerMatchStatsAttributionTest'
./gradlew runGameTest
```

**Step 5: Commit**

```bash
git add src/test/java/kim/biryeong/semiontd/rating/RatingContributionCalculatorTest.java src/gametest/java/kim/biryeong/semiontd/gametest/SemionRatingGameTest.java
git commit -m "test: cover rating stats for leader-targeted summons"
```

### Task 10: 최종 검증

**Objective:** JVM, compile, GameTest까지 통과시킨다.

**Commands:**

```bash
./gradlew compileJava
./gradlew test
./gradlew compileGametestJava
./gradlew tasks --all | grep -i gametest | cat
./gradlew runGameTest
./gradlew build
git diff --check
```

`runGameTest` task 이름이 없으면 Gradle task 목록에서 실제 GameTest task를 찾아 대체한다. 없는 task를 통과했다고 보고하지 않는다.

## 4. 결정 필요 사항

1. 3턴 쿨타임이 사용 라운드를 포함하는지 확인해야 한다.
   - 권장: 사용 이후 다음 3라운드 동안 사용 불가.
2. 팀장 선정 규칙을 확인해야 한다.
   - 권장: 팀 내 가장 낮은 라인 번호 참가자.
3. 두 팀만 남았을 때도 능력 사용을 허용할지 정해야 한다.
   - 권장: 허용한다. 결과가 랜덤과 같아도 플레이어에게 명시적 통제감을 준다.
4. 팀장 전략 기여를 ELO에 넣을지 정해야 한다.
   - 권장: 이번 패치에서는 넣지 않는다. 이벤트만 남기고 다음 rating policy version에서 검토한다.

## 5. 최종 권장안

이번 패치는 “팀 단위 타깃 라우팅”으로 좁힌다. ELO 공식은 건드리지 않는다. 대신 팀장 사용 이벤트와 라우팅 결과가 기존 통계에 정확히 흘러가는지 테스트한다.

이렇게 하면 기능은 플레이에 바로 영향을 주고, rating은 기존 정책을 유지한다. 나중에 팀장 전략 점수를 추가할 때는 별도 설계로 trait-aware contribution normalization과 rating policy versioning을 함께 처리한다.
