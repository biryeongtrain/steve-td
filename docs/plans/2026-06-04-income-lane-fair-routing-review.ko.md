# 라인별 인컴 분배 공평화 타당성 검토

**작성일:** 2026-06-04  
**상태:** 구현 진행 중 / `LEAST_THREAT_PRESSURE` MVP 반영  
**대상:** `steve-td` 인컴 유닛 target lane 선택 정책

## 문제 요약

현재 인컴 유닛 라우팅은 다음 구조다.

```text
인컴 유닛을 보낸 플레이어가 summon 실행
-> target team 선택
   -> 팀장 지정 target이 있으면 해당 팀
   -> 아니면 살아 있는 적 팀 중 random
-> target team 안에서 target lane random
-> 해당 lane queue에 인컴 유닛 enqueue
```

코드 기준 주요 touchpoint:

- `src/main/java/kim/biryeong/semiontd/game/SemionGame.java`
  - `summonMonster(...)`
  - `targetTeamForSummon(...)`
  - `randomTargetTeam(...)`
  - `randomTargetLane(...)`
- `src/main/java/kim/biryeong/semiontd/game/PlayerLane.java`
  - `queuedSummonCount()`
  - `pendingNextRoundSummonCount()`
  - `activeMonsters()`
- `src/main/java/kim/biryeong/semiontd/config/LeaderTargetingConfig.java`
  - 팀장 target 지속 라운드/동일 target team cap

현재 기획 문서(`docs/semion-td-plan.md`)도 초기 정책을 다음처럼 명시한다.

```text
choose one target team randomly
choose one active lane in the target team
```

이 정책은 구현이 단순하고 예측 불가능성이 있지만, 실제 경기에서는 특정 lane에 인컴 유닛이 과도하게 몰리는 경우가 생길 수 있다. 그러면 해당 lane 담당자의 생존/킬/방어 지표가 라우팅 운에 크게 흔들리고, 반대로 적게 받은 lane은 실력 판독 신뢰도가 낮아진다.

## 왜 문제가 실제로 자주 생기는가

random lane 선택은 각 인컴 유닛을 독립적으로 lane에 던지는 구조다. 확률상 평균은 맞지만, 짧은 경기 구간에서는 편차가 꽤 크다.

예: target team에 lane 4개가 있고 한 라운드/구간에 인컴 유닛 10개가 들어오는 경우

```text
평균: lane당 2.5개
한 lane이 4개 이상 받을 확률: 약 79.2%
한 lane이 5개 이상 받을 확률: 약 31.1%
한 lane이 6개 이상 받을 확률: 약 7.9%
```

즉 “운 나쁘게 한 lane만 맞는다”가 드문 예외라기보다, 현재 random 정책에서는 흔히 발생하는 편차다. 특히 팀장 target 기능으로 여러 팀이 같은 target team을 지정하면 target team 선택은 의도적으로 집중되므로, 그 안의 lane 선택까지 random이면 특정 개인 lane에 과도한 압력이 걸릴 수 있다.

## 목표

라인별 인컴 분배 공평화의 목표는 “모든 lane이 항상 같은 난이도”가 아니다. TD 게임 특성상 압박 편차와 순간 사고는 필요하다.

목표는 다음에 가깝다.

1. 같은 target team 안에서 한 lane이 연속/과도하게 인컴 유닛을 받는 현상을 줄인다.
2. 팀장 target, 유닛 역할, 자연 wave 난이도 같은 의도된 전략성은 유지한다.
3. ELO/기여도 판정에서 lane별 방어 지표가 라우팅 운보다 실력에 더 가깝게 수렴하게 한다.
4. 정책을 config-driven으로 두어 운영 중 보수적으로 튜닝할 수 있게 한다.

## 검토한 대안

### 대안 A: 완전 라운드로빈

```text
target team의 lane 목록을 순회하면서 1,2,3,4,1,2,3,4... 순서로 배정
```

장점:

- 가장 공평하다.
- 구현과 테스트가 쉽다.
- 짧은 구간에서도 편차가 거의 없다.

단점:

- 너무 예측 가능하다.
- 팀장 target이나 특정 timing rush가 “게임적 압박”이 아니라 기계적 분산으로 느껴질 수 있다.
- 인컴 유닛을 보낸 플레이어가 연속 소환해도 항상 고르게 퍼져 원기옥/압박 전략이 약해질 수 있다.

