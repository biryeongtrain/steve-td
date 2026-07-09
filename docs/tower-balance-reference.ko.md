# 타워 수치 설정

`tower_balance.json`은 타워의 전투 수치, 업그레이드 가격, 타워별 고유 능력값을 나눠서 관리합니다. 서버는 `config/semion-td/tower_balance.json`을 읽고, 없는 필드는 현재 코드의 기본값으로 채웁니다.

## 먼저 볼 규칙

- `towers`는 타워 공통값입니다. 배치 가격, 체력, 사거리, 공격력처럼 `TowerType`에 들어가는 값을 덮어씁니다.
- `upgradeCosts`는 업그레이드 가격입니다. 업그레이드 비용은 업그레이드 대상 타워의 `mineralCost`로 정해지지 않습니다.
- `abilities`는 타워별 고유 수치입니다. 버프, 흡혈, 스택, 범위, 지속 시간 같은 값이 여기에 들어갑니다.
- `illusionCloneQueue`는 무리 빌더 환영 소환 큐 설정입니다.
- `villagerAdv`는 주민 ADV 빌더의 경험치, 평판, 경험치 요구량, 보너스 수치입니다.

`/semiontd reload`는 타워 밸런스 설정과 카탈로그를 다시 읽습니다. 진행 중인 게임에서 새로 설치하거나 업그레이드하는 타워부터 확인하는 편이 안전합니다.

## `towers`: 타워 공통값

형태:

```json
{
  "towers": {
    "t1_cat_tower": {
      "mineralCost": 120,
      "maxHealth": 50.0,
      "range": 10.0,
      "damage": 10.0,
      "attackIntervalTicks": 15,
      "aggroPriority": 5
    }
  }
}
```

필드:

| 필드 | 의미 |
|---|---|
| `mineralCost` | starter 타워를 새로 설치할 때 드는 다이아입니다. 업그레이드 가격이 아닙니다. |
| `maxHealth` | 타워 최대 체력입니다. 최소 `1.0`으로 보정됩니다. |
| `range` | 타워 사거리입니다. 블록 단위입니다. |
| `damage` | 기본 공격력입니다. |
| `attackIntervalTicks` | 공격 간격입니다. 낮을수록 빨리 공격합니다. 최소 `1`입니다. |
| `aggroPriority` | 적이 방어 대상을 고를 때 쓰는 우선순위입니다. 값이 높을수록 먼저 맞습니다. |

`towers`는 표시명, 엔티티 외형, 업그레이드 경로를 바꾸지 않습니다. 그런 값은 Java의 타워 카탈로그가 정합니다.

## `upgradeCosts`: 업그레이드 가격

형태:

```json
{
  "upgradeCosts": {
    "t1_cat_tower->t2_anti_tanker_cat_tower": 250,
    "t1_cat_tower->t2_lane_clear_cat_tower": 200
  }
}
```

가격 결정 순서:

1. `fromTowerId->upgradeId` 키를 먼저 찾습니다.
2. 없으면 `upgradeId`만 있는 키를 찾습니다.
3. 그래도 없으면 코드 기본값을 씁니다.

운영에서는 `fromTowerId->upgradeId` 형태를 씁니다. `upgradeId`만 쓰면 서로 다른 타워가 같은 업그레이드 ID를 공유할 때 의도하지 않은 가격이 붙을 수 있습니다.

중요한 점:

- 업그레이드 가격은 대상 타워의 `towers.<targetTowerId>.mineralCost`를 보지 않습니다.
- 업그레이드 가격은 `ProductionTowerCatalog.linkUpgrade(...)`가 카탈로그를 만들 때 `TowerBalanceRuntime.upgradeCost(from, upgradeId)`로 가져옵니다.
- 주민 ADV 빌더의 다이아 가격도 `upgradeCosts`를 씁니다. 별도로 필요한 경험치는 `villagerAdv.upgradeRequirements`가 정합니다.
- `0`도 유효한 가격입니다. 예를 들어 흑마법사 기본 타워의 원거리/근접 분기처럼 무료 분기를 만들 수 있습니다.

## `abilities`: 타워별 고유 수치

형태:

```json
{
  "abilities": {
    "t2_librarian_tower": {
      "bonusPerSurvivedRound": 0.05,
      "maxSurvivalStacks": 6,
      "splashRadius": 1.25,
      "splashDamageRatio": 0.75
    }
  }
}
```

규칙:

- 키는 타워 ID입니다. 예: `t2_librarian_tower`, `t3_fox_tower`.
- 값은 `double` 숫자입니다. 정수처럼 보이는 값도 JSON에서는 숫자로 둡니다.
- `0.10`은 10%입니다.
- `*Ticks`는 Minecraft tick입니다. `20 tick = 1초`입니다.
- `radius`, `range`는 블록 단위입니다.
- 코드가 읽지 않는 키는 효과가 없습니다.

## 주민 계열 능력값

주민 빌더와 주민 ADV 빌더는 별도 빌더입니다. 주민 ADV 빌더는 별도 타워 ID와 `villagerAdv` 경험치/평판 설정을 씁니다.

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 사서/성직자 | `bonusPerSurvivedRound`, `maxSurvivalStacks` | 살아남은 라운드당 공격 보너스와 최대 스택입니다. |
| 사서/성직자 | `splashRadius`, `splashDamageRatio` | 스플래시 범위와 피해 비율입니다. |
| 성직자 | `extraAttackEvery` | 몇 번 공격할 때마다 추가 공격을 넣을지 정합니다. |
| 라마/철 골렘 | `thornCooldownTicks`, `thornDamage`, `thornRadius` | 피격 반격의 쿨다운, 피해, 범위입니다. |
| 라마/철 골렘 | `healthBonusPerSurvivedRound`, `maxSurvivalStacks` | 생존 라운드 기반 체력 보너스입니다. |
| 알레이/대장장이 | `supportBlockTicks`, `buffDurationTicks`, `radius` | 지원 효과 재적용 제한, 지속 시간, 범위입니다. |
| 알레이/갑옷 제조인 | `healAmount`, `damageReduction` | 회복량과 받는 피해 감소율입니다. |
| 대장장이 | `weaponBuff` | 공격력 또는 공격 속도 지원 배율입니다. |
| 저격 캣 | `nonWaveBonus`, `tankBonus`, `stackDamage`, `stackDamageCap` | 인컴/탱커 대상 피해와 처치 스택 피해입니다. |
| 라클 캣 | `waveBonus`, `stackDamage`, `stackDamageCap`, `explosionRadius` | 웨이브 대상 피해, 처치 스택, 폭발 범위입니다. |

## 주민 ADV 빌더 `villagerAdv`

형태:

```json
{
  "villagerAdv": {
    "experienceMax": 100.0,
    "experiencePerTower": 1.0,
    "experiencePerTier": 1.0,
    "reputationMax": 100.0,
    "upgradeRequirements": {
      "villager_adv_villager_splash_t1->villager_splash_t2": 15.0
    },
    "buffs": {
      "villager_adv_villager_splash_t1": {
        "rangedDamagePerExperience": 0.0015,
        "rangedDamagePerExperienceInterval": 1.0
      }
    }
  }
}
```

필드:

| 필드 | 의미 |
|---|---|
| `experienceMax` | 플레이어별 경험치 상한입니다. |
| `experiencePerTower` | 라운드 시작 시 타워 1기당 얻는 경험치입니다. |
| `experiencePerTier` | 타워 티어에 따른 추가 경험치입니다. |
| `reputationMax` | 평판 상한입니다. |
| `reputationGainRoundMultiplier` | 웨이브 방어 성공 시 라운드값에 곱하는 평판 획득 배율입니다. |
| `reputationLossPerLeak` | 누수 1회당 평판 감소량입니다. |
| `effectDurationTicks` | 주민 ADV 보너스 효과 지속 시간입니다. |
| `experienceBuffCap` | 경험치 기반 보너스 상한입니다. |
| `reputationBuffCap` | 평판 기반 보너스 상한입니다. |
| `upgradeRequirements` | 주민 ADV 업그레이드에 필요한 경험치입니다. 다이아 가격이 아닙니다. |
| `buffs` | 타워 ID별 경험치/평판 보너스 수치입니다. |

