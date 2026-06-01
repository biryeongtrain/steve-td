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

Microsoft Research의 TrueSkill 문서는 Elo의 일반화로서 불확실성 추적, 무승부 모델링, 다인/팀 경기에서 개인 실력 추론을 지원한다고 설명한다. TrueSkill은 경기 결과를 "참가 팀/플레이어의 상대 순위"로 보고, 팀 경기는 팀원의 skill 합을 기준으로 업데이트한다. TrueSkill2 문서는 온라인 멀티플레이어 매치메이킹에서 과거 경기 결과로 다음 경기 승리 능력을 추정하고, player experience, squad membership, kills, quit tendency, 다른 mode의 skill 같은 보조 정보를 추가해 Halo 5 과거 매치 예측에서 TrueSkill보다 높은 정확도를 보였다고 보고한다.

Semion TD에는 다음 특성이 있으므로 단순 Elo보다 TrueSkill 계열이 맞다.

- 1:1이 아니라 최대 5개 팀, 팀당 1-5명이다.
- 같은 팀 안에서도 개인 lane 성과가 갈린다.
- 신규 플레이어, 파티, 관전자 회전 같은 운영 변수가 있다.
- 승패만 저장하면 너무 거칠고, 라운드/소환/처치/수입 같은 보조 지표를 나중에 rating 보정에 쓸 수 있다.

타당성 요약:

- 다팀 결과를 Winner Takes All로만 처리하는 것은 2위와 최하위의 정보 차이를 버린다. TrueSkill 계열은 다팀 상대 순위를 입력으로 삼을 수 있으므로 Semion TD도 팀별 placement를 기록하는 쪽이 더 타당하다.
- 다만 플레이어가 이해하는 UI/정산 문구는 "승리 그룹/패배 그룹"으로 단순화할 수 있다. 내부 rating은 placement 가중치를 유지해 1등이 2등보다 더 얻고, 최하위가 중위권보다 더 잃게 한다.
- 개인 성과 보정은 TrueSkill2 방향과 맞지만, 기본 TrueSkill은 팀 결과와 팀 구성만 처리한다. 따라서 lane leak 보정은 Phase 1 모델의 일부가 아니라 로그를 쌓은 뒤 제한적으로 켜는 TrueSkill2-style factor로 봐야 한다.
- lane leak은 원시 누수 수가 아니라 난이도 보정 점수로 써야 한다. 같은 누수라도 라운드 기본 wave, 해당 lane에 들어온 견제 유닛, 유닛 tier/cost/role/ability, spawn timing에 따라 책임도가 달라진다.

## 설계 원칙

- Phase 1은 승패 기반 팀 rating만 구현한다. 개인 스탯 보정은 데이터가 쌓인 뒤 켠다.
- 표시용 ELO는 내부 매칭 점수와 분리한다.
- 신규 플레이어는 낮은 확신도로 취급해서 팀 배정에는 보수적으로 섞고, ELO 변동은 크게 둔다.
- 팀 결과가 rating의 주 신호다. 개인 스탯은 보조 신호로만 사용한다.
- 다팀 경기에서는 Winner Takes All을 피하고, 상위 절반은 승리 그룹, 하위 절반은 패배 그룹으로 본다. 단, 내부 rating 변화량은 placement에 따라 차등 적용한다.
- 파티/친구 고정 팀이 생기면 팀 시너지를 별도 보정값으로 둔다.
- 수치 파라미터는 config로 둬서 운영 중 튜닝 가능하게 한다.

## 현재 코드 접점

