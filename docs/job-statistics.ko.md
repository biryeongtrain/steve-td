# 직업 통계

Semion TD는 일반 경기 결과를 직업별로 집계해 `config/semion-td/job-statistics.db`에 저장합니다. 게임 서버는 집계 결과를 메모리에 올려 두며, `/semiontd job stats` Dialog는 DB를 직접 조회하지 않습니다.

## 집계 기준

- 참가 플레이어 1명이 한 경기에서 사용한 직업을 선택 1회로 계산합니다.
- 같은 경기에서 같은 직업을 여러 명이 사용하면 각 플레이어를 따로 집계합니다.
- `MatchMode.NORMAL` 경기만 포함합니다. 테스트와 샌드박스 경기는 제외합니다.
- 승률은 `승리한 참가 기록 / 전체 참가 기록`입니다.
- 선택률은 `해당 직업 참가 기록 / 전체 직업 참가 기록`입니다.
- 평균 순위는 팀 최종 순위가 기록된 참가 기록만 사용합니다.
- 평균 최종 라운드와 세부 수치는 참가 기록 1건당 평균입니다.
- 라운드 시작 때 실제 웨이브가 배정된 빌더 라인만 해당 라운드의 시도 표본으로 기록합니다.
- 개별 빌더 통과는 자기 라인의 웨이브가 해결되고, 보스 누수가 없으며, 자기 라인의 타워가 전부 파괴돼 최종 방어선 전투로 넘어가지 않은 경우에만 기록합니다.
- 라운드 통과율은 `해당 라운드를 통과한 빌더 표본 / 해당 라운드를 실제로 시도한 빌더 표본`입니다. 아직 그 라운드에 도달하지 못한 경기는 분모에 넣지 않습니다.
- 팀의 승리, 팀 생존, 최종 순위만으로 라운드 통과를 추정하지 않습니다. 한 팀 안에서도 각 빌더의 라인 결과가 다르면 통과 기록도 다르게 남습니다.
- 라운드 통과율은 1~40라운드만 집계합니다. 40라운드를 넘긴 개인 기록은 R40까지 반영합니다.
- 특성 조합은 `jobId`, 주특성 ID와 버전, 부특성 ID와 버전의 순서 있는 조합으로 집계합니다. `(A, B)`와 `(B, A)`는 서로 다릅니다.
- 특성이 비활성화됐거나 특성 필드가 없던 기록은 `(none v0, none v0)`으로 집계합니다.
- 최종 타워 구성은 경기 종료 직전 살아 있던 타워를 종류와 티어별로 묶어 기록합니다. 파괴하거나 판매한 타워와 설치 순서는 추정하지 않습니다.

직업 ID를 저장하기 전에 생성된 경기 기록은 직업을 확인할 수 없어 제외합니다. 플레이어 프로필의 현재 직업으로 과거 기록을 추정하지 않습니다. 개인 라운드 결과를 저장하기 전의 기록은 전체 직업 통계에는 남을 수 있지만 라운드 통과율 표본에는 넣지 않습니다.

## 표시 지표

요약 Dialog에는 선택 수, 선택률, 승률, 평균 순위, 평균 최종 라운드와 R10·R20·R30·R40 통과율이 나옵니다. 직업 버튼을 누르면 R1~R40 통과율, 특성 조합 통계와 다음 세부 지표를 확인할 수 있습니다.

- 전투: 처치 수, 처치 미네랄
- 인컴·소환: 소환 수, 최종 인컴, 생성 인컴, 보낸 위협, 공격 성공 위협, 받은 인컴 위협
- 라인 방어·지원: 라인 유입 위협, 누수 위협, 본인 라인 다이아, 지원 다이아, 지원 정리 위협

특성 조합은 표본이 많은 순서로 최대 8개를 표시합니다. 각 조합에는 선택 수와 선택률, 승률, 평균 순위, 평균 최종 라운드, R10·R20·R30·R40 통과율과 가장 많이 사용한 최종 타워 3종을 표시합니다.

방어율은 다음 식으로 계산합니다.

```text
1 - 누수 위협 합계 / 라인 유입 위협 합계
```

인컴 공격 성공률은 다음 식으로 계산합니다.

```text
공격 성공 위협 합계 / 보낸 인컴 위협 합계
```

분모가 0이면 `0%`가 아니라 `-`로 표시합니다.

## 비동기 처리

