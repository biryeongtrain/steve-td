# TrueSkill2 기반 매치메이킹 및 ELO 기획안

이 문서는 Semion TD에 내부 MMR과 표시용 ELO를 추가하기 위한 구현 전 기획안이다.
현재 코드는 경기 종료 시 `MatchResult`를 만들고 `ProgressionService`가 승패/플레이 수/꾸미기 재화를 저장한다. 새 시스템은 이 흐름 위에 rating 갱신과 시작 전 팀 편성기를 얹는다.

## 목표

- 실제 팀 실력 차이가 작은 경기를 만든다.
- 신규/복귀 플레이어의 불확실성을 rating에 반영한다.
- 플레이어에게는 이해하기 쉬운 ELO/티어를 보여주고, 내부 매칭은 TrueSkill2 계열의 `평균 실력 + 불확실성` 모델로 처리한다.
- 5팀 경쟁 TD, 팀당 최대 5명, 최대 25명 active player 구조를 유지한다.
- 25명 이상도 ready 상태가 될 수 있다. 시작 시 active player만 최대 25명으로 자르고, 초과 ready player는 관전자/다음 매치 우선권으로 회전시켜 대기 불만을 줄인다.

## 용어

| 용어 | 의미 |
|---|---|
| Rating mean `mu` | 내부 실력 평균. 높을수록 강한 플레이어로 추정한다. |
| Rating deviation `sigma` | 내부 실력 불확실성. 신규/오래 쉰 플레이어는 높고, 경기 수가 쌓이면 낮아진다. |
| Conservative rating | 매칭/표시 안정성을 위해 `mu - k * sigma`처럼 계산한 보수 점수. |
| Display ELO | 플레이어에게 보여주는 정수 점수. 내부 `mu/sigma`를 직접 노출하지 않는다. |
| Placement | 신규 플레이어가 충분한 경기 수를 채우기 전의 임시 배치 구간. |
| Match quality | 팀 편성 후보가 얼마나 공정한지 나타내는 내부 점수. |

## 외부 근거

Microsoft Research의 TrueSkill 문서는 Elo의 일반화로서 불확실성 추적, 무승부 모델링, 다인/팀 경기에서 개인 실력 추론을 지원한다고 설명한다. TrueSkill2 문서는 온라인 멀티플레이어 매치메이킹에서 과거 경기 결과로 다음 경기 승리 능력을 추정하고, Halo 5 과거 매치 예측에서 TrueSkill보다 높은 정확도를 보였다고 보고한다.

Semion TD에는 다음 특성이 있으므로 단순 Elo보다 TrueSkill 계열이 맞다.

- 1:1이 아니라 최대 5개 팀, 팀당 1-5명이다.
- 같은 팀 안에서도 개인 lane 성과가 갈린다.
- 신규 플레이어, 파티, 관전자 회전 같은 운영 변수가 있다.
- 승패만 저장하면 너무 거칠고, 라운드/소환/처치/수입 같은 보조 지표를 나중에 rating 보정에 쓸 수 있다.

## 설계 원칙

- Phase 1은 승패 기반 팀 rating만 구현한다. 개인 스탯 보정은 데이터가 쌓인 뒤 켠다.
- 표시용 ELO는 내부 매칭 점수와 분리한다.
- 신규 플레이어는 낮은 확신도로 취급해서 팀 배정에는 보수적으로 섞고, ELO 변동은 크게 둔다.
- 팀 결과가 rating의 주 신호다. 개인 스탯은 보조 신호로만 사용한다.
- 파티/친구 고정 팀이 생기면 팀 시너지를 별도 보정값으로 둔다.
- 수치 파라미터는 config로 둬서 운영 중 튜닝 가능하게 한다.

## 현재 코드 접점