| 영역 | 현재 역할 | 변경 방향 |
|---|---|---|
| `ParticipantSelectionService` | ready player를 섞고 팀 수/팀 크기를 결정한다. | rating 기반 후보 생성과 팀 품질 평가를 추가한다. |
| `ParticipantSelectionPlan` | active/spectator/팀 배정 결과를 담는다. | 매치 품질, 평균 팀 rating, spectator 선정 이유를 선택적으로 담는다. |
| `SemionGame.matchResult()` | 승리 팀, 참가자, 최종 라운드, 개인 스탯을 만든다. | elimination order/팀별 생존 라운드와 lane round stats를 추가하면 rating 품질이 좋아진다. |
| `ProgressionService.applyMatchResult()` | 승패/재화 프로필을 저장한다. | rating 업데이트를 같은 transaction 흐름에서 실행한다. |
| `SemionPlayerProfile` | 플레이 수, 승패, 재화, 선택 직업을 저장한다. | 별도 rating record를 추가하거나 profile 안에 rating 필드를 확장한다. |
| Match telemetry | 현재 별도 구조가 없다. | rating 반영 전 개인 skill 검증용 append-only 로그를 먼저 추가한다. |
| `/semiontd profile` | 현재 프로필을 보여준다. | ELO, 티어, placement 상태, 최근 변동량을 보여준다. |

## Rating 구현 전 준비 상태

이 문서의 rating math는 아직 구현하지 않는다. 먼저 경기 결과와 저장 경계를 다음처럼 준비한다.

- `MatchResult`는 `matchId`, `startedAtEpochMillis`, `endedAtEpochMillis`를 가진다. `matchId`는 roster lock 시점에 생성하고, 같은 종료 경기에서 `matchResult()`를 여러 번 호출해도 변하지 않아야 한다.
- 기존 `winningTeams`와 `MatchParticipantResult.winner()`는 UI/progression 호환성을 위해 유지한다.
- `TeamMatchResult`는 team별 `placement`, `resultGroup`, `placementWeight`, `eliminatedRound`, `eliminatedTick`, `bossDamageTaken`을 담는다.
- 5팀 경기는 최종 생존 팀이 1위이고, 늦게 탈락한 팀일수록 더 높은 placement를 받는다.
- progression/rating/telemetry는 같은 `matchId`를 idempotency key로 공유하되, 적용 여부는 `(matchId, subsystem)`으로 분리한다.

## 코드 기준 타당성 조사

| 제안 | 타당성 | 현재 코드 상태 | 필요한 변경 |
|---|---|---|---|
| 다팀 결과를 승리/패배 그룹으로 나누기 | 높음 | `MatchResult`는 winning team과 participant stats만 가진다. loser 사이 순위는 없다. | `SemionGame`이 team elimination order를 기록하고 `MatchResult`에 `TeamMatchResult`를 추가한다. |
| 1등이 2등보다 더 많은 ELO를 얻기 | 높음 | 현재는 winner boolean이라 차등 상승을 표현할 수 없다. | placement별 `placementWeight`를 rating update에 전달한다. |
| lane leak을 개인 성과로 쓰기 | 중간-높음 | `PlayerMatchStatsSnapshot`에는 kill/summon/income만 있다. lane leak과 boss damage attribution은 없다. | lane별 round telemetry를 추가하고, leak 발생 시 lane/player/round/threat를 기록한다. |
| 견제 유닛이 많은 lane의 페널티 완화 | 높음, 단 로그 필요 | 소환은 target team/lane과 summon type을 알고, wave config도 lane별 entry를 제공한다. | wave threat와 summon pressure를 같은 단위로 환산하는 config 기반 scoring이 필요하다. |
| 개인 보정 조기 적용 | 중간 | 하루 2-10경기 규모에서는 200-500경기 로그를 기다리기 어렵다. | 소표본 전제로 낮은 가중치, 경기당 상한, dry-run 비교, 운영 롤백을 같이 둔다. |

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

Rating 기반 팀 편성을 추가할 때도 uneven roster 자체는 막지 않는다. 기본 정책은 `TeamSizeBalancePolicy.ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION`이다.

- `sum(mu)`는 팀 크기가 같은 경우에만 직접 비교하기 쉽다.
- 5명 팀과 4명 팀을 비교할 때는 총합 실력과 1인 평균 실력을 함께 본다.
- match quality에는 팀 크기 차이에 대한 penalty 또는 normalized per-player score를 포함한다.
- rated match에서 완전 동일 팀 크기를 강제하는 정책은 `PREFER_EQUAL_SIZE_FOR_RATED_MATCHES`로 별도 config화할 수 있지만, 초기값으로 쓰지 않는다.

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

