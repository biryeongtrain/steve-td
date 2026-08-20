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
- `towerSlotCost`는 타워 제한에서 차지하는 가중치입니다. 없으면 `1`입니다.

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
| 염소 | `radius`, `damageBonus`, `damageReduction`, `cloneDamageBonus`, `cloneDamageReduction`, `maxStacks`, `buffDurationTicks` | 일반 무리 타워와 환영에 적용할 피해 증가·받는 피해 감소 버프 값입니다. |
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

## 대적자 계열 능력값

히어로 빌더는 값을 세 위치에서 읽습니다.

- `towers`: `adversary_fox`의 기본 배치 수치와 `adversary_<종류>_rival`, `adversary_<종류>_rival_enhanced` 숙적의 절대 수치입니다. `<종류>`는 `breeze`, `creeper`, `phantom`, `polar_bear`입니다.
- `upgradeCosts`: 일반 숙적에서 같은 종류의 강화 숙적으로 가는 네 방향성 업그레이드 가격입니다.
- `abilities`: 전역 전투 규칙, 여우 형태별 수치와 진화 요구량, 숙적의 방어·점수입니다.

여우 형태 설정 ID는 기본형만 `adversary_fox`이고, 나머지는 `adversary_fox_form_<형태>`입니다. 예: `adversary_fox_form_golden_fang`, `adversary_fox_form_sculk_core`.

| 형태별 키 | 의미 |
|---|---|
| `maxHealth`, `range`, `damage`, `attackIntervalTicks`, `damageReduction` | 해당 형태의 체력, 사거리, 피해, 공격 간격, 받는 피해 감소율입니다. Mace와 Sculk 특수 공격도 여기의 `damage`와 `attackIntervalTicks`를 사용합니다. |
| `requiredBreezeScore`, `requiredCreeperScore`, `requiredPhantomScore`, `requiredPolarBearScore` | 값이 있는 숙적 종류의 진화 요구 점수입니다. |

숙적 ID의 `abilities`에는 다음 두 키가 있습니다.

| 숙적별 키 | 의미 |
|---|---|
| `baseArmor` | 라운드 보정 전 방어입니다. 강화 숙적은 강화값이 이미 포함된 절대값입니다. |
| `scorePerKill` | 여우가 해당 숙적에 막타를 냈을 때 얻는 점수입니다. |

강화 숙적의 체력·사거리·공격력·공격 간격·방어는 강화 숙적 ID의 절대값이 기준입니다. 전역 강화 배수를 따로 설정하지 않습니다.

전역 설정 ID는 `adversary_global`입니다.

| 기능 | 주요 키 |
|---|---|
| 숙적 라운드 보정 | `rivalRoundHealthGrowth`, `rivalRoundDamageGrowth`, `rivalArmorRoundInterval` |
| 기본/질풍 공격 | `baseSplashRadius`, `baseSplashExtraTargets`, `baseSplashDamageRatio`, `breezeExtraTargets`, `breezeExtraTargetDamageRatio` |
| 황금/방패 공격 | `goldenExtraAttackEvery`, `goldenExtraDamageRatio`, `shieldCounterDamage`, `shieldCounterCooldownTicks` |
| 여우 회복·팀 지원 | `bellHealIntervalTicks`, `bellHealRadius`, `bellHealTargetCount`, `bellHealMaxHealthRatio`, `beaconHealIntervalTicks`, `beaconHealRadius`, `beaconHealTargetCount`, `beaconHealMaxHealthRatio`, `ominousMonsterDamageReduction`, `ominousMonsterAttackSpeedReduction`, `ominousMonsterTowerDamageTakenBonus`, `teamEffectScanIntervalTicks`, `teamEffectDurationTicks` |
| 폭죽 관통 | `fireworkWaveDamageMultiplier`, `fireworkIncomeDamageMultiplier`, `fireworkMaxTargets`, `fireworkSecondary2Ratio` ~ `fireworkSecondary8Ratio` |
| 거물/메아리 | `bigGameWaveDamageMultiplier`, `bigGameIncomeDamageMultiplier`, `bigGameStreak2`, `bigGameStreak3`, `echoBonusPerHit`, `echoMaxBonusStacks` |
| 메이스 | `maceFocusTicks`, `maceBreakHealthRatio`, `maceStreak2` ~ `maceStreak5`, `maceSweepRadius`, `maceSweepExtraTargets`, `maceSweepDamageRatio` |
| 스컬크 | `sculkDelayTicks`, `sculkRadius`, `sculkMaxTargets`, `sculkSelfDamageRatio`, `sculkSelfDamageFloorRatio` |

