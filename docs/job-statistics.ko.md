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

직업 ID를 저장하기 전에 생성된 경기 기록은 직업을 확인할 수 없어 제외합니다. 플레이어 프로필의 현재 직업으로 과거 기록을 추정하지 않습니다. 개인 라운드 결과를 저장하기 전의 기록은 전체 직업 통계에는 남을 수 있지만 라운드 통과율 표본에는 넣지 않습니다.

## 표시 지표

요약 Dialog에는 선택 수, 선택률, 승률, 평균 순위, 평균 최종 라운드와 R10·R20·R30·R40 통과율이 나옵니다. 직업 버튼을 누르면 R1~R40 통과율과 다음 세부 지표를 확인할 수 있습니다.

- 전투: 처치 수, 처치 미네랄
- 인컴·소환: 소환 수, 최종 인컴, 생성 인컴, 보낸 위협, 공격 성공 위협, 받은 인컴 위협
- 라인 방어·지원: 라인 유입 위협, 누수 위협, 본인 라인 다이아, 지원 다이아, 지원 정리 위협

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

### `job_round_statistics`

직업별 1~40라운드 시도·통과 수를 저장합니다. 기본 키는 `(job_id, round_number)`입니다.

- `attempt_count`: 해당 라운드를 실제로 시도한 빌더 수입니다.
- `cleared_count`: 그중 자기 라인을 통과한 빌더 수입니다.

통과율은 `cleared_count / attempt_count`로 계산합니다. 서버 시작 시 `job_stat_participant_rounds`를 기준으로 다시 계산합니다. 이전 팀 기반 `cleared_round`만 가진 집계 행은 자동으로 비우며, 과거 기록에서 개인 라운드 결과를 정확히 복원할 수는 없습니다.

## 외부 조회

향후 웹에서는 별도 API 서비스가 `job-statistics.db`를 읽기 전용으로 조회해야 합니다. Minecraft 서버에 공개 HTTP 서버를 붙이거나 브라우저에 DB 파일을 직접 제공하지 않습니다.

간단한 운영 확인은 다음처럼 할 수 있습니다.

```bash
sqlite3 -readonly config/semion-td/job-statistics.db \
  'SELECT job_id, appearances, wins FROM job_statistics ORDER BY appearances DESC;'
```

실행 중인 서버의 통계를 계속 읽을 때는 SQLite WAL 파일을 함께 사용해야 하므로 `immutable=1` 연결은 사용하지 않습니다. 외부 서비스는 테이블을 수정하지 않아야 합니다.

`job-statistics.db`는 원본 경기 기록에서 다시 만들 수 있는 파생 데이터입니다. 파일을 교체하거나 삭제해야 한다면 서버를 먼저 끄고 `job-statistics.db`, `job-statistics.db-wal`, `job-statistics.db-shm`을 같은 시점에 다룹니다.