Prework 이후 `MatchResult`는 loser 사이의 순서를 `teamResults`로 전달할 수 있다. 다만 실제 rating math를 넣기 전까지 기존 progression/UI는 계속 `winningTeams`와 participant `winner` boolean을 기준으로 동작한다.

### Phase 2: elimination order 추가

팀별 탈락 라운드/탈락 tick을 저장하면 더 좋은 신호가 된다.

```text
TeamMatchResult
  teamId
  placement
  resultGroup
  placementWeight
  eliminatedRound
  eliminatedTick
  bossDamageTaken
```

이후 rating 업데이트는 Winner Takes All이 아니라 다음 정책을 사용한다.

- active team 수가 `N`이면 상위 `floor(N / 2)`팀은 `WIN_GROUP`, 나머지는 `LOSS_GROUP`으로 본다.
- 홀수 팀 경기의 중앙값은 운영 정책에 따라 `WIN_GROUP` 또는 `LOSS_GROUP`으로 둘 수 있다. 초안은 5팀 기준 1-2등 승리, 3-5등 패배처럼 중앙값을 패배 그룹에 둔다.
- 그룹은 플레이어에게 보여줄 정산 문구와 큰 방향을 정한다.
- 내부 rating 변화량은 placement 순서를 유지한다. 예를 들어 1등은 2등보다 더 많이 얻고, 5등은 3등보다 더 많이 잃는다.
- 동시 탈락은 같은 placement로 처리하거나, `eliminatedTick`, `bossDamageTaken`, 남은 lane 수 같은 tie-break를 config로 선택한다.

5팀 예시:

```text
1위: 최종 생존 팀, WIN_GROUP, 가장 큰 상승
2위: 가장 늦게 탈락한 팀, WIN_GROUP, 작은 상승
3위: 그 이전 탈락 팀, LOSS_GROUP, 작은 하락
4위: 그 이전 탈락 팀, LOSS_GROUP, 중간 하락
5위: 가장 먼저 탈락한 팀, LOSS_GROUP, 가장 큰 하락
```

2팀 경기에서는 기존처럼 1등 승리, 2등 패배로 동작한다.

### Phase 3: 개인 성과 보조 신호

TrueSkill2는 경기 결과 외의 보조 지표를 모델에 넣을 수 있지만, 처음부터 개인 지표를 강하게 넣으면 TD 메타를 왜곡할 수 있다.

Semion TD에서 후보 지표:

| 지표 | 용도 | 주의점 |
|---|---|---|
| `finalIncome` | 경제 성장 능력 | 팀 전략상 한 명이 희생할 수 있다. |
| `summonedMonsters` | 견제 기여 | 유닛 비용/타이밍을 같이 봐야 한다. |
| `monsterKills` | 수비 기여 | lane 난이도와 팀 지원 영향을 받는다. |
| `killMinerals` | 처치 가치 | 막타 편향이 있다. |
| `laneLeakThreat` | 방어 실패 비용 | 단순 누수 수가 아니라 boss damage/유닛 위협도로 봐야 한다. |
| `incomingPressure` | 받은 견제 난이도 | 라운드 기본 wave와 적 소환 유닛을 분리해서 기록해야 한다. |
| `pressureAdjustedLeakScore` | 난이도 대비 방어 성과 | 표본이 적으면 운과 target random 영향을 크게 받는다. |
| finalRound | 경기 길이 보정 | 긴 경기와 실력 차이를 단순 연결하면 안 된다. |

권장 방향:

- 개인 스탯은 rating 변경량의 10-20% 이내 보정으로만 둔다.
- 승리한 팀의 낮은 스탯 플레이어도 승리 rating은 받는다.
- 패배한 팀의 높은 스탯 플레이어도 패배 rating 하락을 완전히 피하지는 못한다.
- 라인을 놓친 원시 횟수는 직접 쓰지 않는다. 각 lane이 받은 wave/견제 난이도 대비 누수 비용을 계산한 보정 지표만 사용한다.
- 평균보다 어려운 lane을 받은 플레이어는 누수에 따른 ELO 하락이 완화되어야 한다.
- 평균보다 쉬운 lane에서 큰 누수가 난 플레이어는 개인 성과 보정에서 더 불리할 수 있다.
- 하루 2-10경기 규모에서는 200-500경기 로그를 기다리는 방식이 현실적이지 않다. 따라서 개인 성과 보정은 소표본 불안정성을 인정하고, 제한적 가중치로 조기 적용할 수 있게 설계한다.
- 첫 적용은 개인 보정 share를 rating 변경량의 5-10%로 시작하고, 안정성이 확인되면 10-20%까지 올린다.
- 한 경기에서 개인 보정이 만들 수 있는 display ELO 차이는 절대값 상한을 둔다. 초안은 `maxPersonalDeltaElo=3-5`다.
- 개인 보정은 팀 결과 방향을 뒤집지 않는다. 승리 그룹 player가 개인 보정 때문에 최종 하락하거나, 패배 그룹 player가 최종 상승하는 일은 기본 off로 둔다.
- 운영자는 config로 개인 보정을 즉시 끄거나, 최근 N경기 rating delta를 재계산/복구할 수 있어야 한다.

난이도 보정 초안:

```text
baseWaveThreat = roundWaveThreat(round, laneKey)
summonPressure = sum(threat(summonType, scalingRound, spawnTick, senderTeam))
incomingPressure = baseWaveThreat + summonPressure
laneLeakThreat = sum(leakedMonsterThreat or bossDamageTakenByLeak)
pressureAdjustedLeakScore = laneLeakThreat / max(1.0, expectedLeakThreat(incomingPressure, round))
```

`threat(...)`는 유닛의 gas cost, tier, maxHealth, attackDamage, role, ability activation, round scaling을 조합해 시작한다. 처음에는 완벽한 모델보다 일관된 로그가 더 중요하므로, config 기반 가중치로 작게 시작하고 실제 경기 로그로 보정한다.

구현상 필요한 추가 기록:

```text
LaneRoundStats
  teamId
  laneId
  playerId
  round
  baseWaveThreat
  summonPressure
  incomingPressure
  leakCount
  laneLeakThreat
  bossDamageTakenByLeak
```

## 개인 성과 로깅 정책

개인 skill 체크가 가능한 지표는 먼저 match telemetry로 쌓는다. 다만 Semion TD는 하루 2-10경기 정도의 소규모 운영을 전제로 하므로, 200-500경기 검증이 끝날 때까지 rating 반영을 전부 미루지는 않는다. 로그는 조기 적용한 개인 보정이 과하게 흔들리는지 감시하고, 필요하면 재계산/롤백하기 위한 원본 데이터다.

기획안 반영 위치:

- 이 섹션은 `Phase 3: 개인 성과 보조 신호` 바로 뒤에 둔다. 이유는 개인 성과 후보 지표가 정리된 직후, 그 지표를 어떤 정책으로 수집할지 정의해야 구현자가 rating 로직에 바로 섞지 않기 때문이다.
- 구현 순서에서는 `탈락 순위 기록` 다음, `lane 난이도와 누수 로그` 이전에 둔다. 먼저 공통 로그 envelope와 저장 정책을 만들고, 그 위에 lane pressure/leak 이벤트를 얹는 순서가 맞다.
- 운영 지표와 GameTest에는 "로그가 남는지", "개인 보정이 상한 안에 머무는지", "config off에서 rating 변경에 반영되지 않는지"를 별도 검증 항목으로 둔다.

원칙:

- append-only로 기록한다. 경기 중 산출된 원본 이벤트와 경기 종료 요약을 나중에 재계산할 수 있어야 한다.
- rating update와 분리 가능한 구조로 둔다. 개인 보정이 켜져 있어도 로그 원본, dry-run 결과, 실제 적용 delta를 따로 저장해야 한다.
- match idempotency key를 공유한다. 같은 경기가 두 번 finalize되어도 telemetry와 rating이 중복 적용되지 않아야 한다.
- 개인 식별은 UUID와 lastKnownName을 같이 기록하되, 분석용 export에서는 이름을 생략할 수 있게 한다.
- 원본 이벤트와 파생 점수를 분리한다. 예를 들어 `summonPressure`는 저장하되, 어떤 formula로 계산했는지 `scoringVersion`을 같이 남긴다.
- 로그 schema version을 둔다. balance/config 변경 후 이전 로그와 새 로그를 섞어 분석할 때 버전을 구분해야 한다.
- 저장 실패는 경기를 깨지 않게 한다. 단, rating dry-run과 운영 리포트에는 "telemetry missing"을 표시한다.