비율은 `0.0`~`1.0`, tick과 개수는 정수로 둡니다. 형태의 체력·사거리·피해·공격 간격과 핵심 쿨다운/반경은 양수여야 하며, 잘못된 값은 reload 검증에서 거부됩니다.

## 식물 계열 능력값

식물 빌더는 다른 빌더와 달리 능력값이 **타워 단위가 아니라 지형 단위**로 들어갑니다. 지형 키 4개와 공통 키
1개가 있고, 각 전투 타워의 `soilPower`가 티어별 배율을 담당합니다.

| config id | 주요 키 | 의미 |
|---|---|---|
| `plant_global` | `bloomDamagePerTile`, `bloomDamageCap` | 개화. 자기 계열 지형 1칸당 피해 증가와 그 상한입니다. |
| `plant_global` | `soilPulseIntervalTicks`, `soilAuraMinRadius` | 지형 효과 갱신 주기와 최소 적용 반경입니다. |
| 민들레 계열 id | `diamondPerWave` | 팀이 웨이브를 마칠 때 살아 있고 잔디 위에 있는 타워가 지급하는 다이아입니다. 샌드박스 라운드 이동은 정산하지 않습니다. |
| `plant_global` | `environmentTickIntervalTicks` | 환경 효과 적용 주기입니다. 타워와 무관하게 돕니다. |
| `plant_soil_mycelium` | `environmentWeakness`, `environmentDamageTakenBonus`, `environmentDurationTicks` | 균사 위 적의 공격력 감소, 받는 타워 피해 증가, 지속 시간입니다. 균사 전투 타워는 지뢰라 상주하지 않으므로 딜증도 지형이 담당합니다. |
| 균사 계열 id | `triggerRadius`, `triggerIntervalTicks` | 지뢰 발동 반경과 확인 주기입니다. |
| 균사 계열 id | `fuseTicks` | 밟은 뒤 터지기까지의 도화선 길이입니다. 점화 순간 섬광 파티클이 한 번 뜨고, 이 시간이 지나야 폭발합니다. **폭발 판정은 터지는 시점에 다시 잡으므로 그 사이에 빠져나간 적은 맞지 않습니다.** 0 은 즉발이라 거부됩니다 — 밟는 순간 이미 맞은 뒤면 피할 여지가 없습니다. |
| 균사 계열 id | `explosionRadius`, `explosionDamageMultiplier`, `explosionHealthRatio` | 폭발 반경, 공격력 배율, 남은 체력 반영 비율입니다. 실제 피해는 `(damage × 배율 + 현재 체력 × 체력 비율) × (1+개화)`입니다. |
| 균사 계열 id | `explosionMoveSpeedReduction`, `explosionDisableTicks` | 폭발 둔화율과 무력화 시간입니다. 무력화 동안 공격 속도·공격력이 100% 깎입니다. 지뢰는 **라운드당 한 번**만 터지므로 이 시간이 그대로 라운드당 무력화 총량입니다. 라운드 안에서 다시 장전하는 값은 일부러 두지 않았습니다 — 재장전보다 무력화가 길면 그 길목의 적이 영영 공격하지 못합니다. |
| `plant_soil_desert` | `environmentAttackSpeedReduction` | 사암 위 적의 공격 속도 감소입니다. |
| `plant_soil_desert` | `environmentMaxHealthDamagePerSecond` | 사암 위 적이 초당 잃는 **최대 체력 비율**입니다. 펄스 간격을 바꿔도 초당 피해량은 유지됩니다. |
| `plant_soil_meadow` | `supportRadius`, `healPercentPerPulse` | 잔디 지원 범위와 펄스마다 주변 아군을 회복시키는 최대 체력 비율입니다. |
| `plant_global` | `meadowHealOverlapReduction` | 한 대상을 여러 잔디가 함께 회복시킬 때 **두 번째부터** 깎는 비율입니다(기본 50%). 겹치기 자체는 유효하되 잔디 개수만큼 회복이 선형으로 늘어나지 않게 합니다. 겹침 판정 창은 `soilPulseIntervalTicks` 를 그대로 씁니다 — 잔디들의 펄스는 서로 맞춰져 있지 않아 "같은 펄스"라는 것이 없기 때문입니다. |
| `plant_soil_meadow` | `maxHealthGrowthPerRound`, `maxHealthGrowthCap` | 잔디 성장. 라운드당 최대 체력 증가와 누적 상한입니다. |
| `plant_soil_meadow` | `growthShareRatio`, `supportDurationTicks` | 성장 체력 중 라인 전체에 나눠 주는 비율과 버프 지속 시간입니다. 잔디 타워별 몫을 **합산**해 라인 안 모든 타워에게 같은 값으로 겁니다. |
| 튤립 계열 id | `novaRadius`, `novaDamageRatio` | 타워 자신을 중심으로 터지는 광역 반경과 피해 비율입니다. |
| `plant_soil_desert` | `attackSpeedReduction`, `debuffDurationTicks`, `auraRadius` | 사암 전투 타워가 주변 사암 위 적에게 거는 공격 속도 감소, 지속 시간, 반경입니다. |
| `plant_soil_desert` | `thornReflectRatio` | 받은 피해를 가시로 반사하는 비율입니다. |
| `plant_soil_podzol` | `rangeBonus`, `attackSpeedBonus`, `damageGrowthPerRound`, `damageGrowthCap` | 회백토 위 전투 타워의 사거리·공격 속도와 웨이브별 피해 성장입니다. |
| 전투 타워 id | `soilPower` | 위 지형 값에 곱해지는 티어 배율입니다. 기본값은 T1 `0.6`, T2 `1.0`, T3 `1.4`입니다. |
| `t3_podzol_lilac_tower` | `splashRadius`, `splashDamageRatio` | 라일락 전용 꽃잎 스플래시입니다. 광역을 얻는 대신 `soilPower`가 `1.2`로 낮습니다. |