판단:

- 실력 판독에는 좋지만 게임성이 다소 밋밋해질 위험이 있다.
- debug/admin mode나 tournament strict mode 후보로는 좋다.

### 대안 B: 현재 random 유지 + 사후 ELO 보정

```text
라우팅은 그대로 두고, ELO/기여도 계산에서 lane별 incoming pressure를 보정
```

장점:

- 게임 플레이 감각은 바뀌지 않는다.
- 구현 범위가 rating 쪽에 한정된다.

단점:

- 플레이어 경험상 “내 lane만 너무 맞았다”는 문제는 그대로 남는다.
- 방어 실패/팀 패배 자체를 막지 못한다.
- rating 설명 가능성이 떨어진다. “게임은 불공평했지만 점수는 보정했다”가 된다.

판단:

- 장기적으로 rating 보정은 필요하지만, 현재 문제의 1차 해결책으로는 부족하다.

### 대안 C: least-loaded lane 선택

```text
target team의 lane 중 현재 인컴 부담이 가장 낮은 lane 선택
동률이면 random 또는 round-robin tie-break
```

부담 점수 예시:

```text
lanePressure = queuedThreat
             + pendingNextRoundThreat * nextRoundWeight
             + activeIncomeThreat * activeWeight
             + recentAssignedThreat * recentWeight
```

여기서 중요한 점은 **count가 아니라 value/threat 기준**이어야 한다는 것이다. 20 에메랄드짜리 `chicken` 1마리와 200 에메랄드짜리 `ravager` 1마리를 같은 1개로 취급하면 명시적으로는 평등하지만 실질적으로는 불평등하다.

코드상 `Monster.attributionThreat()`는 현재 다음처럼 계산된다.

```java
Math.max(1.0, maxHealth + Math.max(0.0, attackDamage))
```

예시:

```text
chicken: cost 20, income +1, health 18, attack 1  -> threat 19
ravager: cost 200, income +10, health 260, attack 15 -> threat 275
warden: cost 800, income +40, health 1050, attack 46 -> threat 1096
```

따라서 lane fairness의 단위는 “몇 마리인가”가 아니라 “얼마나 큰 압력인가”여야 한다.

장점:

- 실제 과밀 lane을 직접 피한다.
- 한 lane에 과도하게 몰리는 현상을 강하게 줄인다.
- 인컴 유닛의 비용/수입/전투력을 실질적으로 반영할 수 있다.
- 이미 `Monster.attributionThreat()`가 match stats에 쓰이고 있어 정책 기준을 telemetry와 맞출 수 있다.

단점:

- queue 안 monster의 threat 합계를 노출하는 method가 필요하다.
- active/recent threat까지 넣으면 상태 관리가 늘어난다.
- 너무 강하게 보정하면 플레이어가 “어차피 자동 분산된다”고 느낄 수 있다.

판단:

- MVP로 가장 타당하다.
- 다만 이전의 count 기반 MVP는 폐기한다. 최소 구현도 `queuedSummonThreat + pendingNextRoundSummonThreat` 기준이어야 실질적으로 공평하다.

### 대안 D: weighted random, 낮은 부담 lane일수록 높은 확률

```text
weight = 1 / (1 + lanePressure)
weighted random으로 lane 선택
```

장점:

- random의 예측 불가능성을 유지한다.
- 과밀 lane 확률은 줄어든다.
- 완전 라운드로빈보다 게임적 변동성이 남는다.

단점:

- 여전히 운이 나쁘면 몰림이 가능하다.
- 테스트가 deterministic하지 않으면 회귀 테스트가 어려워진다.
- 운영자가 “왜 이 lane이 선택됐는지” 설명하기 어렵다.

판단:

- 장기적으로는 자연스럽지만, 첫 구현으로는 deterministic least-loaded보다 검증성이 낮다.

### 대안 E: soft cap + overflow 분산

```text
기본은 random
단, 특정 lane의 pressure가 team 평균보다 threshold 이상 높으면 후보에서 제외하거나 weight를 크게 낮춤
```

예시:

```text
averagePressure = teamTotalPressure / activeLaneCount
if lanePressure >= averagePressure + 2:
    lane is overloaded
```

장점:

- 평소에는 기존 random 감각을 유지한다.
- 과도한 몰림만 막는다.
- “운의 변동성”과 “실력 판독 안정성” 사이 절충이 좋다.