저장 위치 초안:

```text
run/semiontd/match-telemetry/
  2026-05/
    match-<matchId>.jsonl
run/semiontd/match-telemetry-index.json
```

파일 형식은 JSONL을 우선한다. 한 경기의 이벤트가 많아질 수 있고, 경기 중 append가 쉬우며, 나중에 외부 분석 도구로 읽기 좋다.

공통 event envelope:

```text
TelemetryEvent
  schemaVersion
  matchId
  eventId
  eventType
  serverTick
  wallClockEpochMillis
  round
  phase
  teamId
  laneId
  playerId
  payload
```

개인 skill 검증용 event type:

| eventType | 기록 시점 | payload 핵심 필드 | 목적 |
|---|---|---|---|
| `MATCH_STARTED` | roster lock 직후 | activeTeams, participants, teamAverageMu snapshot | 경기 전 rating/팀 상태 보존 |
| `ROUND_STARTED` | 라운드 wave enqueue 직전 | round, baseWaveThreatByLane | 라운드 기본 난이도 기준점 |
| `SUMMON_SENT` | 소환 구매 성공 시 | senderPlayerId, summonId, gasCost, tier, targetTeamId, targetLaneId, scheduledRound | 누가 어떤 압박을 보냈는지 |
| `SUMMON_SPAWNED` | 실제 lane 투입 시 | summonId, scalingRound, targetLaneId, threatScore | 예약 소환과 실제 압박 분리 |
| `MONSTER_KILLED` | 몬스터 사망 시 | killerPlayerId, monsterSource, threatScore, minerals | 방어 기여와 막타 편향 분석 |
| `LANE_LEAKED` | 몬스터가 boss/최종 방어선에 피해를 줄 때 | monsterSource, leakedThreat, bossDamage, incomingPressureAtTick | 누수 책임을 난이도와 함께 기록 |
| `TEAM_ELIMINATED` | 팀 탈락 시 | placementCandidate, eliminatedRound, eliminatedTick, bossDamageTaken | 다팀 placement 산출 |
| `MATCH_ENDED` | MatchResult 생성 시 | finalPlacementByTeam, participantSummary, ratingDryRun | 경기 요약과 dry-run 결과 |

경기 종료 요약:

```text
MatchTelemetrySummary
  matchId
  schemaVersion
  scoringVersion
  startedAtEpochMillis
  endedAtEpochMillis
  finalRound
  teamResults: list<TeamMatchResult>
  playerResults: list<PlayerTelemetrySummary>
  ratingDryRun: optional<MatchRatingDryRun>
```

```text
PlayerTelemetrySummary
  playerId
  teamId
  laneId
  finalIncome
  summonedMonsters
  summonPressureSent
  monsterKills
  killThreat
  laneIncomingPressure
  laneLeakThreat
  pressureAdjustedLeakScore
```

소표본 적용 정책:

- 운영 초기부터 개인 성과 보정을 켤 수 있다. 단, 기본값은 낮은 영향도로 시작한다.
- 첫 30경기 또는 2주 동안은 `personalAdjustmentShare=0.05`와 `maxPersonalDeltaElo=3`을 권장한다.
- 30-100경기 구간에서는 운영자가 리포트를 보고 `personalAdjustmentShare=0.10`까지 올릴 수 있다.
- 100경기 이상 누적 뒤에도 최대 share는 0.20을 넘기지 않는다.
- 항상 dry-run 결과를 같이 저장한다. 실제 적용된 delta와 "개인 보정 off였을 때 delta"를 비교할 수 있어야 한다.
- 개인 보정 후보는 match winner/placement 예측력뿐 아니라 플레이어가 납득 가능한 경기 체감 보정인지 확인한다. 표본이 작으므로 통계적 유의성만 기다리지 않고, 운영 리포트와 반복 사례를 함께 본다.
- 개인 보정은 승패/placement 방향을 뒤집는 신호가 아니라, 최종 rating delta의 제한된 일부로만 작동한다.
- 운영자는 `/semiontd rating dryrun <matchId>` 또는 로그 리포트에서 어떤 이벤트가 어떤 보정으로 이어졌는지 볼 수 있어야 한다.
- 운영자는 `/semiontd rating personal-adjustment off` 같은 config 변경으로 다음 경기부터 개인 보정을 끌 수 있어야 한다.

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
config/semion-td/ratings.json
config/semion-td/match-results.json
config/semion-td/progression-applied-matches.json
```

첫 prework 구현은 file-first, DB-ready 구조다. 기본 backend는 `FILE`이고, config enum에는 `SQLITE`, `MONGODB` 선택지를 미리 둔다. 이 단계에서는 SQLite JDBC나 MongoDB driver dependency를 추가하지 않는다.

Repository port:

```text
AppliedMatchRepository
  hasApplied(matchId, subsystem)
  markApplied(matchId, subsystem, appliedAtEpochMillis)