기본값 기준 최대 딜증은 개화 `+40%`와 균사 취약 `+25%`가 곱연산으로 붙어 `+75%`입니다.
기본 피해는 모든 티어에서 50 이하로 두고, 화력은 이 배율로 만듭니다. 이 규칙을 바꿀 때는
`PlantTowerCatalogTest`의 `baseDamageStaysAtOrBelowFiftyOnEveryTier`와
`soilAmplificationStaysWithinTheConservativeCap`도 함께 갱신합니다.

## 마왕 빌더

마왕은 타워가 아니라 플레이어가 싸우므로, `towers`의 전투 수치는 전부 0입니다. 실제 값은 제단별
`abilities`와 전역 `demon_lord_global`에 있습니다. 제단 id는 `t{1..4}_{스킬키}_tower` 형식이며
스킬 키는 `wave_of_malice`, `demon_wings`, `sky_breaker`, `arcane_bombardment`, `demon_barrier`,
`hellfire_brand`, `soul_drain`, `roar_of_dread`, `grip_of_doom`, `hell_guillotine`입니다.

| config id | 주요 키 | 의미 |
|---|---|---|
| 제단 id 전체 | `towerSlotCost` | 빌더의 "코스트". 라운드 타워 한도를 이만큼 차지합니다. 티어가 올라도 바뀌지 않습니다. |
| 제단 id 전체 | `cooldownTicks` | 스킬 쿨타임입니다. 기본값은 티어마다 1초씩 줄어듭니다. |
| `demon_lord_global` | `baseMaxHealth`, `maxHealthPerLevel`, `maxLevel` | 마왕 체력 곡선입니다. 기본 450, 레벨당 +52.5, 만렙 30입니다. |
| `demon_lord_global` | `experiencePerMaxHealth`, `experienceBase`, `experienceGrowth` | 처치 경험치입니다. 처치 대상 최대 체력에 비례하며, 다음 레벨 요구량은 `experienceBase × experienceGrowth^(레벨-1)`입니다. |
| `demon_lord_global` | `damagePerLevel` | 스킬과 평타 모두에 곱해지는 성장 배율입니다. 기본 레벨당 +5%입니다. |
| `demon_lord_global` | `bladeDamage`, `bladeAttackIntervalTicks` | 마검 평타 수치입니다. 기본 피해는 19입니다. `bladeAttackIntervalTicks`는 바닐라와 같은 차지 곡선(`0.2 + 차지² × 0.8`)으로 피해에 곱해집니다. 바닐라 공격 쿨다운은 바닐라 피해 경로에만 걸리므로 직접 계산합니다. |
| `demon_lord_global` | `passiveExperiencePerRound` | 몹을 하나도 못 잡은 라운드에도 주는 기본 경험치입니다. 한 번 밀린 마왕이 영영 따라잡지 못하는 상황을 막습니다. 직접 잡는 편이 여전히 훨씬 빠릅니다. |
| `demon_lord_global` | `statPointsPerLevel` | 레벨업마다 받는 스탯 포인트 수입니다. 기본 3입니다. |
| `demon_lord_global` | `statHealthPerPoint`, `statAttackPerPoint` | 포인트당 최대 체력(기본 +40)과 피해 배율(기본 +4%p) 증가입니다. |
| `demon_lord_global` | `statDefensePerPoint`, `statDefenseCap` | 포인트당 피해 감소율(기본 +2%p)과 그 상한(기본 60%)입니다. 상한이 없으면 무적이 됩니다. |
| `demon_lord_global` | `statCooldownHalvingPoints` | 쿨감은 선형이 아니라 이 포인트마다 절반이 되는 곱연산입니다(기본 40 → 50%, 80 → 25%). 0 에 닿지 않습니다. **다른 스탯보다 포인트를 많이 요구하는 것은 의도된 것입니다** — 쿨감은 모든 스킬에 한꺼번에 곱해지고 딜뿐 아니라 생존기·이동기 회전율까지 같이 올려서, 같은 효율로 두면 다른 선택지가 존재할 이유가 없어집니다. |
| `demon_lord_global` | `statSkillRangePerPoint` | 포인트당 스킬 거리 증가입니다(기본 +3%p). 사거리·반경·돌진 거리 같은 거리 계열 값 전부에 곱해집니다. |
| `demon_lord_global` | `statMoveSpeedPerPoint`, `statMoveSpeedCap` | 포인트당 이동 속도 증가와 상한입니다. 물약 효과가 아니라 일시 속성 수정자라 등급 단위(20%)가 아닌 잔단위를 표현할 수 있습니다. |
| `..._wave_of_malice_tower` | `coneDegrees`, `range`, `damage`, `knockback` | 전방 부채꼴 각도·거리·피해·넉백입니다. |
| `..._demon_wings_tower` | `leapPower`, `radius`, `damage`, `knockback`, `healRatio` | 도약력, 광역 반경, 피해, 넉백, 최대 체력 대비 회복량입니다. |
| `..._sky_breaker_tower` | `dashDistance`, `hitRadius`, `damage`, `liftPower`, `stunTicks` | 돌진 거리, 경로 판정 반경, 피해, 띄우기 세기, 기절 시간입니다. 기절은 이동·공격 속도·공격력을 100% 깎습니다. |
| `..._arcane_bombardment_tower` | `jumpPower`, `castDelayTicks`, `projectileRange`, `blastRadius`, `damage` | 솟아오르는 힘, 정점에서 발사까지의 대기 시간, 착탄 지점까지의 최대 거리, 폭발 반경, 피해입니다. 조준은 시전 시점이 아니라 **발사 시점의 시선**을 씁니다. |
| `..._demon_barrier_tower` | `shieldRatio`, `shieldDurationTicks` | 최대 체력 대비 방어막 비율과 지속 시간입니다. 중첩되지 않고 큰 쪽으로 갱신됩니다. |
| `..._hellfire_brand_tower` | `placementRange`, `zoneRadius`, `zoneDurationTicks`, `tickIntervalTicks` | 시선으로 까는 최대 거리, 장판 반경, 지속 시간, 피해 주기입니다. 장판은 한 번에 하나만 유지되고 재시전하면 이전 것을 덮어씁니다. |
| `..._hellfire_brand_tower` | `damage`, `damageTakenBonus` | 주기마다 들어가는 피해와, 장판 위 적이 받는 피해 증가입니다. |
| `..._soul_drain_tower` | `range`, `width`, `damage` | 전방 직선 판정의 길이·폭과 대상당 피해입니다. |
| `..._soul_drain_tower` | `rootDurationTicks` | 꿰뚫린 적을 그 자리에 묶는 시간입니다. 이동 속도를 100% 깎는 것이라 **공격은 계속합니다** — 붙어 있는 적을 떼어내는 용도가 아니라 지나가려는 줄을 세우는 용도입니다. |
| `..._soul_drain_tower` | `lifeStealRatio`, `lifeStealCap` | 입힌 피해 대비 회복 비율과, 1회 회복량의 최대 체력 대비 상한입니다. 여럿을 꿰뚫어도 상한을 넘지 않습니다. |
| `..._roar_of_dread_tower` | `radius`, `damage`, `knockback` | 광역 반경, 피해, 넉백 세기입니다. |
| `..._roar_of_dread_tower` | `moveSpeedReduction`, `dreadDurationTicks` | 이동 속도 감소율과 지속 시간입니다. 지속 동안 공격도 함께 막힙니다. |
| `..._grip_of_doom_tower` | `range`, `executeHealthRatio` | 지목 사거리와 처형 임계값입니다. 대상 체력이 최대 체력의 이 비율 이하면 고정 피해로 즉사시킵니다. **1.0으로 올리면 체력과 무관한 무조건 즉사가 되어 상대가 비싼 유닛을 뽑을 이유가 사라집니다.** |
| `..._grip_of_doom_tower` | `explosionRadius`, `explosionHealthRatio`, `areaDamage` | 처형 시 시체 폭발입니다. 피해는 `처형 시점 체력 × explosionHealthRatio + areaDamage`이므로 단단한 적일수록 크게 터집니다. |
| `..._grip_of_doom_tower` | `damage`, `missingHealthRatio`, `pullStrength`, `killRefundTicks` | 임계값을 넘긴 대상에게 들어가는 일반 피해와 **대상이** 잃은 체력 비례 추가 피해, 끌어당김 세기, 처형 성공 시 쿨타임 환급입니다. |
| `..._hell_guillotine_tower` | `range`, `radius`, `damage` | 순간이동 사거리, 착지 광역 반경, 기본 피해입니다. |
| `..._hell_guillotine_tower` | `missingHealthDamageBonus` | **마왕 자신이** 잃은 체력 비율에 곱해지는 최대 피해 증가폭입니다. 빈사에서 `기본 × (1 + 이 값)`이 됩니다. 손아귀와 달리 시전자 기준인 점에 주의합니다. |