단점:

- threshold 튜닝이 필요하다.
- 구현은 대안 C보다 조금 복잡하다.
- 초반처럼 표본이 작을 때 threshold가 민감할 수 있다.

판단:

- 최종 운영 정책으로 가장 좋아 보인다.
- MVP는 C로 만들고, config로 E 모드까지 열 수 있게 확장하는 방식이 안전하다.

## 추천안

### 결론

**라인별 인컴 분배 공평화는 타당하다.**  
현재 pure random lane 선택은 구현은 단순하지만, lane별 실력 판독을 흔들 정도의 편차가 확률적으로 자주 생긴다.

수정된 추천 MVP는 다음이다.

```text
target team 선택은 현재 정책 유지
target lane 선택만 fair routing으로 교체
기본 모드: LEAST_THREAT_PRESSURE
동률 tie-break: round-robin 또는 random
정책/가중치/threshold는 config-driven
```

즉 팀장 target의 전략성은 유지하되, 지정된 target team 안에서 특정 lane만 과도하게 맞는 문제를 줄인다. 단, 여기서 “과도함”은 유닛 수가 아니라 **누적 threat/가치** 기준으로 판단한다.

## 추천 정책 상세

### 1차 구현 정책: least-threat-pressure deterministic routing

새 config 후보:

```json
{
  "enabled": true,
  "mode": "LEAST_THREAT_PRESSURE",
  "queuedThreatWeight": 1.0,
  "nextRoundQueuedThreatWeight": 0.75,
  "tieBreakMode": "ROUND_ROBIN"
}
```

MVP의 최소 pressure는 아래가 되어야 한다.

```text
pressure = queuedSummonThreat + pendingNextRoundSummonThreat * nextRoundQueuedThreatWeight
```

여기서 `queuedSummonThreat`는 queue 안 인컴 유닛들의 `Monster.attributionThreat()` 합이다.

```java
public double queuedSummonThreat() {
    return summonedMonsterSpawnQueue.stream()
            .mapToDouble(Monster::attributionThreat)
            .sum();
}

public double pendingNextRoundSummonThreat() {
    return nextRoundSummonedMonsterSpawnQueue.stream()
            .mapToDouble(Monster::attributionThreat)
            .sum();
}
```

이 정도는 구현 부담이 크지 않으면서도, `chicken` 1마리와 `ravager` 1마리를 같은 부하로 취급하는 문제를 피한다. active/recent threat는 2차로 추가한다.

### 왜 count 기반이 아니라 threat 기반부터 시작해야 하는가

현재 `PlayerLane`에 이미 count method가 있다.

```java
queuedSummonCount()
pendingNextRoundSummonCount()
```

하지만 이것만 쓰면 “명시적 평등”만 달성한다. 예를 들어 lane A에 `chicken` 1마리, lane B에 `ravager` 1마리가 있을 때 둘 다 count는 1이다. 그런데 실제 방어 부담과 rating 입력 품질은 전혀 다르다.

따라서 최소 구현도 아래 method를 추가해서 threat 합계를 기준으로 해야 한다.

```java
queuedSummonThreat()
pendingNextRoundSummonThreat()
```

`Monster.attributionThreat()`가 이미 kill/assist/incoming telemetry에 쓰이므로, 라우팅 기준과 사후 분석 기준을 맞출 수 있다.

### tie-break

동률일 때는 `ROUND_ROBIN`을 추천한다.

이유:

- 테스트가 쉽다.
- 같은 pressure에서 random streak가 다시 생기는 것을 막는다.
- 완전 라운드로빈보다 강하지 않다. pressure가 같을 때만 순회한다.

단, 운영 감각상 너무 기계적이면 `RANDOM` tie-break를 config로 열어두면 된다.

## ELO/실력판독 관점 영향

이 변경은 ELO 자체보다 “입력 데이터 품질”을 개선한다.

현재 rating/progression이 참고하는 match stats에는 다음 계열의 지표가 있다.

```text
ownLaneIncomingThreat
incomingIncomeThreat
ownLaneLeakedThreat
sentIncomeThreat
incomeAttackSuccessThreat
ownLaneDiamondGain
assistClearDiamondGain
assistClearThreat
incomeGenerated
```