| 영역 | 현재 역할 | 변경 방향 |
|---|---|---|
| `ParticipantSelectionService` | ready player를 섞고 팀 수/팀 크기를 결정한다. | rating 기반 후보 생성과 팀 품질 평가를 추가한다. |
| `ParticipantSelectionPlan` | active/spectator/팀 배정 결과를 담는다. | 매치 품질, 평균 팀 rating, spectator 선정 이유를 선택적으로 담는다. |
| `SemionGame.matchResult()` | 승리 팀, 참가자, 최종 라운드, 개인 스탯을 만든다. | elimination order/팀별 생존 라운드를 추가하면 rating 품질이 좋아진다. |
| `ProgressionService.applyMatchResult()` | 승패/재화 프로필을 저장한다. | rating 업데이트를 같은 transaction 흐름에서 실행한다. |
| `SemionPlayerProfile` | 플레이 수, 승패, 재화, 선택 직업을 저장한다. | 별도 rating record를 추가하거나 profile 안에 rating 필드를 확장한다. |
| `/semiontd profile` | 현재 프로필을 보여준다. | ELO, 티어, placement 상태, 최근 변동량을 보여준다. |

## 데이터 모델

Phase 1에서는 `SemionPlayerProfile`에 직접 필드를 추가하기보다 rating 전용 record를 분리하는 쪽이 안전하다. 기존 프로필 저장 스키마와 progression UI 변경 범위를 줄일 수 있다.

```text
SemionRatingProfile
  lastKnownName: string
  gamesPlayed: int
  wins: int
  losses: int
  mu: double
  sigma: double
  displayElo: int
  peakDisplayElo: int
  placementGamesRemaining: int
  lastDeltaElo: int
  lastPlayedAtEpochMillis: long
```

추후 확장 필드:

```text
modeRatings: map<string, RatingState>
jobRatings: map<string, RatingState>
partyPenaltyOrBonus: double
disconnectCount: int
recentMatchIds: list<string>
```

기본값 초안:

| 값 | 기본값 | 설명 |
|---|---:|---|
| `initialMu` | `25.0` | TrueSkill 계열에서 흔히 쓰는 시작 평균값. |
| `initialSigma` | `8.333333` | `initialMu / 3`. 신규 불확실성을 크게 둔다. |
| `beta` | `4.166666` | 경기 결과 노이즈. `initialMu / 6`부터 시작한다. |
| `dynamicsFactor` | `0.083333` | 오래 플레이하지 않은 유저의 불확실성 회복 폭. |
| `conservativeSigmaMultiplier` | `3.0` | 보수 점수 계산용. |
| `baseDisplayElo` | `1000` | 표시용 시작 ELO. |
| `displayEloScale` | `25` | `mu` 1 차이를 ELO 몇 점으로 볼지. |
| `placementGames` | `10` | 배치 경기 수. |

표시용 ELO 초안:

```text
displayElo = round(baseDisplayElo + displayEloScale * (mu - initialMu))
```

운영 UI에는 `displayElo`를 보여주되, 매칭 정렬에는 `mu`, `sigma`, conservative rating을 사용한다.

## 매치메이킹 흐름

### 1. Ready 후보 수집

- `/semiontd ready`한 플레이어만 active 후보로 본다.
- 진행 중 신규 접속자는 지금처럼 spectator 전환 대상이다.
- ready 인원이 4명 미만이면 NORMAL 시작 불가를 유지한다.
- ready 자체에는 25명 상한을 걸지 않는다. 26번째 이후 플레이어도 ready 성공해야 한다.
- ready 인원이 25명을 초과하면 최대 25명만 active로 고르고 나머지는 spectator로 보낸다.

### 2. Active 후보 선정

25명 이하:

- 모든 ready player를 active 후보로 사용한다.

25명 초과:

- 최근 spectator였던 플레이어에게 우선권을 준다.
- placement player가 너무 많이 몰리면 팀별로 분산한다.
- 최종 active 25명은 rating 분포가 전체 ready 분포를 크게 왜곡하지 않도록 고른다.
- 제외된 플레이어는 `nextMatchPriorityPlayerIds`에 넣어 다음 매치 우선권을 준다.

### 3. 팀 수와 팀 크기 결정

현재 `ParticipantSelectionService.shapeFor(...)`의 원칙을 유지한다.

- TEST: 2명, 1:1
- NORMAL: 최소 4명, 팀당 최대 5명
- 가능한 경우 팀별 최소 3명을 선호
- 불가능하면 팀별 최소 2명 fallback

### 4. 팀 편성 후보 생성

초기 구현은 완전탐색이 아니라 제한된 후보 탐색으로 충분하다.

1. player를 conservative rating 기준으로 정렬한다.
2. snake draft로 초기 팀을 만든다.
3. 무작위 swap 후보를 여러 개 만든다.
4. 각 후보의 match quality를 계산한다.
5. 가장 품질이 좋은 후보를 선택한다.