스킬 10종을 전부 열면 코스트 합이 32입니다. 이 값을 바꾸면
`DemonLordTowerCatalogTest`의 `skillCostsMatchTheDesignedValues`와
`openingEverySkillCostsMoreThanAnEarlyTowerLimit`도 함께 갱신합니다.

> **주의**: `tower_balance.json`은 번들 리소스가 코드 기본값과 **병합되지 않고 통째로 대체**합니다.
> Java의 `putDemonLordAbilities`만 고치면 런타임에서 값이 폴백으로 떨어지며 컴파일로는 잡히지 않습니다.
> `src/main/resources/semiontd/balance-defaults/tower_balance.json`도 함께 고쳐야 하며,
> 어긋나면 `bundledResourceCarriesEveryDemonLordEntryThatCodeDefines` 테스트가 깨집니다.

## 겜블 빌더

겜블 빌더의 전역 설정 ID는 `gamble_global`입니다. 도박 능력치는 퍼센트 배율이 아니라 점수를 고정 수치로 환산해 더합니다.

| 키 | 기본값 | 의미 |
|---|---:|---|
| `oddEvenWinScore`, `oddEvenLossScore` | 70, 40 | 홀짝 성공·실패 점수의 절댓값 |
| `maxHealthPerScore` | 5.0 | 점수 1당 최대 체력 변화 |
| `damagePerScore` | 0.50 | 점수 1당 공격력 변화 |
| `rangePerScore` | 0.05 | 점수 1당 사거리 블록 변화 |
| `splashRadiusPerScore` | 0.025 | 이전 공격 범위 도박 상태 호환용 값이며 신규 도박에서는 사용하지 않음 |
| `baseSplashRadius`, `splashDamageRatio` | 2.5, 0.60 | 고정 기본 공격 범위와 주변 적 피해 비율 |
| `twoDiceLoss2`~`twoDiceLoss5` | 70, 50, 30, 10 | 주사위 두 개 실패 점수 절댓값 |
| `twoDiceGain6`~`twoDiceGain12` | 20, 40, 50, 60, 90, 120, 150 | 주사위 두 개 성공 점수 |
| `twoDiceCompoundMinSum` | 10 | 전체 보상을 서로 다른 능력치 두 개가 절반씩 나눠 받는 최소 합계 |
| `abilityRewardChance` | 0.25 | 성공 시 미보유 능력으로 바뀔 확률 |
| `lossInsuranceReduction` | 0.20 | 손실 보험의 음수 고정치 완화율 |
| `supportVfxIntervalTicks` | 40 | 지원 범위와 대상 연결선 재표시 간격 |
| `supportPositiveRangeUnit`, `supportNegativeRangeUnit` | 0.25, 0.25 | 사거리 강화·약화 기본 단위 |
| `supportPositiveRegenUnit`, `supportNegativeHealthLossUnit` | 2.5, 1.0 | 초당 회복·비치명적 초당 체력 감소 기본 단위 |
| `supportPositiveDamageUnit`, `supportNegativeDamageUnit` | 2.5, 2.5 | 공격력 강화·약화 기본 단위 |
| `supportPositiveMaxHealthUnit`, `supportNegativeMaxHealthUnit` | 25, 25 | 최대 체력 강화·약화 기본 단위 |
| `maxSpectatorsPerGambler` | 3 | 한 도박꾼에 연결할 수 있는 구경꾼 수 |
| `kingPromotionScore` | 400 | 도박왕 전직에 필요한 누적 점수 |
| `darkKingPromotionScoreMagnitude` | 200 | 어둠의 도박왕 전직에 필요한 음수 누적 점수의 절댓값 |
| `maxGambleScore` | 500 | 누적 점수 상한. 도달하면 도박 업그레이드를 모두 닫고 능력치별 양수 변화도 같은 점수 환산 상한을 적용 |