경기 종료 시 메인 스레드는 저장된 `MatchResult`를 용량 256의 작업 큐에 넣고 즉시 다음 처리를 계속합니다. 과거 기록 읽기, 합산, SQLite 쓰기는 `semion-td-job-statistics` 작업 스레드에서 실행합니다.

큐가 가득 차면 경기를 기다리게 하지 않고 원본 경기 기록 재스캔을 예약합니다. 서버가 통계 쓰기 도중 종료돼도 다음 시작 시 `semiontd.db`와 `match-results.json`을 다시 읽어 누락된 기록을 복구합니다.

`job-statistics.db`는 기존 `semiontd.db`와 분리돼 있습니다. 통계 쓰기가 경기 결과, ELO, 프로필을 저장하는 SQLite writer 락을 점유하지 않습니다.

## 테이블

### `job_stat_participant_facts`

일반 경기의 참가 기록을 한 행씩 저장합니다. 기본 키는 `(match_id, player_id)`이며 같은 경기를 다시 읽어도 중복되지 않습니다.

주요 열:

- `job_id`, `team_id`, `won`, `placement`, `final_round`, `cleared_round`
- `primary_trait_id`, `primary_trait_version`, `secondary_trait_id`, `secondary_trait_version`
- `started_at_epoch_millis`, `ended_at_epoch_millis`
- `monster_kills`, `kill_minerals`, `summoned_monsters`, `final_income`
- `own_lane_incoming_threat`, `own_lane_leaked_threat`
- `sent_income_threat`, `income_attack_success_threat`, `incoming_income_threat`
- `own_lane_diamond_gain`, `assist_clear_diamond_gain`, `income_generated`, `assist_clear_threat`

### `job_statistics`

직업별 참가 행의 합계를 저장합니다. 서버 시작 시 참가 기록을 기준으로 다시 계산하므로 중간 종료로 집계가 어긋나도 복구할 수 있습니다.

평균과 비율은 이 테이블의 합계로 계산합니다. DB에는 반올림된 표시값을 저장하지 않습니다.

`cleared_round`는 이전 DB와의 호환을 위해 남겨 둔 값입니다. 라운드 통과율 계산에는 사용하지 않습니다.

### `job_stat_participant_rounds`

참가자별 라운드 시도와 결과를 저장합니다. 기본 키는 `(match_id, player_id, round_number)`입니다.

- `round_number`: 실제 웨이브가 배정된 라운드입니다.
- `cleared`: 해당 빌더가 자기 라인을 통과했으면 `1`, 아니면 `0`입니다.

### `job_stat_participant_towers`

참가자별 최종 타워 구성을 저장합니다. 기본 키는 `(match_id, player_id, tower_type_id, tier)`입니다.

- `tower_type_id`: 타워 종류 ID입니다.
- `tier`: 경기 종료 시 티어입니다.
- `count`: 같은 종류와 티어의 살아 있는 타워 수입니다.

특성 조합 통계는 참가 사실, 참가자 라운드, 참가자 타워 테이블을 SQL로 묶어 계산합니다. 별도의 중복 조합 집계 테이블을 유지하지 않으므로 원본 사실을 재스캔하거나 마이그레이션해도 같은 결과를 얻습니다.

### `job_round_statistics`

직업별 1~40라운드 시도·통과 수를 저장합니다. 기본 키는 `(job_id, round_number)`입니다.

- `attempt_count`: 해당 라운드를 실제로 시도한 빌더 수입니다.
- `cleared_count`: 그중 자기 라인을 통과한 빌더 수입니다.

통과율은 `cleared_count / attempt_count`로 계산합니다. 서버 시작 시 `job_stat_participant_rounds`를 기준으로 다시 계산합니다. 이전 팀 기반 `cleared_round`만 가진 집계 행은 자동으로 비우며, 과거 기록에서 개인 라운드 결과를 정확히 복원할 수는 없습니다.

### `job_stat_participant_round_metrics`

빌더별 라운드 전투·경제 스냅샷입니다. 기본 키는 `(match_id, player_id, round_number)`입니다.

- `wave_duration_ticks`: 웨이브 시작부터 해결까지 걸린 전체 틱입니다.
- `combat_ticks`: 소유 타워의 첫 실제 피해·피격·유효 회복부터 마지막 이벤트까지의 틱입니다. 이벤트가 없으면 `0`입니다.
- `tower_count_start`, `tower_count_end`, `tower_death_count`: 웨이브 시작·종료 수량과 사망 횟수입니다. `semion-td:demon_lord`는 이 수량에서 제외합니다.
- `emerald_production_upgrade_count`, `emerald_per_second`, `income`, `emerald_balance`, `diamond_balance`, `tower_limit_purchase_count`: 정상 라운드는 인컴 지급 뒤, 다음 준비 단계 전에 찍은 경제 상태입니다. 즉시 승리한 최종 라운드는 별도 지급 없이 종료 시점 값을 저장합니다.
- `monster_kills`: 해당 라운드에서 증가한 플레이어의 전체 처치 수입니다.