후보 수 초안:

| active player | 후보 수 |
|---:|---:|
| 4-8 | 200 |
| 9-15 | 600 |
| 16-25 | 1200 |

### 5. Match quality 계산

팀 rating:

```text
teamMu = sum(player.mu)
teamVariance = sum(player.sigma^2 + beta^2)
teamSigma = sqrt(teamVariance)
teamConservative = teamMu - conservativeSigmaMultiplier * teamSigma
```

품질 점수는 낮을수록 좋게 둔다.

```text
qualityCost =
  teamMeanSpreadWeight * spread(teamMu)
  + teamConservativeSpreadWeight * spread(teamConservative)
  + placementImbalanceWeight * spread(placementCountByTeam)
  + partyImbalanceWeight * partyPenalty
  + repeatTeamWeight * repeatTeammatePenalty
```

Phase 1에서는 `teamMeanSpreadWeight`, `teamConservativeSpreadWeight`, `placementImbalanceWeight`만 적용한다.

## Rating 업데이트

### Phase 1: 팀 승패 기반

경기 종료 후 `MatchResult` 기준으로 업데이트한다.

- winning team은 1위.
- losing team들은 공동 2위.
- 우승팀이 없으면 무승부 또는 무효 경기로 처리한다.
- 참가자가 너무 적거나 강제 종료된 운영 종료면 rating 갱신을 생략할 수 있어야 한다.

현재 `MatchResult`는 loser 사이의 순위를 알 수 없으므로 첫 구현은 "승리팀 vs 나머지"만 반영한다.

### Phase 2: elimination order 추가

팀별 탈락 라운드/탈락 tick을 저장하면 더 좋은 신호가 된다.

```text
TeamMatchResult
  teamId
  placement
  eliminatedRound
  eliminatedTick
  bossDamageTaken
```

이후 rating 업데이트는 다음 순위를 사용할 수 있다.

```text
1위: 최종 생존 팀
2위: 가장 늦게 탈락한 팀
3위: 그 이전 탈락 팀
4위: 가장 먼저 탈락한 팀
```

### Phase 3: 개인 성과 보조 신호

TrueSkill2는 경기 결과 외의 보조 지표를 모델에 넣을 수 있지만, 처음부터 개인 지표를 강하게 넣으면 TD 메타를 왜곡할 수 있다.

Semion TD에서 후보 지표:

| 지표 | 용도 | 주의점 |
|---|---|---|
| `finalIncome` | 경제 성장 능력 | 팀 전략상 한 명이 희생할 수 있다. |
| `summonedMonsters` | 견제 기여 | 유닛 비용/타이밍을 같이 봐야 한다. |
| `monsterKills` | 수비 기여 | lane 난이도와 팀 지원 영향을 받는다. |
| `killMinerals` | 처치 가치 | 막타 편향이 있다. |
| finalRound | 경기 길이 보정 | 긴 경기와 실력 차이를 단순 연결하면 안 된다. |

권장 방향:

- 개인 스탯은 rating 변경량의 10-20% 이내 보정으로만 둔다.
- 승리한 팀의 낮은 스탯 플레이어도 승리 rating은 받는다.
- 패배한 팀의 높은 스탯 플레이어도 패배 rating 하락을 완전히 피하지는 못한다.
- 최소 200-500경기 로그를 모은 뒤 켠다.

## ELO/티어 표시

표시 텍스트 초안:

```text
ELO 1042 (배치 6/10)
ELO 1280 Gold II (+18)
ELO 972 Bronze I (-11)
```

티어 초안:

| 티어 | ELO |
|---|---:|
| Iron | 0-899 |
| Bronze | 900-1049 |
| Silver | 1050-1199 |
| Gold | 1200-1399 |
| Platinum | 1400-1599 |
| Diamond | 1600-1849 |
| Master | 1850+ |

커맨드 변경:

- `/semiontd profile`: 현재 ELO, 티어, 배치 상태, 승패를 표시한다.
- `/semiontd rating`: 자기 rating 상세를 표시한다.
- `/semiontd rating <player>`: 관리자/운영자용 조회.
- `/semiontd leaderboard`: 상위 ELO 목록.

한국어 문구는 기존 스타일에 맞춰 짧게 둔다.

