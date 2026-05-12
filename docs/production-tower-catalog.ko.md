# 프로덕션 타워 카탈로그

## 설계 수렴 요약

두 agent를 생성자/비평자 역할로 두고 20회 이상 반복 검토한 결론은 다음과 같다.

- 경쟁형 income TD에서는 몹팩 대응이 필수이므로 대부분의 타워가 스플래시를 가져야 한다.
- 전역 자원, 전역 영혼, 이동형 타워, 타워별 소환수는 1차 구현 범위에서 제외한다. 기존 lane/tower reset/final defense 흐름과 충돌하거나 snowball 위험이 크다.
- 팩션 특색은 tower-local capped stack으로 둔다. GameTest로 검증 가능하고, HUD/명령 출력에서 설명하기 쉽다.
- 1차 구현은 세 팩션, 각 3개 타워로 제한한다.

## 직업

### 주민 기술자

ID: `semion-td:villager_engineer`

주민 타워만 사용할 수 있다. 주민 타워는 `Emerald` 스택으로 전투가 길어질수록 피해량이 오른다.

### 언데드 강령술사

ID: `semion-td:undead_necromancer`

언데드 타워만 사용할 수 있다. 언데드 타워는 `Decay` 스택과 죽음 폭발로 몹팩을 연쇄 정리한다.

### 동물 조련사

ID: `semion-td:beast_tamer`

동물 타워만 사용할 수 있다. 동물 타워는 `Rage` 스택으로 교전 중 공격 속도와 압박 대응력이 오른다.

## 명령

```text
/semiontd job list
/semiontd job select villager_engineer
/semiontd tower list
/semiontd tower build villager_crossbow_post
```

`/semiontd tower build <id>`는 현재 위치가 자신의 lane area 안이고, 현재 직업이 해당 팩션 타워를 허용하며, 다이아가 충분할 때 타워를 설치한다.

## 타워

| 팩션 | ID | 이름 | 역할 |
| --- | --- | --- | --- |
| 주민 | `villager_crossbow_post` | 주민 쇠뇌 초소 | 저렴한 시작 타워, 작은 스플래시, 킬 기반 Emerald 성장 |
| 주민 | `villager_bell_mortar` | 주민 종 포대 | 주력 스플래시, 공격 기반 Emerald 성장 |
| 주민 | `villager_emerald_lens` | 주민 에메랄드 렌즈 | 장거리 보스/누수 대응, 작은 스플래시 |
| 언데드 | `undead_bone_spitter` | 언데드 뼈 발사기 | 저렴한 시작 타워, 뼈 파편 스플래시 |
| 언데드 | `undead_grave_bombard` | 언데드 묘지 폭격기 | 주력 광역 타워, Decay 죽음 폭발 강화 |
| 언데드 | `undead_soul_reaper` | 언데드 영혼 수확자 | 보스/고체력 대응, Decay 누적 피해 |
| 동물 | `beast_wolf_den` | 동물 늑대 소굴 | 빠른 시작 타워, Rage로 공격 속도 증가 |
| 동물 | `beast_boar_crasher` | 동물 멧돼지 돌격대 | 짧은 사거리 광역 bruiser |
| 동물 | `beast_hawk_roost` | 동물 매 둥지 | 장거리 anti-leak, 작은 스플래시 |

## 검증

- 프로덕션 직업 3개와 9개 타워 등록 GameTest.
- 직업별 타워 허용/거부 GameTest.
- 스플래시 타워가 packed monsters를 동시에 피해 입히는 GameTest.
- 주민 타워가 교전 후 Emerald 스택을 얻고 피해 배율이 증가하는 GameTest.
