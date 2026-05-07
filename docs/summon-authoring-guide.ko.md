# 공격 소환수 추가 가이드

이 문서는 플레이어가 다른 팀으로 보내는 공격 소환수를 추가하는 방법을 정리한다.

공격 소환수는 config가 아니라 Java class로 정의한다. 이유는 소환수가 특수 능력, 쿨다운, 조건부 발동, 시각 모델, 타겟 우선도 같은 동작을 가져야 하기 때문이다.

## 현재 상태

가격 티어 enum은 `T1`부터 `T5`까지 존재한다.

```text
T1: 10-35 gas
T2: 40-80 gas
T3: 90-160 gas
T4: 180-320 gas
T5: 350+ gas
```

하지만 기본 등록된 소환수가 모든 티어를 채운 것은 아니다.

현재 기본 등록 상태:

```text
grunt             T1  RUSH
skitter_swarm     T1  SWARM
quilt_guard       T1  TANK
static_bobbin     T1  DISRUPTOR
button_nurse      T1  SUPPORT
popper_pod        T1  SIEGE
ironclad_tank     T2  TANK
ward_tank         T2  TANK
static_disruptor  T2  DISRUPTOR
pulse_support     T2  SUPPORT
gale_ferret       T3  RUSH
bulwark_bison     T3  TANK
wizard_cat        T3  DISRUPTOR
grove_alpaca      T3  SUPPORT
bombard_toad      T4  SIEGE
storm_lynx        T4  RUSH
aegis_golem       T4  TANK
null_imp          T4  DISRUPTOR
elder_sprite      T4  SUPPORT
siege_breaker     T5  SIEGE
apex_warden       T5  TANK, DISRUPTOR
oracle_phoenix    T5  SUPPORT, DISRUPTOR
```

현재 Blockbench 모델 리소스:

```text
grunt             semion-td:summon/t1_fox_kit
skitter_swarm     semion-td:summon/t1_honey_bee
quilt_guard       semion-td:summon/t1_shell_turtle
static_bobbin     semion-td:summon/t1_spark_axolotl
button_nurse      semion-td:summon/t1_medic_duck
popper_pod        semion-td:summon/t1_pincer_crab
ironclad_tank     semion-td:summon/t2_ironclad_boar
ward_tank         semion-td:summon/t2_ward_ram
static_disruptor  semion-td:summon/t2_static_owl
pulse_support     semion-td:summon/t2_pulse_fawn
gale_ferret       semion-td:summon/t3_gale_ferret
bulwark_bison     semion-td:summon/t3_bulwark_bison
wizard_cat        semion-td:summon/t3_wizard_cat
grove_alpaca      semion-td:summon/t3_grove_alpaca
siege_breaker     semion-td:summon/t5_siege
```

즉, T1/T2는 전체 역할군 초안과 모델 리소스가 채워졌고, T3는 rush/tank/disruptor/support 중간 티어 초안이 채워졌다.
T4/T5는 Java class 기반 gameplay catalog만 먼저 채웠으며, 새 Blockbench 모델은 아직 만들지 않았다.
T5는 기존 전차형 siege 모델을 사용하는 `siege_breaker`만 모델 리소스를 가진다.

## 추가 절차

1. `src/main/java/kim/biryeong/semiontd/summon/` 아래에 새 class를 만든다.
2. `SummonMonsterType`을 상속한다.
3. 생성자에서 소환수의 스탯, 역할, 티어, 시각 정보, 능력 발동 타입을 넘긴다.
4. 표시 이름은 `SummonDisplayNames`에 상수로 추가하고 생성자에서 그 상수를 사용한다.
5. 필요하면 `createMonster(...)`, `onSummoned(...)`, `createAbilityGoals(...)`를 override한다.
6. `SummonRegistry`에 등록한다.
7. GameTest를 추가한다.

## 기본 예시

```java
package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public final class ExampleRushSummon extends SummonMonsterType {
    public ExampleRushSummon() {
        super(
                "example_rush",
                "Example Rush",
                25,
                3,
                45,
                0,
                5,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                MonsterDimensions.DEFAULT,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                5
        );
    }
}
```

등록:

```java
public static final SummonMonsterType EXAMPLE_RUSH = register(new ExampleRushSummon());
```

등록 위치:

```text
src/main/java/kim/biryeong/semiontd/summon/SummonRegistry.java
```

## 생성자 필드

```text
id
  내부 id. 중복되면 안 된다.

displayName
  UI와 명령 출력에 쓸 표시명.
  나중에 수정하기 쉽도록 `SummonDisplayNames`의 상수를 사용한다.

gasCost
  소환 비용.

incomeGain
  소환 성공 시 소환한 플레이어가 얻는 income.

maxHealth
  최대 체력.

armor
  PHYSICAL 피해 감소.

attackDamage
  몬스터의 공격력.

attackKind
  MELEE 또는 RANGED.

entityTypeId
  vanilla 또는 modded entity id. 예: minecraft:zombie.

blockbenchModelId
  BIL이 로드할 Blockbench 모델 id.
  리소스 위치는 src/main/resources/model/<namespace>/<path>.bbmodel 또는 .ajmodel.

dimensions
  서버 gameplay hitbox.
  생략하거나 null이면 기본값 0.6 x 1.95를 사용한다.
  Blockbench 모델 크기에서 자동 추론하지 않는다.

damageType
  PHYSICAL, MAGIC, TRUE.

resistance
  MAGIC 피해 감소.

tier
  T1~T5.

roles
  RUSH, TANK, SWARM, DISRUPTOR, SUPPORT, SIEGE 중 하나 이상.

abilityActivations
  PASSIVE, CONDITIONAL, COOLDOWN 중 하나 이상.

mineralReward
  이 몬스터가 죽었을 때 방어 측에 지급할 mineral.
```