```text
프로필 ELO=1280 Gold II, 배치완료, 최근=+18, 플레이=42, 승=25, 패=17
```

## Config 초안

```json
{
  "matchmaking": {
    "enabled": true,
    "ratingBasedTeamAssignment": true,
    "ratingUpdatesEnabled": true,
    "maxActivePlayers": 25,
    "candidateSearchAttempts": 1200,
    "teamMeanSpreadWeight": 1.0,
    "teamConservativeSpreadWeight": 0.7,
    "placementImbalanceWeight": 50.0,
    "repeatTeammateWeight": 0.0
  },
  "rating": {
    "initialMu": 25.0,
    "initialSigma": 8.333333,
    "beta": 4.166666,
    "dynamicsFactor": 0.083333,
    "conservativeSigmaMultiplier": 3.0,
    "baseDisplayElo": 1000,
    "displayEloScale": 25,
    "placementGames": 10,
    "minRatedParticipants": 4,
    "skipRatingOnAdminEnd": true
  }
}
```

## 저장 파일 초안

기존 progression store와 분리한다.

```text
run/semiontd/ratings.json
```

형태:

```json
{
  "00000000-0000-0000-0000-000000000000": {
    "lastKnownName": "Player",
    "gamesPlayed": 12,
    "wins": 7,
    "losses": 5,
    "mu": 27.15,
    "sigma": 5.92,
    "displayElo": 1054,
    "peakDisplayElo": 1072,
    "placementGamesRemaining": 0,
    "lastDeltaElo": 18,
    "lastPlayedAtEpochMillis": 0
  }
}
```

## 구현 순서

### 1단계: Rating 저장소와 표시

- `SemionRatingProfile` 추가.
- `SemionRatingStore` 추가.
- `SemionRatingConfig` 추가.
- `/semiontd profile`에 ELO 표시 추가.
- 기존 progression 저장과 충돌하지 않는지 GameTest 추가.

완료 기준:

- 신규 플레이어 profile 조회 시 기본 ELO가 표시된다.
- 저장/로드 후 rating 값이 유지된다.

### 2단계: Rating 기반 팀 편성

- `ParticipantSelectionService`에 rating 입력을 받는 overload 추가.
- 기존 random selection은 config fallback으로 유지한다.
- 팀 rating spread가 낮은 plan을 선택한다.
- 25명 초과 ready player spectator 회전 정책을 명시한다.
- 26번째 이후 플레이어도 `/semiontd ready`에 성공하고, 시작 시 spectator로 밀리는지 검증한다.

완료 기준:

- 고정 rating fixture에서 강한 플레이어가 한 팀에 몰리지 않는다.
- 기존 TEST/NORMAL 최소 인원 규칙이 깨지지 않는다.

### 3단계: 승패 기반 rating 업데이트

- `ProgressionService.applyMatchResult(...)` 직후 rating update 실행.
- `MatchProgressionReward`와 별도 `MatchRatingChange`를 UI에 전달한다.
- 경기 결과 dialog에 ELO 변화량을 표시한다.

완료 기준:

- 승리팀 ELO는 평균적으로 상승하고 패배팀 ELO는 하락한다.
- 신규 플레이어의 변화량이 기존 플레이어보다 크다.
- admin reset/create 흐름에서 rating이 중복 갱신되지 않는다.

### 4단계: 탈락 순위 기록

- `MatchResult`에 팀별 placement를 추가한다.
- 동시 탈락은 같은 placement 또는 deterministic tie-break 중 하나를 config로 선택한다.
- rating update가 1위/2위/3위/4위 차이를 반영한다.

완료 기준:

- 5팀 경기에서 먼저 탈락한 팀이 늦게 탈락한 팀보다 더 큰 하락을 받는다.

### 5단계: 운영 도구

- `/semiontd rating set <player> <elo>` 관리자 명령.
- `/semiontd rating reset <player>` 관리자 명령.
- leaderboard command.
- rating update dry-run debug command.

완료 기준:

- 잘못된 rating을 운영 중 복구할 수 있다.
- 매치 시작 전 팀 rating 요약을 status에서 볼 수 있다.

## GameTest 계획

