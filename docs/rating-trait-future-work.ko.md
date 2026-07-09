# 특성 시스템 도입 시 Rating/ELO 후속 작업 계획

> **상태:** 향후 작업 제안 문서
> **대상:** SemionTD ELO v1 / Rating Beta 이후 trait-aware rating 개선
> **작성일:** 2026-06-02
> **관련 문서:** `docs/rating-policy.md`

## 배경

현재 Rating/ELO 시스템은 match placement를 1차 신호로 사용하고, 개인 contribution 지표를 제한된 multiplier로 반영한다.

현재 정책의 핵심은 다음과 같다.

```text
rating delta = placement 기반 base delta × contribution multiplier
contribution multiplier = bounded auxiliary signal
placement outcome = primary signal
```

이 구조는 현재 단계에서는 안전하다. Contribution weighting이 승패/순위 결과의 부호를 뒤집지 않기 때문에, 개인 지표가 완벽하지 않아도 rating이 과도하게 왜곡될 가능성이 낮다.

하지만 향후 게임 시작 전 플레이어가 특성을 선택할 수 있게 되고, 그 특성에 수입/방어/인컴 압박/경제 관련 증감 효과가 포함되면 현재 contribution 계산을 그대로 유지하면 안 된다.

예를 들어 다음과 같은 특성이 생길 수 있다.

```text
수입 +20%
방어 효율 +15%
income unit 압박 +10%
킬 보상 +10%
팀원 경제 보조 +5%
```

이 경우 `incomeGenerated`, `sentIncomeThreat`, `ownLaneLeakedThreat`, `killMinerals`, `finalIncome` 같은 수치가 플레이어 실력뿐 아니라 특성 효과에 의해 직접 변한다.

따라서 특성 시스템 도입 시에는 ELO 시스템 전체를 교체하기보다는, 현재 rating 구조를 유지하면서 contribution/telemetry/policy versioning을 trait-aware하게 확장해야 한다.

## 결론

특성 시스템이 추가되어도 ELO 시스템 전체를 갈아엎을 필요는 없다.

유지 가능한 부분:

```text
- MatchId
- rating profile
- rating event
- applied marker
- placement-based ELO
- 2~5팀 placement target score 정책
- leaderboard command
- persistence/idempotency 구조
```

수정해야 하는 부분:

```text
- match result/rating event에 trait snapshot 저장
- contribution 계산에서 trait modifier 보정
- rating formula/policy version bump
- 특성 출시 초기 contribution 영향 축소 또는 비활성화 옵션
- trait별 live telemetry와 운영 리포트
```

권장 방향:

```text
rating은 placement를 primary signal로 유지한다.
contribution은 optional, bounded, versioned, trait-aware auxiliary signal로 취급한다.
특성 정보는 match result/rating event에 당시 기준 snapshot으로 저장한다.
```

## Rating이 측정할 대상

특성 도입 전에 먼저 rating이 무엇을 측정할지 정책을 정해야 한다.

### 선택지 A: 특성 선택을 포함한 종합 실력

이 관점에서는 rating이 다음 능력을 모두 포함한다.

```text
- 순위/승률
- 특성 선택 능력
- 메타 이해도
- 방어 운영
- 인컴 압박
- 경제 최적화
- 팀 기여
```

장점:

```text
- 플레이어가 이해하기 쉽다.
- ladder 운영이 단순하다.
- 최종 순위가 가장 중요한 신호로 유지된다.
- 현재 ELO 구조와 잘 맞는다.
```

단점:

```text
- OP 특성을 빠르게 찾은 플레이어가 rating 이득을 볼 수 있다.
- 특성 밸런스 패치 전후 rating 의미가 흔들릴 수 있다.
- 특정 메타가 rating을 지배할 수 있다.
```

### 선택지 B: 특성 영향을 제거한 순수 플레이 실력

이 관점에서는 rating이 특성 효과를 최대한 제거한 개인 기량을 측정해야 한다.

장점:

```text
- 플레이어 순수 실력 비교에 가깝다.
- 특성 밸런스 문제와 rating 왜곡을 분리할 수 있다.
```

단점:

```text
- 구현 난도가 높다.
- player rating 외에 trait strength, map/lane difficulty, team composition 보정이 필요해진다.
- 단순 ELO보다는 TrueSkill/회귀 모델에 가까운 별도 모델이 필요할 수 있다.
```

