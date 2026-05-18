# 프로덕션 타워 카탈로그

## 현재 상태

기본 등록된 프로덕션 타워는 없다.

`ProductionTowerCatalog`는 비어 있는 registry facade로 남겨 두었고, 실제 타워 정의는 직접 추가해야 한다. 이 상태에서 `/semiontd tower list`와 빌드 UI는 등록된 starter tower가 없으므로 빈 목록을 보여준다.

## 패키지 구조

```text
src/main/java/kim/biryeong/semiontd/tower/
  Tower.java                       모든 타워의 공통 상태/훅
  EntityBackedTower.java         엔티티를 가진 타워의 공용 생명주기
  ProductionTowerCatalog.java      외부 API와 registry
  ProductionTower.java             런타임 프로덕션 타워
  ProductionTowerBehavior.java     팩션/스택/스플래시 동작값

src/main/java/kim/biryeong/semiontd/entity/tower/
  SemionTowerEntity.java           런타임 타워 엔티티
  goal/TowerAttackMonsterGoal.java 타워 공격/추적/스플래시 goal

src/main/java/kim/biryeong/semiontd/tower/catalog/
  ProductionTowerDefinitions.java  tower/behavior/upgrade/branch/line helper
  ProductionTowerLine.java         starter + 두 upgrade branch 묶음
  ProductionTowerBranch.java       tier-2 + ultimate 묶음
```

새 기본 타워를 만들 때는 `tower.catalog` 패키지 아래에 직접 카탈로그 클래스를 만들고, `ProductionTowerCatalog.registerLine(...)` 또는 `registerAll(...)`로 등록한다.
작성한 `register()` 메서드는 모드 초기화 흐름에서 한 번 호출해야 한다.

## 런타임 구조

현재 엔티티를 가진 타워의 상속 구조는 다음과 같다.

```text
Tower
  EntityBackedTower
    TestTower
    ProductionTower
    custom entity-backed production tower, optional
```

- `EntityBackedTower`는 배치, 제거, 라운드 리셋, 엔티티 health/position 동기화처럼 엔티티를 가진 타워가 공유하는 생명주기를 담당한다. 이름 그대로 엔티티 기반이라는 뜻이며, 공격 여부를 의미하지 않는다.
- `TestTower`는 GameTest와 테스트 명령용 타워다. 프로덕션 타워가 `TestTower`를 상속하지 않는다.
- `ProductionTower`는 catalog entry가 기본 생성하는 실전용 런타임 타워다. 커스텀 런타임은 `ProductionTower`를 상속해도 되고, 프로덕션 스택/스플래시 기본 동작이 필요 없으면 `EntityBackedTower`를 직접 상속해도 된다.
- `SemionTowerEntity`는 테스트/프로덕션 양쪽이 공유하는 엔티티다. 엔티티 타입 ID는 `semion-td:tower`이며, 예전 `SemionTestTowerEntity`/`test_tower` 전용 구조는 쓰지 않는다.
- `TowerAttackMonsterGoal`은 `SemionTowerEntity`에서 동작한다. 공격 속도, 스플래시, 스택 UI 값은 `Tower` 훅과 `ProductionTowerBehavior`를 통해 얻는다.
- 업그레이드 서비스는 기존 타워를 `ProductionTower`로 캐스팅하지 않고 일반 `Tower` 상태(`type`, `ownerPlayer`, `position`)로 판정한다. 업그레이드 대상 생성은 catalog entry의 factory가 담당한다.
- 업그레이드로 새 타워 인스턴스를 만들면 `Tower.copyFrom(previousTower, upgradeCost)`가 호출된다. 기본 판매/라운드 상태는 공통으로 복사되고, 영구 스택 같은 타워별 런타임 상태는 `copyRuntimeStateFrom(...)`을 override해 복사한다.

따라서 새 프로덕션 카탈로그만 추가하면 기본적으로 `semion-td:tower` 엔티티를 가진 타워가 생성된다. 기본 공격 goal을 그대로 쓸 때는 별도 엔티티나 goal을 새로 만들 필요는 없다.

## 등록 예시