## 역할 선택 기준

```text
RUSH
  빠른 압박. 낮은 방어, 높은 진행 압박.

SWARM
  다수 개체 압박. 단일 타겟 타워를 흔드는 역할.

TANK
  armor 또는 resistance 특화. support를 가려주는 전방 역할.

DISRUPTOR
  방해자. 낮은 티어부터 COOLDOWN 능력을 가질 수 있다.

SUPPORT
  주변 몬스터 보조. 낮은 티어부터 COOLDOWN 능력을 가질 수 있다.

SIEGE
  보스/최종 방어선 압박. 기본은 T3 이상부터 권장.
```

## 타겟 우선도

런타임 몬스터의 기본 타겟 점수는 다음 기준을 따른다.

```text
targetScore = laneProgress * 100 + rolePriority + threatBonus
```

역할 우선도:

```text
SWARM      0
RUSH       5
SIEGE      15
SUPPORT    35
TANK       45
DISRUPTOR  45
```

추가 규칙:

```text
SIEGE는 laneProgress 0.8 이상에서 +30 보너스
TANK는 같은 진행도에서 SUPPORT보다 먼저 맞도록 설계
```

## 티어와 income 효율

가격이 높아질수록 gas 대비 income 효율은 낮아지고, 전투 가치와 능력 가치가 커져야 한다.

권장 범위:

```text
T1: gasCost의 10-13%
T2: gasCost의 8-11%
T3: gasCost의 6-9%
T4: gasCost의 4-7%
T5: gasCost의 3-5%
```

예외:

- `SUPPORT`, `DISRUPTOR`, `SIEGE`는 전투/능력 가치가 크므로 income 효율을 낮게 잡는다.
- `RUSH`, `SWARM`은 상대적으로 income 효율을 높게 잡아도 된다.

## 능력 구현 위치

단순히 런타임 몬스터를 바꾸는 능력은 `onSummoned(...)`에서 처리할 수 있다.

```java
@Override
public void onSummoned(SummonContext context, Monster monster) {
    monster.damage(10, DamageType.TRUE);
}
```

더 복잡한 능력은 아직 전용 buff/debuff runtime system이 없으므로 다음 구현 단계에서 별도 시스템으로 추가해야 한다.

권장 방향:

```text
PASSIVE
  항상 적용되는 능력.

CONDITIONAL
  체력 조건, 보스 도달, 사망 시 등 특정 조건에서 발동.

COOLDOWN
  일정 주기마다 발동. SUPPORT/DISRUPTOR는 낮은 티어부터 가능.
```

## 시각 모델

둘 중 하나는 반드시 있어야 한다.

```text
entityTypeId
blockbenchModelId
```

`blockbenchModelId`가 있으면 런타임 엔티티는 Blockbench Import Library(BIL)의 `AnimatedEntity`/`LivingEntityHolder`로 모델을 붙인다.
모델 파일은 BIL 규칙에 맞춰 `src/main/resources/model/<namespace>/<path>.bbmodel` 또는 `.ajmodel`로 둔다.
예를 들어 `semion-td:summon/custom_model`은 `src/main/resources/model/semion-td/summon/custom_model.bbmodel` 또는 `.ajmodel`을 찾는다.
모델 파일이 없으면 테스트 환경에서는 BIL holder를 만들지 않고 모델 ID만 보존한다.

`dimensions`는 렌더링 크기가 아니라 서버 hitbox다. 큰 전차형 소환수처럼 모델이 바닐라 좀비보다 크면 `MonsterDimensions.of(width, height)`를 명시한다.
폭과 높이는 finite 양수여야 하며, 잘못된 값은 등록/설정 로드 시 예외로 처리한다.

게임플레이 id와 모델 id는 분리한다. 예를 들어 T5 전차형 siege는 게임플레이 id `siege_breaker`를 유지하되 모델은 `semion-td:summon/t5_siege`를 사용한다.
새 T1 siege 모델을 만들 때는 `popper_pod` 같은 별도 게임플레이 id와 `semion-td:summon/t1_pincer_crab` 같은 tier 포함 모델 id를 쓴다.

## 테스트 체크리스트

새 소환수를 추가하면 최소한 다음을 테스트한다.

```text
SummonRegistry.find(id)가 성공하는가
tier와 roles가 의도대로 들어갔는가
displayName이 `SummonDisplayNames`의 상수를 사용하는가
gasCost와 incomeGain이 정책 범위에 맞는가
createMonster(...) 후 runtime Monster에 tier/roles/damageType/resistance가 보존되는가
특수 능력이 있다면 onSummoned 또는 능력 시스템에서 적용되는가
entityTypeId 또는 blockbenchModelId가 보존되는가
dimensions가 runtime Monster와 SemionMonsterEntity hitbox에 반영되는가
```

기존 테스트 위치:

```text
src/gametest/java/kim/biryeong/semiontd/gametest/SemionParticipantGameTest.java
```