`upgradeRequirements`도 `fromTowerId->upgradeId` 키를 먼저 봅니다. 다이아 가격은 여기가 아니라 `upgradeCosts`입니다.

자주 쓰는 `buffs` 키:

- `reputationDamagePerPoint`, `reputationAttackSpeedPerPoint`, `reputationHealthPerPoint`, `reputationDamageReductionPerPoint`
- `rangedDamagePerExperience`, `rangedAttackSpeedPerExperience`
- `golemHealthPerExperience`, `golemDamageReductionPerExperience`
- `allayHealAmountPerExperience`, `allayIntervalReductionPerExperience`
- `catDamagePerExperience`, `catAttackSpeedPerExperience`
- `catIncomeDamagePerExperience`, `catWaveDamagePerExperience`

각 키에 `<key>Interval`을 붙이면 보너스 적용 간격을 조정합니다.

## 언데드 계열 능력값

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 좀비/허스크/드라운드 | `lifeStealRatio` | 준 피해 대비 회복 비율입니다. |
| 좀비 | `killDamageBoost`, `damageBoostTicks` | 처치 후 공격력 증가량과 지속 시간입니다. |
| 허스크/드라운드 | `damageBoostOnHit`, `damageBoostTicks` | 피격 후 공격력 증가량과 지속 시간입니다. |
| 허스크/드라운드 | `thornRadius`, `thornCooldownTicks`, `thornHealPerHit` | 피격 반격 범위, 쿨다운, 적중당 회복량입니다. |
| 드라운드 | `lastStandTicks` | 치명 피해를 받고 버티는 시간입니다. |
| 보그드/스트레이 | `extraTargets`, `lifeStealRatio`, `stackDamage`, `stackDamageCap` | 추가 공격 대상 수, 흡혈, 사망 스택 피해입니다. |
| 위더 스켈레톤 | `splashRadius`, `splashDamageRatio` | 근접 광역 범위와 피해 비율입니다. |
| 위더 스켈레톤 | `damagePerStack`, `healthPerStack`, `stackCap` | 주변 사망 스택당 공격력/체력 증가와 상한입니다. |
| 좀비 말/스켈 말 | `scanIntervalTicks`, `debuffDurationTicks`, `radius` | 디버프 탐색 간격, 지속 시간, 범위입니다. |
| 좀비 말/스켈 말 | `attackDamageReduction`, `towerDamageTakenBonus` | 적 공격력 감소와 타워 피해 증폭입니다. |

## 동물 계열 능력값

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 돼지 | `maxStacks`, `healthPerStack`, `damagePerStack` | 같은 계열 타워 수에 따른 체력/공격력 스택입니다. |
| 돼지 | `damageReduction`, `splashRadius`, `splashDamageRatio` | 최대 스택 보너스의 피해 감소와 스플래시입니다. |
| 늑구 | `maxStacks`, `damagePerStack`, `intervalReductionPerStack` | 같은 계열 타워 수에 따른 공격력과 공격 간격 감소입니다. |
| 늑구 | `maxStackExtraIntervalReduction`, `maxStackDamageBonus` | 최대 스택 도달 시 추가 공격 속도와 공격력입니다. |
| 늑구 | `splashRadius`, `splashDamageRatio` | 업그레이드 후 스플래시 범위와 피해 비율입니다. |
| 토끼 | `maxStacks`, `damagePerStack` | 같은 계열 타워 수에 따른 공격력 스택입니다. |
| 토끼 | `maxStackExtraIntervalReduction`, `extraAttackDamageRatio` | 최대 스택 추가 공격 속도와 추가 공격 피해 비율입니다. |
| 여우 | `maxStacks`, `executeHealthThreshold`, `executeThresholdPerStack`, `maxExecuteHealthThreshold` | 처형 대상 체력 기준과 같은 계열 스택 보정입니다. |
| 여우 | `executeDamageBonusRatio`, `executeDamageBonusPerStack` | 처형 대상 추가 피해입니다. |
| 여우 | `killBonusDamage`, `killBonusDamageCap` | 주변 적 사망으로 얻는 영구 피해 보너스와 상한입니다. |

