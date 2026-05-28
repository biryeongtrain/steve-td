# 프로덕션 타워 카탈로그

## 현재 상태

기본 reload 기준으로 주민/언데드/동물 프로덕션 타워가 등록된다.

`ProductionTowerCatalogs.reloadBuiltIns(...)`는 villager, undead, animal built-in 카탈로그를 다시 등록한다. 현재 starter family는 주민 4개, 언데드 3개, 동물 2개이며, `/semiontd tower list`와 빌드 UI는 선택한 직업이 허용하는 starter tower만 보여준다.

## 패키지 구조

```text
src/main/java/kim/biryeong/semiontd/tower/
  Tower.java                       모든 타워의 공통 상태/훅
  EntityBackedTower.java         엔티티를 가진 타워의 공용 생명주기
  ProductionTowerCatalog.java      외부 API와 registry
  ProductionTower.java             런타임 프로덕션 타워

src/main/java/kim/biryeong/semiontd/entity/tower/
  SemionTowerEntity.java           런타임 타워 엔티티
  goal/TowerAttackMonsterGoal.java 타워 공격/추적 goal

src/main/java/kim/biryeong/semiontd/tower/catalog/
  ProductionTowerDefinitions.java  tower/upgrade helper

src/main/java/kim/biryeong/semiontd/tower/villager/
src/main/java/kim/biryeong/semiontd/tower/undead/
src/main/java/kim/biryeong/semiontd/tower/animal/
  *TowerCatalogs.java              built-in 카탈로그 등록
```

새 기본 타워를 만들 때는 해당 계열 패키지 아래에 카탈로그 클래스를 만들고, 타워 타입을 먼저 등록한 뒤 등록된 타입끼리 업그레이드 링크를 만든다.
작성한 `register()` 메서드는 `ProductionTowerCatalogs.reloadBuiltIns(...)` 흐름에서 호출해야 한다.

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
- `ProductionTower`는 catalog entry가 기본 생성하는 실전용 런타임 타워다. 커스텀 런타임은 `ProductionTower`를 상속해도 되고, 특수 로직이 필요하면 `EntityBackedTower`를 직접 상속해도 된다.
- `SemionTowerEntity`는 테스트/프로덕션 양쪽이 공유하는 엔티티다. 엔티티 타입 ID는 `semion-td:tower`이며, 예전 `SemionTestTowerEntity`/`test_tower` 전용 구조는 쓰지 않는다.
- `TowerAttackMonsterGoal`은 `SemionTowerEntity`에서 동작한다. 공격 피해/공격 속도 조정이 필요하면 개별 `Tower` 훅을 override한다.
- 업그레이드 서비스는 기존 타워를 `ProductionTower`로 캐스팅하지 않고 일반 `Tower` 상태(`type`, `ownerPlayer`, `position`)로 판정한다. 업그레이드 대상 생성은 catalog entry의 factory가 담당한다.
- 업그레이드로 새 타워 인스턴스를 만들면 `Tower.copyFrom(previousTower, upgradeCost)`가 호출된다. 기본 판매/라운드 상태는 공통으로 복사되고, 영구 스택 같은 타워별 런타임 상태는 `copyRuntimeStateFrom(...)`을 override해 복사한다.

따라서 새 프로덕션 카탈로그만 추가하면 기본적으로 `semion-td:tower` 엔티티를 가진 타워가 생성된다. 기본 공격 goal을 그대로 쓸 때는 별도 엔티티나 goal을 새로 만들 필요는 없다.

## 등록 예시

```java
package kim.biryeong.semiontd.tower.catalog;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.upgrade;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
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

    private MyProductionTowers() {
    }

    public static void register() {
        ProductionTowerCatalog.registerStarter(STARTER);
        ProductionTowerCatalog.register(LEFT_BRANCH);
        ProductionTowerCatalog.register(LEFT_ULTIMATE, 3);
        ProductionTowerCatalog.register(RIGHT_BRANCH);
        ProductionTowerCatalog.register(RIGHT_ULTIMATE, 3);

        ProductionTowerCatalog.linkUpgrade(STARTER, "my_left_branch", "내 왼쪽 2차 타워", LEFT_BRANCH, 100);
        ProductionTowerCatalog.linkUpgrade(STARTER, "my_right_branch", "내 오른쪽 2차 타워", RIGHT_BRANCH, 100);
        ProductionTowerCatalog.linkUpgrade(LEFT_BRANCH, "my_left_ultimate", "내 왼쪽 궁극 타워", LEFT_ULTIMATE, 250);
        ProductionTowerCatalog.linkUpgrade(RIGHT_BRANCH, "my_right_ultimate", "내 오른쪽 궁극 타워", RIGHT_ULTIMATE, 250);
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

`/semiontd tower build <tower_id>`는 등록된 starter tower만 설치할 수 있다. 직업이 허용하지 않는 starter tower는 카탈로그에 있어도 거절된다.

## 작성 기준

- 직접 설치 가능한 starter tower는 `registerStarter(...)`로 등록한다.
- 2차/3차 타워는 `register(type, factory, tier)`로 등록하고 starter로 표시하지 않는다.
- 타워별 특수 로직이 필요하면 `ProductionTower` 또는 `EntityBackedTower`를 상속한 클래스를 만들고 등록 시 factory를 넘긴다.
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
ProductionTowerCatalog.registerStarter(STARTER, SupporterTower::new);
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
| `minecraft:slime` | `SlimeVisual.builder()` |
| `minecraft:tropical_fish` | `TropicalFishVisual.builder()` |
| `minecraft:sheep` | `SheepVisual.builder()` |

## 검증

현재 GameTest 기준:

- `ProductionTowerCatalog.clearForTesting()` 후에는 수동 authoring fixture를 위해 카탈로그를 비울 수 있다.
- built-in reload는 주민/언데드/동물 카탈로그와 각 tower job을 등록한다.
- `ProductionTower`의 업그레이드 에러 처리는 테스트 fixture로 검증한다.
- `TestTower`와 `ProductionTower`는 모두 `SemionTowerEntity`를 사용한다.
- `./gradlew runGameTest --console=plain --no-daemon`로 검증한다.