### 권장 정책

초기에는 선택지 A를 채택한다.

```text
SemionTD rating은 특성 선택과 운영을 포함한 종합 승률 기여도를 측정한다.
```

단, contribution weighting은 특성 효과를 그대로 실력으로 오인하지 않도록 보정한다.

## Phase 1: 특성 출시 안전 모드

특성 시스템을 처음 출시할 때는 rating을 보수적으로 운영한다.

### 목표

```text
- placement-based ELO는 유지한다.
- contribution weighting의 영향은 줄이거나 일시적으로 끈다.
- 모든 match에 trait snapshot을 저장한다.
- 실제 데이터를 수집한 뒤 trait-aware contribution을 켠다.
```

### 권장 설정

옵션 A: contribution 비활성화

```text
rating.contribution.enabled = false
```

옵션 B: contribution clamp 축소

```text
기존: 0.85..1.15
임시: 0.90..1.10
```

옵션 C: 특성 미보정 지표만 약하게 반영

```text
placement delta = 그대로 사용
contribution multiplier = neutral에 가깝게 제한
```

### Acceptance criteria

```text
- 특성 선택 여부와 관계없이 match placement 기반 rating은 정상 적용된다.
- trait snapshot이 모든 rating-eligible participant에 저장된다.
- 특성 출시 후에도 1등/상위권이 과도하게 감점되는 비율이 낮다.
- contribution을 끄거나 clamp를 줄여도 기존 Rating/GameTest가 통과한다.
```

## Phase 2: Trait snapshot 저장

특성 효과는 시간이 지나면 밸런스 패치로 바뀔 수 있다. 따라서 단순히 trait id만 저장하면 추후에 과거 match를 재해석하기 어렵다.

### 저장해야 할 정보

각 participant마다 다음 정보를 match result 또는 rating event에 저장한다.

```json
{
  "playerId": "...",
  "traitLoadoutId": "income-rusher-v1",
  "traitVersion": 3,
  "selectedTraits": [
    {
      "id": "income_plus_20",
      "version": 2,
      "modifiers": {
        "incomeMultiplier": 1.20,
        "pressureMultiplier": 1.00,
        "defenseMultiplier": 0.95,
        "economyMultiplier": 1.10
      }
    }
  ]
}
```

최소 필드:

```text
- playerId
- selected trait ids
- trait version
- 당시 effective modifiers snapshot
```

권장 필드:

```text
- trait loadout id/name
- trait category
- explicit modifier map
- rating formula version
- game balance version
```

### 주의점

```text
- 현재 trait config를 추후에 다시 읽어서 과거 match를 해석하면 안 된다.
- 과거 match는 당시 적용된 modifier snapshot으로 replay 가능해야 한다.
- rating event에는 계산에 사용한 formula version도 함께 남겨야 한다.
```

## Phase 3: Trait-aware contribution normalization

특성으로 인해 raw stat이 증가했다면 contribution 계산에서는 raw stat을 그대로 비교하면 안 된다.

### 예시: 수입 증가 특성

수입 +20% 특성이 있는 플레이어의 raw income을 그대로 쓰면 economy contribution이 과대평가될 수 있다.

보정 예시:

```text
normalizedIncome = rawIncome / traitIncomeMultiplier
normalizedFinalIncome = rawFinalIncome / traitIncomeMultiplier
```

### 예시: 인컴 압박 증가 특성

income unit 강화나 압박 증가 특성이 있으면 `sentIncomeThreat`, `incomeAttackSuccessThreat`도 보정 대상이다.

```text
normalizedSentThreat = rawSentThreat / traitPressureMultiplier
normalizedAttackSuccessThreat = rawAttackSuccessThreat / traitPressureMultiplier
```

### 예시: 방어 강화 특성

방어 강화 특성이 있으면 leakage/kill/own-lane hold 지표를 직접 비교하기 어렵다.

가능한 접근:

```text
normalizedDefenseThreat = rawDefenseThreat / traitDefenseMultiplier
normalizedLeakedThreat = rawLeakedThreat / opponentPressureMultiplier
```

