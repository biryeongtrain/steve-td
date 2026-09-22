# 벌레 빌더 자폭 리워크

전투 벌레 9종은 사망 시 아래 티어별 범위의 적에게 사망 당시 최대 체력에 비례한 마법 피해를 입힌다. 범위 내 피해 감쇠는 없으며, 스포너 연결 여부와 관계없이 생명마다 한 번 폭발한다. 스포너 자체와 판매·업그레이드·라운드 정리는 폭발을 발생시키지 않는다.

| 티어 | 최대 체력 비례 피해 | 폭발 반경 |
|---|---:|---:|
| 1티어 | 25% | 2블록 |
| 2티어 | 40% | 3블록 |
| 3티어 | 50% | 3블록 |

초반 폭발 범위를 제한하는 C안의 테스트 수치다. 가격·기본 체력·판매 환급·업그레이드 비용은 유지한다. 첫 배치 강화 상태의 1티어 좀벌레·동굴거미·벌의 첫 폭발 기본 피해는 각각 45·75·25다.

좀벌레·거미는 기존 공격을 유지한다. 벌 계열은 평타 없이 접근하고 중심 거리 1.5블록 이내에서 자폭한다. 사거리 버프는 접촉 거리를 늘리지 않는다. 최종 방어에서는 기존처럼 자리를 지키다가 적이 접촉하면 자폭한다.

새 1티어의 첫 웨이브에는 부활 후에도 최대 체력 ×2·받는 피해 ×3을 적용한다. 평타 강화는 없다. 부활을 n번 완료하면 최대 체력은 체력 버프를 포함해 ×0.95ⁿ, 받는 피해는 ×(1 + 0.2n)이 된다. 거미 피해 감소와 첫 웨이브 ×3은 이 배율에 곱한다. 최대 체력은 최소 1이며, 부활 시 감소한 최대 체력까지 회복한다. 다음 라운드에 강화와 부활 누적 상태를 초기화한다.

| 계열 | 첫 부활 | 두 번째 | 세 번째 | 이후 증가 |
|---|---:|---:|---:|---:|
| 벌 계열 | 2초 | 3.5초 | 5초 | +1.5초 |
| 좀벌레·거미 계열 | 6초 | 9초 | 12초 | +3초 |

## 기존 서버 설정 적용

이 변경은 운영 서버 설정이나 실행 중인 서버에 자동 배포되지 않는다. `config/semion-td/tower_balance.json`의 기존 값은 누락 기본값 보완으로 덮어쓰지 않으므로, 배포 시 아래 값을 함께 확인해야 한다. 다른 빌더의 설정은 변경하지 않는다.

`abilities.insect_global`:

| 키 | 리워크 기본값 |
|---|---:|
| freshPowerMultiplier | 2.0 |
| freshDamageTakenMultiplier | 3.0 |
| reviveBaseTicks | 120 |
| reviveIncrementTicks | 60 |
| beeReviveBaseTicks | 40 |
| beeReviveIncrementTicks | 30 |
| reviveHealthLossRatio | 0.05 |
| deathDamageTakenPerStack | 0.2 |
| tier1DeathExplosionHealthRatio | 0.25 |
| tier2DeathExplosionHealthRatio | 0.4 |
| deathExplosionHealthRatio | 0.5 |
| tier1DeathExplosionRadius | 2.0 |
| deathExplosionRadius | 3.0 |
| contactDetonationRange | 1.5 |

기존 `deathExplosionHealthRatio` 키는 3티어 피해 계수, `deathExplosionRadius` 키는 2·3티어 반경에 사용한다. 1·2티어 피해 계수와 1티어 반경은 새 키로 각각 조정한다. 누락된 새 키는 C안 기본값으로 보완하고, 이미 설정된 값은 유지한다.

기존 `freshPowerMultiplier=1.75`와 `reviveBaseTicks=80`을 그대로 두면 새 강화·근접 부활 기본값이 적용되지 않는다. `insect_bee_t1`, `insect_bee_t2`, `insect_bee_t3`의 `towers` 항목도 `damage=0`, `range=1.5`로 맞춘다. 기존 설정에 평타 수치가 남아 있어도 벌의 실제 행동은 접촉 자폭이지만, 카탈로그 표시까지 일치시키려면 이 값을 갱신해야 한다.

## 검증

자폭은 전용 `insect_explosion` 스타일을 사용한다. 연두색 범위 테두리와 중심 폭발핵, 바깥으로 뻗는 보라색 곡선 6개와 연두색 파편으로 표현한다. 테두리는 실제 피해 반경을 따르고, 입자 계획은 최대 202점으로 제한한다. 기존 광역 효과의 수신자·입자 예산을 공유하며 일반 처치 효과와 중복 재생하지 않는다.

관리자 명령 `/semiontd-debug vfx insect explosion`으로 자기 라인의 살아 있는 벌레 타워에서 효과만 미리 볼 수 있다. 이 명령은 피해·사망·부활을 발생시키지 않는다.

Java 21에서 `rtk ./gradlew test runGameTest remapJar --console=plain --no-daemon`으로 검증한다. 벌레 GameTest만 실행할 때는 `JAVA_TOOL_OPTIONS='-Dfabric-api.gametest.filter=semion-td-gametest:insect_game_test_*'`를 사용하고 실제 실행 개수가 8개인지 확인한다. 9종의 티어별 피해, 2·3블록 반경 경계와 경계 밖 제외, 부활·체력 버프·설정 리로드 및 설명 출력을 확인한다.

티어별 체력 25%·40%·50%, 반경 2·3·3블록은 밸런스 테스트 시작값이다. 서버 엔티티 이동·피해 테스트와 실제 클라이언트의 폭발 화면 확인, 실전 밸런스 검증은 별도로 구분한다.

2026-09-14 전용 VFX 변경 후 Java 21 전체 검증: 단위 테스트 1,091개 통과·2개 제외, GameTest 543개 통과, `remapJar` 성공. 9종 각각 두 번의 사망·부활 처리에서 생명당 전용 효과 1회, 티어별 반경·팔레트·발생 타워 귀속과 정리 시 미발생을 검증했다. 기존 개발자 VFX 테스트의 무작위 핫픽스 결과는 난수 시드를 고정해 재현 가능하게 했다.

클라이언트 확인은 운영 서버와 분리한 `build/insect-vfx-proof/`에서 수행한다. 1·2티어의 실제 화면 캡처와 전투 캡처는 같은 폴더에 보관한다. 이 확인은 실전 밸런스나 다인원 네트워크 부하 검증을 의미하지 않는다.