```java
package kim.biryeong.semiontd.tower.catalog;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.behavior;
import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.branch;
import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.line;
import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.upgrade;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerBehavior;
import kim.biryeong.semiontd.tower.TowerFaction;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class MyProductionTowers {
    private static final TowerType LEFT_ULTIMATE = tower(
            "my_left_ultimate", "내 왼쪽 궁극 타워", 425, 220.0, 10.0, 14.0, 16, 10,
            "minecraft:iron_golem",
            List.of("직접 작성한 왼쪽 궁극 타워입니다.")
    );

    private static final TowerType LEFT_BRANCH = tower(
            "my_left_branch", "내 왼쪽 2차 타워", 175, 140.0, 9.0, 9.0, 18, 5,
            VillagerVisual.builder()
                    .profession(VillagerProfession.FLETCHER)
                    .level(2)
                    .build(),
            List.of("직접 작성한 왼쪽 2차 타워입니다."),
            List.of(upgrade("my_left_ultimate", "내 왼쪽 궁극 타워", LEFT_ULTIMATE, 250))
    );

    private static final TowerType RIGHT_ULTIMATE = tower(
            "my_right_ultimate", "내 오른쪽 궁극 타워", 425, 180.0, 12.0, 12.0, 14, 4,
            "minecraft:iron_golem",
            List.of("직접 작성한 오른쪽 궁극 타워입니다.")
    );

    private static final TowerType RIGHT_BRANCH = tower(
            "my_right_branch", "내 오른쪽 2차 타워", 175, 120.0, 11.0, 8.0, 16, 2,
            "minecraft:villager",
            List.of("직접 작성한 오른쪽 2차 타워입니다."),
            List.of(upgrade("my_right_ultimate", "내 오른쪽 궁극 타워", RIGHT_ULTIMATE, 250))
    );

    private static final TowerType STARTER = tower(
            "my_starter", "내 시작 타워", 75, 100.0, 8.0, 6.0, 20, 0,
            "minecraft:villager",
            List.of("직접 작성한 시작 타워입니다."),
            List.of(
                    upgrade("my_left_branch", "내 왼쪽 2차 타워", LEFT_BRANCH, 100),
                    upgrade("my_right_branch", "내 오른쪽 2차 타워", RIGHT_BRANCH, 100)
            )
    );

    private static final ProductionTowerBehavior STARTER_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 1.0, 0.5, 8, 0.03, 0.0, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior LEFT_BRANCH_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 1.5, 0.6, 10, 0.04, 0.0, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior LEFT_ULTIMATE_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 2.0, 0.7, 12, 0.05, 0.0, true, false, 0.0, 0.0);
    private static final ProductionTowerBehavior RIGHT_BRANCH_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.5, 0.4, 10, 0.04, 0.01, true, true, 0.0, 0.0);
    private static final ProductionTowerBehavior RIGHT_ULTIMATE_BEHAVIOR =
            behavior(TowerFaction.VILLAGER, "Emerald", 0.75, 0.5, 12, 0.05, 0.02, true, true, 0.0, 0.0);

    private MyProductionTowers() {
    }

    public static void register() {
        ProductionTowerCatalog.registerLine(line(
                STARTER,
                STARTER_BEHAVIOR,
                branch(LEFT_BRANCH, LEFT_BRANCH_BEHAVIOR, LEFT_ULTIMATE, LEFT_ULTIMATE_BEHAVIOR),
                branch(RIGHT_BRANCH, RIGHT_BRANCH_BEHAVIOR, RIGHT_ULTIMATE, RIGHT_ULTIMATE_BEHAVIOR)
        ));
    }
}
```

중복 ID가 등록되면 `ProductionTowerCatalog`가 예외를 던진다. 업그레이드 대상 `TowerType`은 반드시 카탈로그에 함께 등록되어야 한다.

## 명령 동작

```text
/semiontd tower list
/semiontd tower build <tower_id>
/semiontd tower upgrades
/semiontd tower upgrade <upgrade_id>
```

`/semiontd tower build <tower_id>`는 등록된 starter tower만 설치할 수 있다. 현재 기본 등록 타워가 없으므로 직접 등록하기 전에는 모든 production tower build 요청이 거절된다.

## 작성 기준