방어 지표는 단순 나눗셈만으로 정확히 보정하기 어려울 수 있으므로, 초기에는 방어 contribution의 clamp를 더 작게 두는 것이 안전하다.

### 예시: 팀 버프 특성

팀원에게 수입/방어/압박 보너스를 주는 특성은 개인 contribution 귀속이 어렵다.

초기 정책:

```text
- 팀 버프 특성은 individual contribution에서 직접 가점하지 않는다.
- match result에는 source attribution만 저장한다.
- 충분한 데이터가 쌓인 후 team-level contribution으로 별도 분석한다.
```

## Phase 4: Rating policy versioning

특성 보정이 들어가면 rating 공식이 달라진다. 같은 `ELO v1`로 계속 운영하면 전후 데이터를 해석하기 어렵다.

### 권장 version 체계

```text
ratingSystemId = ELO
ratingFormulaVersion = 1: placement + basic contribution
ratingFormulaVersion = 2: placement + trait-aware normalized contribution
```

또는 시즌 단위로 구분한다.

```text
season 0: ELO beta without traits
season 1 preseason: traits enabled, contribution safe mode
season 1 official: trait-aware contribution enabled
```

### Migration 정책

초기 권장:

```text
- 기존 rating profile은 유지한다.
- 공식 시즌 시작 시 soft reset 또는 provisional placement를 검토한다.
- rating event에는 formula version을 저장한다.
- 다른 formula version의 event를 replay할 때는 해당 version calculator를 사용한다.
```

## Phase 5: Live telemetry와 운영 리포트

특성 도입 후에는 평균 ELO 변화뿐 아니라 특성별 지표를 봐야 한다.

### 수집할 지표

```text
- trait별 pick rate
- trait별 win rate
- trait별 평균 placement
- trait별 평균 rating delta
- trait별 contribution multiplier 분포
- 1등 감점률
- 2등 감점률
- 꼴등 가점률
- team count별 평균 delta
- 신규 플레이어 첫 10게임 delta 분포
- 특성 조합별 outlier
```

### 위험 신호

```text
- 특정 수입 특성이 pick rate와 win rate를 동시에 과점한다.
- 특정 특성을 쓰는 플레이어의 contribution multiplier가 항상 상한에 붙는다.
- 2등 감점률이 급증한다.
- 하위권인데 contribution 덕분에 손실이 지나치게 줄어든다.
- 방어형/지원형 특성이 contribution에서 계속 손해를 본다.
```

### 운영 판단 기준 예시

```text
2등 감점률 <= 5%
1등 감점률 ~= 0%
trait별 평균 delta 편차가 과도하지 않을 것
contribution multiplier p90이 상한에 과도하게 몰리지 않을 것
특정 trait pick rate가 지나치게 높으면 balance review 필요
```

## Phase 6: Admin/repair/replay 도구

특성 도입 후에는 rating event를 재계산해야 할 일이 생길 수 있다.

필요한 운영 도구:

```text
- 특정 match rating 재적용
- 특정 match rating 무효화
- 특정 player profile 재계산
- rating event replay
- rating backup/export
- formula version별 replay
- skipped match reason 조회
```

특히 trait balancing이나 formula 버그가 생겼을 때는 replay 도구가 없으면 수동 복구가 어렵다.

## 구현 작업 목록

### Task 1: Rating event에 formula version 명시