## 흑마법사 계열 능력값

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 기본 흑마법사 | `baseSacrificeRadius`, `baseFatalHealRatio` | 희생 대상 탐색 범위와 치명 상황 회복 비율입니다. |
| 기본 흑마법사 | `basePermanentHealthRatio`, `basePermanentDamageRatio` | 희생으로 얻는 영구 체력/공격력 비율입니다. |
| 원거리/근접 흑마법사 | `lowHealthSacrificeThreshold`, `sacrificeRadius`, `roundStatRatio` | 저체력 희생 기준, 희생 범위, 라운드 임시 스탯 반영 비율입니다. |
| 원거리 흑마법사 | `lifeStealEvery`, `lifeStealPerStep`, `lifeStealCap` | 희생 누적에 따른 흡혈 증가입니다. |
| 원거리 흑마법사 | `splashEvery`, `splashRadiusPerStep`, `splashDamageRatio` | 희생 누적에 따른 스플래시입니다. |
| 근접 흑마법사 | `damageReductionEvery`, `damageReductionPerStep`, `damageReductionCap` | 희생 누적에 따른 피해 감소입니다. |
| 근접 흑마법사 | `lifeStealPerSacrifice`, `lifeStealCap`, `roundSplashRadiusPerSacrifice` | 희생당 흡혈과 라운드 스플래시 증가입니다. |
| 희생 양/개구리 | `deathEffectRadius`, `deathEffectDurationTicks` | 사망 효과 범위와 지속 시간입니다. |
| 희생 양/개구리 | `towerDamageTakenBonus`, `attackSpeedReduction` | 적이 받는 타워 피해 증가와 공격 속도 감소입니다. |

## 무리 계열 능력값

환영 계열 공통 키:

| 키 | 의미 |
|---|---|
| `cloneCount` | 생성할 환영 수입니다. |
| `cloneDurationTicks` | 환영 지속 시간입니다. 없으면 코드 기본값을 씁니다. |
| `cloneHealthRatio` | 원본 대비 환영 체력 비율입니다. |
| `cloneDamageRatio` | 원본 대비 환영 공격력 비율입니다. |
| `cloneRangeRatio` | 원본 대비 환영 사거리 비율입니다. |
| `cloneAttackIntervalMultiplier` | 원본 대비 환영 공격 간격 배율입니다. 낮을수록 빠릅니다. |
| `cloneSpawnRadius` | 환영 생성 반경입니다. |
| `cloneAggroPriorityBonus` | 환영의 어그로 우선순위 추가값입니다. |

타워별 추가 키:

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 닭/펭귄 | `splashRadius`, `splashDamageRatio` | 광역 피해 범위와 피해 비율입니다. |
| 슬라임 | `regenAmount`, `regenIntervalTicks` | 자가 회복량과 회복 간격입니다. |
| 앵무 | `attackStackBonus`, `maxAttackStacks` | 공격할수록 쌓이는 공격력 배율과 상한입니다. |
| 염소 | `radius`, `damageBonus`, `cloneDamageBonus`, `cloneDamageReduction`, `maxStacks`, `buffDurationTicks` | 주변 타워와 환영을 강화하는 버프 값입니다. |
| 벌 | `maxSwarmStacks`, `poisonDamagePerStack`, `poisonDamagePerSwarmStack`, `maxPoisonStacks`, `poisonStacksPerSwarmStack`, `poisonDurationTicks`, `poisonTickIntervalTicks` | 벌 계열 독 스택 값입니다. 현재 카탈로그 등록 코드는 비활성 주석 상태입니다. |

`illusionCloneQueue`:

| 필드 | 의미 |
|---|---|
| `spreadTicks` | 환영 소환을 몇 tick에 나눠 처리할지 정합니다. |
| `maxSpawnsPerTick` | tick당 환영 생성 상한입니다. |

## 무블룸 계열 능력값

공통 링크 키:

- `linkRange`: 공명 링크 거리입니다.
- `maxLinksPerTower`: 한 타워가 연결할 수 있는 최대 링크 수입니다.
- `maxResonanceLevel`: 해당 타워가 열 수 있는 최대 공명 단계입니다.
- `level1RequiredLinks`, `level2RequiredLinks`, `level3RequiredLinks`: 단계별 필요 링크 수입니다.

계열별 키:

| 계열 | 주요 키 | 의미 |
|---|---|---|
| Focus | `focusLevel*AttackSpeedBonus`, `focusLevel*DamageBonus`, `focusStrikeEveryAttacks`, `focusStrikeDamageRatio` | 공격 속도/피해 보너스와 주기적 추가 타격입니다. |
| Wave | `waveLevel*SplashRadius`, `waveLevel*SplashDamageRatio`, `wavePulseEveryAttacks`, `wavePulseRadius`, `wavePulseDamageRatio` | 스플래시와 파동 피해입니다. |
| Frost | `frostLevel*SlowMagnitude`, `frostLevel*AttackSpeedReductionMagnitude`, `frostLevel*SlowTicks`, `frostPulse*`, `frostAuraRange` | 둔화, 공격 속도 감소, 둔화 대상 추가 피해입니다. |
| Amplify | `bloomLevel*DamageReduction`, `bloomLevel*AuraAttackSpeedBonus`, `bloomProtect*`, `bloomAuraRange` | 피해 감소, 주변 공격 속도, 보호/회복 효과입니다. |

`*`는 단계 숫자를 뜻합니다. 예: `focusLevel2DamageBonus`.

## 우민 계열 능력값

습격 전역 설정은 `illager_raid` 키 아래에 있습니다.

| 키 | 의미 |
|---|---|
| `gaugeMax` | 습격 발동에 필요한 게이지입니다. |
| `waveKillGauge` | 웨이브 적 처치 시 얻는 게이지입니다. |
| `incomeKillGauge` | 인컴 적 처치 시 얻는 게이지입니다. |
| `markedKillBonusGauge` | 표식 적 처치 추가 게이지입니다. |
| `illagerTowerDeathGauge` | 내 우민 타워 사망 시 얻는 게이지입니다. |
| `attackSpeedPercentPerTower` | 습격 중 우민 타워 1기당 공격 속도 보너스입니다. |
| `damagePercentPerTower` | 습격 중 우민 타워 1기당 공격력 보너스입니다. |
| `timedEffectDurationTicks` | 습격 보조 효과 지속 시간입니다. |

타워별 키:

| 타워 흐름 | 주요 키 | 의미 |
|---|---|---|
| 변명자/파괴수 | `raidDamageReduction`, `splashRadius`, `splashDamageRatio`, `raidSplashRadiusBonus`, `raidSplashDamageRatioBonus` | 습격 중 피해 감소와 광역 피해 보너스입니다. |
| 약탈자 단일/소환사 단일 | `incomeDamageBonus`, `raidIncomeDamageBonus`, `raidMarkedDamageBonus` | 인컴 유닛과 표식 대상 추가 피해입니다. |
| 약탈자 광역/소환사 광역 | `splashRadius`, `splashDamageRatio`, `raidSplashRadiusBonus`, `raidSplashDamageRatioBonus` | 광역 피해와 습격 보너스입니다. |
| 벡스/마녀/환술사 | `markDamageTakenBonus`, `markDurationTicks`, `raidMarkDamageTakenBonus`, `raidMarkDurationBonusTicks` | 표식 피해 증가와 지속 시간입니다. |
| 마녀/환술사 | `forceTargetRadius`, `raidForceTargetRadiusBonus` | 표식 대상 강제 타겟 범위입니다. |
| 약자/강자 표식 | `raidLowHealthMarkDamageTakenBonus`, `raidHighHealthMarkDamageTakenBonus` | 낮은 체력 또는 높은 체력 대상 표식 보너스입니다. |

## 수정 절차

1. 서버의 `config/semion-td/tower_balance.json`을 백업합니다.
2. `towers`에서 배치 가격과 기본 전투 수치를 조정합니다.
3. 업그레이드 가격은 `upgradeCosts`의 `fromTowerId->upgradeId` 키에서 조정합니다.
4. 버프, 스택, 흡혈, 범위, 지속 시간은 `abilities`에서 조정합니다.
5. 주민 ADV 경험치와 평판은 `villagerAdv`에서 조정합니다.
6. `/semiontd reload`를 실행합니다.
7. `/semiontd tower list`, 타워 UI, 실제 설치/업그레이드로 값이 반영됐는지 확인합니다.