- 직접 설치 가능한 starter tower는 `ProductionTowerLine`의 `starter`로 등록한다.
- 2차/3차 타워는 `ProductionTowerBranch` 안에 들어가며 직접 설치할 수 없다.
- 직업 제한은 `ProductionTowerBehavior.faction()` 기준으로 적용된다.
- 타워별 특수 로직이 필요하면 `ProductionTower` 또는 `EntityBackedTower`를 상속한 클래스를 만들고 `line(...)` 또는 `branch(...)`에 factory를 넘긴다.
- factory는 `ProductionTowerCatalog.TowerFactory` 형태이며, 반환 타입은 `EntityBackedTower`다. 기본값은 `ProductionTower::new`이다.
- 1차/2차/3차가 서로 다른 런타임 클래스여도 각 tier의 factory가 해당 `TowerType`에 맞는 `EntityBackedTower` 구현을 반환하면 등록할 수 있다.
- 외형은 기존 `entityTypeId`/`blockbenchModelId` 생성자 또는 `EntityVisual`로 지정한다. 둘 다 비우면 기본 villager fallback을 쓴다.
- `blockbenchModelId`가 있으면 BIL 모델이 우선이며, 바닐라 tracked data property는 적용하지 않는다.
- `TowerType.description`은 타워 상세/빌드/업그레이드 tooltip에서 MiniMessage로 파싱된다. 예: `List.of("<green>처치 시</green> 주변에 피해를 줍니다.")`

커스텀 프로덕션 타워 예시:

```java
public final class SupporterTower extends EntityBackedTower {
    public SupporterTower(
            TowerType type,
            ProductionTowerBehavior behavior,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onAttack(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount,
            boolean killedTarget
    ) {
        // 특수 효과를 여기서 적용한다.
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof SupporterTower supporterTower) {
            // 업그레이드 후에도 유지할 영구 보너스 값을 복사한다.
        }
    }
}
```

카탈로그 등록 시:

```java
line(STARTER, STARTER_BEHAVIOR, SupporterTower::new, leftBranch, rightBranch);
```

## 바닐라 엔티티 외형 속성

바닐라 엔티티 variant/tracked data는 typed visual builder로 지정한다. 문자열 key/value를 직접 쓰는 방식은 오타가 컴파일에서 잡히지 않으므로 타워 작성 코드에서는 쓰지 않는다.

```java
VillagerVisual.builder()
        .profession(VillagerProfession.FLETCHER)
        .level(2)
        .build();

WolfVisual.builder()
        .variant(WolfVariants.ASHEN)
        .soundVariant(WolfSoundVariants.CLASSIC)
        .collarColor(DyeColor.RED)
        .tame(true)
        .build();
```

지원 builder:

| 엔티티 | builder |
|---|---|
| `minecraft:villager`, `minecraft:zombie_villager` | `VillagerVisual.builder()`, `VillagerVisual.zombieBuilder()` |
| `minecraft:cow` | `CowVisual.builder()` |
| `minecraft:pig` | `PigVisual.builder()` |
| `minecraft:chicken` | `ChickenVisual.builder()` |
| `minecraft:wolf` | `WolfVisual.builder()` |
| `minecraft:cat` | `CatVisual.builder()` |
| `minecraft:frog` | `FrogVisual.builder()` |
| `minecraft:horse` | `HorseVisual.builder()` |
| `minecraft:llama`, `minecraft:trader_llama` | `LlamaVisual.builder()`, `LlamaVisual.traderBuilder()` |
| `minecraft:fox` | `FoxVisual.builder()` |
| `minecraft:rabbit` | `RabbitVisual.builder()` |
| `minecraft:parrot` | `ParrotVisual.builder()` |
| `minecraft:axolotl` | `AxolotlVisual.builder()` |
| `minecraft:mooshroom` | `MooshroomVisual.builder()` |
| `minecraft:salmon` | `SalmonVisual.builder()` |
| `minecraft:tropical_fish` | `TropicalFishVisual.builder()` |
| `minecraft:sheep` | `SheepVisual.builder()` |

## 검증

현재 GameTest 기준:

- 프로덕션 직업 3개는 계속 등록된다.
- 기본 `ProductionTowerCatalog`는 비어 있다.
- 빈 카탈로그에서는 tower list/build UI가 production tower를 노출하지 않는다.
- `ProductionTower`의 스택, 스플래시, 업그레이드 에러 처리는 테스트 fixture로 검증한다.
- `TestTower`와 `ProductionTower`는 모두 `SemionTowerEntity`를 사용한다.
- `./gradlew runGameTest --console=plain --no-daemon` 기준 `All 110 required tests passed :)`를 확인했다.