MatchResultRepository
  saveMatchResult(matchResult)
  findMatchResult(matchId)

RatingRepository
  findProfile(playerId)
  saveProfile(playerId, profile)
```

`AppliedMatchRepository`의 file 구현은 map 형태 JSON을 사용한다.

```json
{
  "match-uuid:progression": 1780290000000,
  "match-uuid:rating": 1780290001000
}
```

중복 적용 정책:

- 같은 `(matchId, "progression")`이 이미 적용되어 있으면 `ProgressionService.applyMatchResult(...)`는 빈 reward map을 반환하고 profile count/currency를 다시 올리지 않는다.
- 같은 `matchId`라도 `"rating"`과 `"telemetry"` subsystem은 독립적으로 mark할 수 있다.
- UI 재시도에서 reward line 재구성이 필요하면 나중에 `MatchResultRepository`와 reward snapshot 저장을 추가한다. 현재 prework는 중복 재적용 방지가 우선이다.

DB-ready schema 방향:

```text
SQLite
  applied_matches(match_id, subsystem, applied_at_epoch_millis), primary key(match_id, subsystem)
  match_results(match_id, started_at_epoch_millis, ended_at_epoch_millis, final_round, raw_json)
  rating_profiles(player_id, last_known_name, games_played, wins, losses, mu, sigma, display_elo, updated_at_epoch_millis)

MongoDB
  applied_matches unique index { matchId: 1, subsystem: 1 }
  match_results unique index { matchId: 1 }
  rating_profiles unique index { playerId: 1 }
```

외부 DB 보안/운영 규칙:

- MongoDB URI는 로그에 원문으로 남기지 않는다. credential, host token, query string secret은 redaction한다.
- `externalDbRequired=true`에서 DB 연결이 실패하면 startup을 명확히 실패시킨다.
- `externalDbRequired=false`에서 DB 연결이 실패하면 file backend fallback 또는 rating write disabled 상태를 명시적으로 기록한다. remote write가 성공한 것처럼 조용히 진행하지 않는다.
- SQLite/MongoDB migration은 별도 PR에서 schema version과 dry-run import 검증을 둔다.

Migration note:

```text
기존 progression profile은 migration command 또는 startup migration이 명시적으로 구현될 때까지 profiles.json에 남긴다.
Rating data는 SemionPlayerProfile을 확장하지 말고 새 repository layer에서 시작한다.
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
- 다팀 결과를 상위 승리 그룹/하위 패배 그룹으로 나눈다.
- 동시 탈락은 같은 placement 또는 deterministic tie-break 중 하나를 config로 선택한다.
- rating update가 1위/2위/3위/4위/5위 차이를 반영한다.

완료 기준:

- 5팀 경기에서 먼저 탈락한 팀이 늦게 탈락한 팀보다 더 큰 하락을 받는다.
- 5팀 경기에서 1-2등은 평균적으로 상승하고 3-5등은 평균적으로 하락한다.
- 1등은 2등보다 더 큰 상승을 받는다.

### 5단계: match telemetry 로깅 정책과 저장소