- 기본 rating profile 생성/저장/로드.
- display ELO 계산.
- placement player의 높은 sigma 유지.
- 승리/패배 rating 업데이트 방향 검증.
- 동일 `MatchResult`가 두 번 적용되지 않도록 방지.
- 5팀 fixture에서 팀 rating spread 최소화.
- 25명 초과 ready 후보에서 spectator 우선권 회전.
- `SemionGame.markReady(...)`가 25명 초과 ready roster를 거부하지 않는지 검증.
- config off일 때 기존 random/priority selection 동작 유지.
- `/semiontd profile` 문구에 ELO가 포함되는지 검증.

검증 명령:

```text
rtk ./gradlew compileJava compileGametestJava --console=plain
rtk ./gradlew runGameTest --console=plain
```

## 운영 지표

초기 배포 후 다음 지표를 로그로 남긴다.

| 지표 | 목적 |
|---|---|
| teamMuSpread | 시작 전 팀 실력 차이 |
| teamConservativeSpread | 불확실성 반영 후 팀 실력 차이 |
| expectedWinProbabilitySpread | 특정 팀이 과도하게 유리한지 확인 |
| actualWinnerExpectedRank | 예상 강팀이 계속 이기는지 확인 |
| averageQueueWaitMatches | 25명 초과 시 대기 회전 공정성 |
| placementCountByTeam | 신규 유저가 한 팀에 몰리는지 확인 |

목표:

- 팀별 expected win probability가 5팀 기준 `20%` 근처에 모이게 한다.
- active 25명 초과 상황에서 같은 플레이어가 연속 spectator가 되는 일을 줄인다.
- rating 업데이트가 한 경기로 과도하게 튀지 않게 한다.

## 리스크와 방어책

| 리스크 | 방어책 |
|---|---|
| 개인 스탯 보정이 메타를 왜곡함 | Phase 1에서는 승패만 사용한다. |
| 신규 플레이어가 팀 밸런스를 흔듦 | 높은 sigma와 placement 분산 비용을 적용한다. |
| 친구끼리 같은 팀 고정 시 rating 왜곡 | party/squad 보정은 별도 phase로 둔다. |
| admin end/reset이 rating을 잘못 갱신함 | rated match 종료 조건과 match idempotency key를 둔다. |
| 25명 초과에서 spectator 불만 | 다음 매치 우선권과 spectator 연속 방지 카운터를 저장한다. |
| TrueSkill2 전체 구현 복잡도 | 1차는 팀 승패 기반 TrueSkill 계열로 작게 시작하고, 로그를 쌓은 뒤 보조 factor를 추가한다. |

## 결정 필요 사항

- 표시 이름을 `ELO`로 할지 `MMR`로 할지.
- 배치 경기 수를 5/10/15 중 어디로 둘지.
- admin `/semiontd end`를 rated match로 볼지, 운영 종료로 볼지.
- 탈락 순위를 rating에 반영할 때 동시 탈락을 공동 순위로 볼지.
- 직업별 rating을 장기적으로 분리할지.

## `$codex-autoresearch` 실행 후보

나중에 자동 구현 루프를 돌릴 경우 launch-ready 초안은 다음과 같다.

```text
$codex-autoresearch
Goal: TrueSkill2 계열 rating profile 저장, 표시용 ELO 계산, 승패 기반 rating update, rating 기반 팀 편성 GameTest를 추가한다.
Scope: src/main/java/kim/biryeong/semiontd/game src/main/java/kim/biryeong/semiontd/progression src/main/java/kim/biryeong/semiontd/config src/main/java/kim/biryeong/semiontd/command src/gametest/java/kim/biryeong/semiontd/gametest
Metric: GameTest failure count
Direction: lower
Verify: rtk ./gradlew runGameTest --console=plain
Guard: rtk ./gradlew compileJava compileGametestJava --console=plain
Iterations: unlimited
Stop condition: all required GameTests pass and the new rating/matchmaking tests cover storage, display ELO, team balancing, and match-result update direction
```

## 참고 자료

- Microsoft Research, TrueSkill Ranking System publications: https://www.microsoft.com/en-us/research/project/trueskill-ranking-system/publications/
- Microsoft Research, TrueSkill 2: An improved Bayesian skill rating system: https://www.microsoft.com/en-us/research/uploads/prod/2018/03/trueskill2.pdf
- Microsoft Research, TrueSkill: A Bayesian Skill Rating System: https://www.microsoft.com/en-us/research/publication/trueskilltm-a-bayesian-skill-rating-system-2/