**목표:** rating event가 어떤 계산식으로 생성됐는지 저장한다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/rating/RatingMatchResult.java
src/main/java/kim/biryeong/semiontd/rating/EloRatingCalculator.java
src/test/java/kim/biryeong/semiontd/rating/*Test.java
```

**검증:**

```bash
./gradlew test --tests '*Rating*' --no-daemon
```

### Task 2: Match participant trait snapshot 모델 추가

**목표:** match 종료 시 participant별 선택 특성과 effective modifier snapshot을 저장할 수 있는 모델을 만든다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/game/MatchParticipantResult.java
src/main/java/kim/biryeong/semiontd/rating/RatingParticipant.java
src/test/java/kim/biryeong/semiontd/game/*Test.java
```

**검증:**

```bash
./gradlew test --tests '*Rating*' --tests '*Persistence*' --no-daemon
```

### Task 3: Trait snapshot persistence round-trip 테스트 추가

**목표:** FILE/SQLITE 저장소가 trait snapshot을 손실 없이 저장/로드하는지 검증한다.

**예상 파일:**

```text
src/test/java/kim/biryeong/semiontd/persistence/RatingPersistenceTest.java
src/test/java/kim/biryeong/semiontd/persistence/PersistenceFallbackTest.java
```

**검증:**

```bash
./gradlew test --tests '*Persistence*' --no-daemon
```

### Task 4: Contribution normalization seam 추가

**목표:** raw stat을 직접 비교하지 않고, trait modifier가 적용된 normalized stat을 계산하는 seam을 만든다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/rating/RatingContributionCalculator.java
src/main/java/kim/biryeong/semiontd/rating/RatingContributionBreakdown.java
src/test/java/kim/biryeong/semiontd/rating/RatingContributionCalculatorTest.java
```

**검증:**

```bash
./gradlew test --tests '*RatingContribution*' --no-daemon
```

### Task 5: 특성 출시 안전 모드 config 추가

**목표:** 특성 도입 직후 contribution을 끄거나 clamp를 줄일 수 있게 한다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/rating/RatingConfig.java
src/main/java/kim/biryeong/semiontd/config/SemionConfigLoader.java
src/test/java/kim/biryeong/semiontd/config/*Test.java
```

**검증:**

```bash
./gradlew test --tests '*Rating*' --tests '*Config*' --no-daemon
```

### Task 6: Trait telemetry report 추가

**목표:** 운영자가 trait별 pick/win/delta/contribution 분포를 확인할 수 있게 한다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/rating/RatingTelemetryReport.java
src/main/java/kim/biryeong/semiontd/command/SemionCommands.java
src/test/java/kim/biryeong/semiontd/rating/*Telemetry*Test.java
```

**검증:**

```bash
./gradlew test --tests '*Rating*' compileGametestJava --no-daemon
```

### Task 7: Admin replay/repair 설계 및 최소 구현

**목표:** formula/trait 버그 발생 시 rating을 복구할 수 있는 기반을 만든다.

**예상 파일:**

```text
src/main/java/kim/biryeong/semiontd/rating/RatingReplayService.java
src/main/java/kim/biryeong/semiontd/command/SemionCommands.java
src/test/java/kim/biryeong/semiontd/rating/RatingReplayServiceTest.java
```

**검증:**

```bash
./gradlew test --tests '*RatingReplay*' --no-daemon
```

## 출시 권장 순서

```text
1. 현재 ELO v1은 Rating Beta/Preseason으로 공개한다.
2. 특성 기능 출시 전 trait snapshot 저장 모델을 먼저 넣는다.
3. 특성 출시 초기에는 contribution을 끄거나 clamp를 줄인다.
4. 실제 match 데이터를 1~2주 수집한다.
5. trait-aware normalization을 적용한다.
6. rating formula version을 올린다.
7. 공식 시즌 시작 전 replay/repair 도구와 telemetry report를 준비한다.
```

## Non-goals

이 문서는 다음 작업을 즉시 요구하지 않는다.

```text
- 현재 ELO 시스템 전체 교체
- TrueSkill2 즉시 도입
- 글로벌 cross-server ladder
- 복잡한 trait strength 회귀 모델
- 시즌 리셋 즉시 구현
```

이 문서의 목적은 특성 시스템 도입 시 현재 Rating/ELO 구조를 안전하게 확장하기 위한 작업 방향을 남기는 것이다.

## 최종 권장안

특성 시스템이 들어와도 rating core는 유지한다.

```text
placement-based ELO = 유지
contribution weighting = trait-aware하게 보정
rating event = formula/trait snapshot 저장
운영 = beta → telemetry → version bump → official season 순서
```

특히 수입 증감 특성이 들어오는 경우에는 `incomeGenerated`, `finalIncome`, `sentIncomeThreat`, `incomeAttackSuccessThreat`를 raw 값 그대로 contribution에 쓰지 말고, 당시 trait modifier snapshot 기준으로 normalized value를 계산해야 한다.

가장 안전한 초기 운영 정책은 다음이다.

```text
특성 출시 직후에는 placement-only 또는 reduced-contribution ELO로 운영한다.
충분한 실측 데이터가 쌓인 뒤 trait-aware contribution을 켠다.
```