- `MatchTelemetryEvent`, `MatchTelemetrySummary`, `PlayerTelemetrySummary` data model을 추가한다.
- `run/semiontd/match-telemetry/` 아래에 match id별 JSONL 로그를 append-only로 저장한다.
- event envelope에 `schemaVersion`, `scoringVersion`, `matchId`, `eventId`, `serverTick`, `round`, `phase`를 포함한다.
- rating update와 telemetry write를 같은 match idempotency key로 묶는다.
- 로그 저장 실패는 경기 진행을 막지 않되, 운영 리포트와 dry-run에 누락 상태를 표시한다.
- 개인 성과 보정은 config로 켜고 끌 수 있게 하며, 실제 적용 delta와 dry-run delta를 모두 저장한다.

완료 기준:

- 정상 경기 종료 후 match telemetry JSONL과 summary가 생성된다.
- 같은 match finalize가 재시도되어도 telemetry가 중복 기록되지 않는다.
- telemetry가 꺼져 있어도 기존 progression/rating update 흐름이 깨지지 않는다.
- 개인 보정 off에서는 dry-run 결과가 저장되더라도 실제 ELO delta가 Phase 1-4 정책과 동일하다.
- 개인 보정 on에서는 실제 적용 delta가 configured share와 `maxPersonalDeltaElo` 상한을 넘지 않는다.

### 6단계: lane 난이도와 누수 로그

- `PlayerMatchStatsSnapshot` 또는 별도 match telemetry에 lane round stats를 추가한다.
- 라운드 기본 wave threat와 적 소환 pressure를 분리해서 기록한다.
- lane leak은 누수 수, boss damage, leaked unit threat를 함께 기록한다.
- rating 보정은 조기 적용할 수 있지만, 낮은 기본 가중치와 debug/dry-run 리포트를 항상 같이 둔다.

완료 기준:

- 같은 라운드에서 견제 유닛을 많이 받은 lane의 `incomingPressure`가 더 높다.
- 같은 누수라도 더 높은 `incomingPressure`를 받은 플레이어의 `pressureAdjustedLeakScore`가 낮게 계산된다.
- rating update에서 개인 성과 보정이 config 상한과 `maxPersonalDeltaElo`를 넘지 않는다.

### 7단계: 운영 도구

- `/semiontd rating set <player> <elo>` 관리자 명령.
- `/semiontd rating reset <player>` 관리자 명령.
- leaderboard command.
- rating update dry-run debug command.
- `/semiontd rating dryrun <matchId>`로 match telemetry 기반 보정 후보를 조회한다.
- 개인 성과 보정 share와 on/off 상태를 운영 중 확인하고 바꿀 수 있게 한다.

완료 기준:

- 잘못된 rating을 운영 중 복구할 수 있다.
- 매치 시작 전 팀 rating 요약을 status에서 볼 수 있다.
- 특정 match id의 telemetry summary와 rating dry-run을 운영자가 확인할 수 있다.
- 개인 보정이 문제를 만들면 다음 경기부터 끌 수 있다.

## GameTest 계획

- 기본 rating profile 생성/저장/로드.
- display ELO 계산.
- placement player의 높은 sigma 유지.
- 승리/패배 rating 업데이트 방향 검증.
- 동일 `MatchResult`가 두 번 적용되지 않도록 방지.
- 5팀 fixture에서 팀 rating spread 최소화.
- 5팀 결과에서 1-2등은 상승, 3-5등은 하락하고 1등이 2등보다 더 많이 상승하는지 검증.
- match telemetry가 JSONL로 생성되고 schema/scoring version을 포함하는지 검증.
- 동일 match id finalize 재시도 시 telemetry event가 중복 append되지 않는지 검증.
- personal adjustment off일 때 dry-run이 실제 ELO delta를 바꾸지 않는지 검증.
- personal adjustment on일 때 실제 ELO delta가 share/maxPersonalDeltaElo 상한을 넘지 않는지 검증.
- lane pressure fixture에서 같은 누수라도 높은 `incomingPressure`가 더 낮은 보정 페널티를 만드는지 검증.
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
| laneIncomingPressureP50/P90 | lane별 견제 압박 분포 확인 |
| pressureAdjustedLeakScore | 난이도 대비 누수 성과 확인 |
| personalAdjustmentShare | 개인 보정이 전체 rating 변화량에서 차지하는 비율 |
| telemetryWriteFailureCount | 로그 저장 실패와 분석 누락 감시 |
| ratingDryRunDeltaP95 | 개인 보정 후보가 ELO를 과도하게 흔드는지 확인 |
| personalAdjustmentEnabledMatches | 개인 보정이 켜진 경기 수 |
| personalAdjustmentClampCount | 상한에 걸린 개인 보정 횟수 |