라인별 인컴 분배가 너무 랜덤하면 `ownLaneIncomingThreat`, `incomingIncomeThreat`, `ownLaneLeakedThreat`가 플레이어 실력보다 라우팅 운을 더 크게 반영할 수 있다. fair routing은 이 편차를 줄여서 아래 효과를 기대할 수 있다.

- 같은 팀 내 lane 담당자 간 방어 지표 비교가 쉬워진다.
- “인컴 유닛을 많이 받은 lane이라서 터졌다”와 “방어 설계가 약해서 터졌다”를 구분하기 쉬워진다.
- ELO가 배치 기반을 유지하더라도, 향후 trait-aware contribution normalization에 넣을 입력값의 분산이 안정된다.

주의점:

- 라우팅 공평화가 rating 보정을 완전히 대체하지는 않는다.
- 인컴 유닛 종류/티어/역할별 threat가 다르므로 count 기반 pressure는 부적합하다.
- MVP부터 `attributionThreat` 합계 기준으로 가야 “명시적 평등”이 아니라 “실질적 공평성”에 가까워진다.
- 장기적으로는 `attributionThreat`도 완전하지 않다. 유닛 능력, aura/support, 이동속도, 공성 보너스까지 반영한 `routingThreat` 또는 `effectiveThreat`를 별도로 둘 수 있다.

## 구현 범위 제안

현재 MVP 구현은 아래 형태로 진행했다.

- `IncomeLaneRoutingConfig`: `enabled`, `mode`, queued/pending threat weight, tie-break 설정
- `IncomeLaneRoutingPolicy`: routing 로직을 `SemionGame`에서 분리한 순수 정책 모듈
- `PlayerLane.queuedSummonThreat()` / `pendingNextRoundSummonThreat()`: lane별 부담 산출 API
- `SemionConfigLoader`: `income_lane_routing.json` 생성/로드
- `SemionGameManager` / `SemionTd`: config를 game 생성 및 reload 경로에 주입
- `SemionGame`: 기존 target team 선택은 유지하고 target lane 선택만 policy에 위임

### Task 1: Config 추가

파일:

```text
src/main/java/kim/biryeong/semiontd/config/IncomeLaneRoutingConfig.java
src/main/java/kim/biryeong/semiontd/config/SemionConfigLoader.java
src/main/java/kim/biryeong/semiontd/game/SemionGameManager.java
src/main/java/kim/biryeong/semiontd/game/SemionGame.java
```

내용:

- `IncomeLaneRoutingConfig` 추가
- `enabled: true`이면 threat-pressure 라우팅, `enabled: false` 또는 `mode: RANDOM`이면 기존 random 호환 라우팅
- 기본값은 `LEAST_THREAT_PRESSURE`, `ROUND_ROBIN`
- config file 이름: `income_lane_routing.json`
- loader default/backfill test 추가

### Task 2: Lane pressure 계산기 추가

파일 후보:

```text
src/main/java/kim/biryeong/semiontd/game/IncomeLaneRoutingPolicy.java
```

MVP 계산:

```text
pressure = lane.queuedSummonThreat()
         + lane.pendingNextRoundSummonThreat() * config.nextRoundQueuedThreatWeight()
```

향후 확장:

```text
+ active income monster threat
+ recent assigned threat
+ ability/effective threat multiplier
```

명시적으로 금지할 테스트 케이스:

```text
lane A: chicken 1마리 queued
lane B: ravager 1마리 queued
다음 고가 유닛은 lane A로 가야 한다.
```

이 케이스가 실패하면 count 기반으로 회귀한 것이다.

### Task 3: `randomTargetLane(...)` 교체

현재:

```java
private Optional<PlayerLane> randomTargetLane(SemionTeam team) {
    List<PlayerLane> lanes = team.laneGroup().lanes();
    if (lanes.isEmpty()) {
        return Optional.empty();
    }
    return Optional.of(lanes.get(random.nextInt(lanes.size())));
}
```

변경 방향:

```text
selectTargetLane(team)
-> config mode RANDOM이면 기존 동작
-> LEAST_THREAT_PRESSURE면 누적 threat 최저 lane 선택
-> 동률이면 configured tie-break 적용
```

### Task 4: JVM test

파일 후보:

```text
src/test/java/kim/biryeong/semiontd/game/IncomeLaneRoutingPolicyTest.java
src/test/java/kim/biryeong/semiontd/config/SemionConfigLoaderTest.java
```

필수 테스트:

1. `LEAST_THREAT_PRESSURE`는 queued/pending threat가 낮은 lane을 선택한다.
2. `chicken` 1마리 queued lane과 `ravager` 1마리 queued lane을 같은 부하로 취급하지 않는다.
3. pressure가 같은 lane은 round-robin tie-break로 순환한다.
4. `RANDOM` mode는 기존 정책 호환을 유지한다. 단 deterministic random seed를 주입하거나 결과 범위만 검증한다.
5. config loader가 missing config를 default로 생성한다.
6. invalid config는 안전한 기본값으로 clamp된다.

### Task 5: GameTest

파일 후보:

```text
src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
```

검증:

- 같은 target team에 여러 인컴 유닛을 연속 소환했을 때 특정 lane에만 threat가 몰리지 않는다.
- queue threat가 이미 높은 lane은 다음 소환 target에서 밀린다.
- 같은 1마리라도 저가/저위협 유닛이 있는 lane과 고가/고위협 유닛이 있는 lane을 다르게 취급한다.
- 팀장 target이 걸려도 target team은 유지되고 lane만 fair routing된다.

## 운영 튜닝 권장값

초기값 추천:

```text
mode = LEAST_THREAT_PRESSURE
tieBreak = ROUND_ROBIN
queuedThreatWeight = 1.0
nextRoundQueuedThreatWeight = 0.75
activeIncomeThreatWeight = 0.0  // MVP에서는 끔
recentAssignedThreatWeight = 0.0 // MVP에서는 끔
```

실서버에서 그래도 특정 lane 체감 몰림이 남으면 2차로 켠다.

```text
activeIncomeThreatWeight = 0.5
recentAssignedThreatWeight = 1.0
recentWindowTicks = 1200 // 60초
```

`attributionThreat = health + attackDamage`만으로 능력형 유닛의 압력이 과소평가되면 3차로 바꾼다.

```text
pressure unit = attributionThreat -> routingThreat/effectiveThreat
```

`routingThreat` 후보:

```text
routingThreat = attributionThreat
              * tierMultiplier
              * roleMultiplier
              * abilityMultiplier
```

예를 들어 SUPPORT, DISRUPTOR, SIEGE는 단순 체력/공격력보다 실제 라인 압력이 클 수 있으므로 별도 multiplier를 둘 수 있다.

## 리스크와 완화

| 리스크 | 설명 | 완화 |
|---|---|---|
| 압박 전략 약화 | 인컴 유닛을 한 lane에 집중시키는 재미가 줄 수 있음 | target team 지정은 유지하고 lane만 soft/fair 분산 |
| 너무 기계적인 분배 | 플레이어가 순서를 예측할 수 있음 | tie-break를 config로 RANDOM/ROUND_ROBIN 선택 가능하게 함 |
| 구현 복잡도 증가 | active/recent/effective threat까지 한 번에 넣으면 복잡 | MVP는 queued/pending `attributionThreat` 합계까지만 구현 |
| 기존 문서와 충돌 | 현재 plan은 random lane 선택을 명시 | 문서 업데이트로 random 초기 정책을 fair routing으로 대체 |
| ELO 영향 해석 오류 | 공평 분배만으로 실력판독이 완성되는 것은 아님 | rating에는 여전히 incoming threat normalization 필요 |

## 최종 판단

라인별 인컴 분배 공평화는 구현 타당성이 높고, 현재 문제인 “한 lane에 너무 과도하게 가서 실력판독이 어려움”을 직접 완화한다.

추천 우선순위:

1. **MVP:** queued/pending `attributionThreat` 기반 `LEAST_THREAT_PRESSURE + ROUND_ROBIN tie-break`
2. **2차:** active income/recent assigned threat 추가
3. **3차:** role/tier/ability를 반영한 `routingThreat` 또는 `effectiveThreat`
4. **4차:** rating contribution normalization 연계

가장 중요한 설계 원칙은 다음이다.

```text
target team 선택의 전략성은 유지하고,
target lane 선택은 유닛 수가 아니라 누적 위협/가치 기준으로 분산한다.
```

이렇게 해야 팀장 target/공격 방향 선택의 재미는 유지하면서, 20 에메랄드 인컴 유닛과 200 에메랄드 인컴 유닛을 같은 부담으로 취급하는 가짜 평등을 피할 수 있다. lane 담당자별 방어 지표와 ELO 입력 데이터도 더 실질적인 압력 기준으로 정렬된다.