### `job_stat_participant_round_tower_metrics`

빌더·라운드·타워 종류별 전투 원자료입니다. 기본 키는 `(match_id, player_id, round_number, tower_type_id)`입니다.

- `sample_count`: 웨이브 중 한 번이라도 참여한 논리 타워 수입니다. 동적 생성 타워도 포함합니다.
- `start_count`, `end_alive_count`, `death_count`: 시작 수량, 종료 시 생존 수량, 사망 횟수입니다. 부활 후 다시 죽으면 사망 횟수도 다시 증가합니다.
- `physical_damage_dealt`, `magic_damage_dealt`, `damage_taken`, `healing_done`, `kill_count`: 실제 적용된 피해·회복과 원천 타워에 귀속된 처치입니다.
- `first_combat_tick`, `last_combat_tick`: 실제 피해·피격·유효 회복이 처음과 마지막으로 발생한 웨이브 틱입니다. 무전투면 둘 다 `-1`입니다.
- `survival_ticks`: 참여 표본의 생존 틱 합계입니다.

업그레이드는 같은 논리 타워 추적기를 이어받으므로 피해, 피격, 회복, 처치와 생존시간이 끊기지 않습니다. 타워 종류 ID는 기존 라운드 피해 집계와 같이 업그레이드 전 추적기의 종류를 유지합니다. 판매·제거·팀 탈락으로 런타임 타워가 사라져도 라운드 종료까지 추적 원자료는 남습니다. 마왕 본체는 합성 ID `semion-td:demon_lord`로 같은 테이블에 저장합니다.

DPS와 평균 생존시간은 원자료에서 다음처럼 계산합니다.

```text
전투 틱 = last_combat_tick - first_combat_tick + 1
DPS = (physical_damage_dealt + magic_damage_dealt) * 20 / 전투 틱
평균 생존시간(초) = survival_ticks / sample_count / 20
```

무전투 또는 표본이 없는 행의 계산값은 `0`으로 처리합니다. 예를 들어 라운드별 타워 DPS는 다음 SQL로 조회할 수 있습니다.

```sql
SELECT tower_type_id,
       round_number,
       SUM(physical_damage_dealt + magic_damage_dealt) * 20.0
         / NULLIF(SUM(last_combat_tick - first_combat_tick + 1), 0) AS dps,
       SUM(survival_ticks) / NULLIF(SUM(sample_count) * 20.0, 0) AS average_survival_seconds
FROM job_stat_participant_round_tower_metrics
WHERE first_combat_tick >= 0
GROUP BY tower_type_id, round_number;
```

재스캔은 참가자별 신규 라운드 행을 모두 지운 뒤 원본 경기 결과의 스냅샷을 다시 삽입합니다. 따라서 같은 원본을 반복 처리해도 중복되지 않습니다. 라운드 전투 스냅샷이 없던 과거 경기에는 추정 행을 만들지 않습니다.

## 외부 조회

향후 웹에서는 별도 API 서비스가 `job-statistics.db`를 읽기 전용으로 조회해야 합니다. Minecraft 서버에 공개 HTTP 서버를 붙이거나 브라우저에 DB 파일을 직접 제공하지 않습니다.

간단한 운영 확인은 다음처럼 할 수 있습니다.

```bash
sqlite3 -readonly config/semion-td/job-statistics.db \
  'SELECT job_id, appearances, wins FROM job_statistics ORDER BY appearances DESC;'
```

실행 중인 서버의 통계를 계속 읽을 때는 SQLite WAL 파일을 함께 사용해야 하므로 `immutable=1` 연결은 사용하지 않습니다. 외부 서비스는 테이블을 수정하지 않아야 합니다.

`job-statistics.db`는 원본 경기 기록에서 다시 만들 수 있는 파생 데이터입니다. 파일을 교체하거나 삭제해야 한다면 서버를 먼저 끄고 `job-statistics.db`, `job-statistics.db-wal`, `job-statistics.db-shm`을 같은 시점에 다룹니다.