목표:

- 팀별 expected win probability가 5팀 기준 `20%` 근처에 모이게 한다.
- active 25명 초과 상황에서 같은 플레이어가 연속 spectator가 되는 일을 줄인다.
- rating 업데이트가 한 경기로 과도하게 튀지 않게 한다.
- 개인 보정이 잦게 상한에 걸리면 weighting formula를 낮추거나 해당 지표를 비활성화한다.

## 리스크와 방어책

| 리스크 | 방어책 |
|---|---|
| 개인 스탯 보정이 메타를 왜곡함 | 낮은 share와 `maxPersonalDeltaElo`로 조기 적용하고, 문제가 보이면 즉시 off한다. |
| 소표본 때문에 보정이 불안정함 | 30경기/100경기 구간별 share 상한을 두고 dry-run 비교를 계속 저장한다. |
| 검증 전 개인 지표가 rating에 섞임 | telemetry, dry-run, 실제 적용 delta를 분리하고 config off 경로를 검증한다. |
| telemetry 로그가 너무 커짐 | match 단위 JSONL, 월별 디렉터리, retention config를 둔다. |
| 로그 schema 변경으로 분석이 깨짐 | `schemaVersion`과 `scoringVersion`을 기록하고 migration 없이 버전별 분석을 허용한다. |
| lane leak 지표가 불공정하게 작동함 | 원시 누수 수 대신 wave/견제 압박으로 보정하고, 상한/롤백/지표별 off switch를 둔다. |
| 적 팀이 특정 player에게 견제를 몰아 ELO 손실을 줄여주는 악용 | 개인 보정 상한을 낮게 두고, sender/target 분포와 반복 패턴을 로그로 감시한다. |
| 신규 플레이어가 팀 밸런스를 흔듦 | 높은 sigma와 placement 분산 비용을 적용한다. |
| 친구끼리 같은 팀 고정 시 rating 왜곡 | party/squad 보정은 별도 phase로 둔다. |
| admin end/reset이 rating을 잘못 갱신함 | rated match 종료 조건과 match idempotency key를 둔다. |
| 25명 초과에서 spectator 불만 | 다음 매치 우선권과 spectator 연속 방지 카운터를 저장한다. |
| TrueSkill2 전체 구현 복잡도 | 1차는 팀 승패 기반 TrueSkill 계열로 작게 시작하고, 로그를 쌓은 뒤 보조 factor를 추가한다. |

## 결정 필요 사항

- 표시 이름을 `ELO`로 할지 `MMR`로 할지.
- 배치 경기 수를 5/10/15 중 어디로 둘지.
- admin `/semiontd end`를 rated match로 볼지, 운영 종료로 볼지.
- match telemetry 보관 기간과 파일 압축/삭제 정책을 어떻게 둘지.
- 운영 서버에서 telemetry를 기본 on으로 둘지, dry-run 기간에만 on으로 둘지.
- 개인 성과 보정을 첫 배포부터 켤지, 몇 경기 dry-run 후 켤지.
- 초기 `personalAdjustmentShare`를 5%로 둘지 10%로 둘지.
- `maxPersonalDeltaElo`를 3점으로 둘지 5점으로 둘지.
- 5팀 경기의 승리 그룹을 1-2등으로 둘지, 중앙값인 3등까지 포함할지.
- 탈락 순위를 rating에 반영할 때 동시 탈락을 공동 순위로 볼지.
- lane leak threat를 boss damage 기준으로 볼지, leaked unit threat 기준으로 볼지, 둘을 혼합할지.
- 난이도 보정에 적 소환 유닛의 gas cost, tier, ability weight를 어떤 비율로 반영할지.
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