지원 타워 ID별 `minimumRoll`과 `supportPowerMultiplier`가 최저 눈과 긍정 효과 배율을 정합니다. 기본 배율은 T1/T2/T3 `1.0/2.0/3.5`이고 약화 효과에는 적용되지 않습니다. 주사위 타워는 범위 안의 자기 전투 타워를 모두 지원하고, 구경꾼은 누적 도박 점수가 가장 높은 도박꾼 하나를 지원합니다. 두 지원 계열의 체력은 전 티어 10이며 범위는 `3.5/5/6.5`입니다.

`gamble_king`과 `gamble_dark_king`의 `splashRadiusBonus` 기본값은 각각 `0.5`, `0.75`입니다. 전직 전 도박 상태는 새 타워 타입으로 복사되며, 운영 설정에는 새 기본 키가 자동 병합되므로 기존 월드·운영값을 지우지 않습니다.

## 수정 절차

1. 서버의 `config/semion-td/tower_balance.json`을 백업합니다.
2. `towers`에서 배치 가격과 기본 전투 수치를 조정합니다.
3. 업그레이드 가격은 `upgradeCosts`의 `fromTowerId->upgradeId` 키에서 조정합니다.
4. 버프, 스택, 흡혈, 범위, 지속 시간은 `abilities`에서 조정합니다.
5. 주민 ADV 경험치와 평판은 `villagerAdv`에서 조정합니다.
6. `/semiontd reload`를 실행합니다.
7. `/semiontd tower list`, 타워 UI, 실제 설치/업그레이드로 값이 반영됐는지 확인합니다.
