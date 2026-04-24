# 세미온 TD 맵 제작 가이드

이 문서는 Nucleoid Creator Tools로 `semion-td:arena` MapTemplate을 만들 때 지켜야 할 region 규칙을 정리한다.

현재 구조는 팀마다 별도 Fantasy 월드를 생성하고, 같은 MapTemplate을 각 팀 월드에 복제한다. 따라서 맵 region 안에는 팀 정보를 넣지 않는다. 팀 구분은 서버가 `RED`, `BLUE`, `GREEN`, `YELLOW` 팀별 월드로 처리한다.

## 템플릿 위치

기본 `map.json` 기준 템플릿 id는 다음과 같다.

```text
semion-td:arena
```

리소스 파일 위치는 다음 경로를 사용한다.

```text
src/main/resources/data/semion-td/map_template/arena.nbt
```

## 필수 Region

MapTemplate에는 아래 marker가 반드시 있어야 한다.

```text
semion:team_spawn
semion:boss_spawn
semion:lane_spawn
semion:lane_path
semion:final_defense_tower
```

`semion:lane_spawn`과 `semion:lane_path`는 lane별로 1개씩 필요하다. 현재 한 팀은 최대 5개 lane을 가지므로, lane 1부터 lane 5까지 모두 만들어야 한다.

`semion:final_defense_tower`도 lane별로 1개씩 필요하다. 이 region은 최종 방어선에서 해당 lane 타워들이 배치될 7x7 슬롯 영역이다.

꺾이는 라인은 아래 marker를 추가로 사용한다.

```text
semion:lane_waypoint
```

`semion:lane_waypoint`는 필수는 아니지만, 11시, 1시, 5시, 7시처럼 90도로 돌아서 보스 쪽으로 들어와야 하는 라인에는 만들어야 한다.

## Region 데이터

`semion:team_spawn`

```text
data 없음
```

플레이어가 팀 월드에 들어올 때 사용할 위치다. region의 `centerBottom`이 기준 좌표가 된다.

`semion:boss_spawn`

```text
data 없음
```

보스 위치이자 enemy 이동 경로의 끝점이다. region의 `centerBottom`이 기준 좌표가 된다.

`semion:lane_spawn`

```text
data:
  lane: int
```

enemy가 처음 생성되는 위치다. `lane`은 문자열이 아니라 NBT 정수여야 한다.

예시:

```text
marker = "semion:lane_spawn"
data.lane = 1
```

`semion:lane_path`

```text
data:
  lane: int
```

해당 lane의 라인, 전투 구역, 타워 배치 구역을 겸한다. `lane_path`는 이동 지점이 아니라 전투와 배치 가능 영역 판정에 쓰기 위한 bounds다.

예시:

```text
marker = "semion:lane_path"
data.lane = 1
```

`semion:lane_waypoint`

```text
data:
  lane: int
  order: int
```

enemy가 꺾어서 이동해야 하는 지점이다. region의 `centerBottom`을 이동 지점으로 사용한다. 같은 lane의 waypoint는 `order`가 작은 순서대로 통과한다.

예시:

```text
marker = "semion:lane_waypoint"
data.lane = 1
data.order = 0
```

`semion:final_defense_tower`

```text
data:
  lane: int
```

최종 방어선에서 타워가 배치될 7x7 영역이다. region의 바닥 면 전체를 slot으로 사용한다. 시스템은 이 바닥 블록들을 `boss_spawn`에 가까운 순서대로 정렬해서, 레인 클리어 시 lane의 타워를 그 순서대로 즉시 재배치한다.

예시:

```text
marker = "semion:final_defense_tower"
data.lane = 1
```

## 만들지 않는 Region

현재 구조에서는 아래 region을 만들지 않는다.

```text
semion:final_defense
semion:tower_area
```

`final_defense`는 제거됐다. enemy는 path 끝에서 별도 추적되지 않고, `boss_spawn` 지점까지 이동한 뒤 보스에 도달한 것으로 처리된다.

`tower_area`도 제거됐다. 타워 배치 가능 영역은 각 lane의 `semion:lane_path` bounds를 사용한다.

주의: `semion:final_defense`는 여전히 사용하지 않지만, `semion:final_defense_tower`는 별도로 필요하다.

## H형 맵 기준

현재 맵은 H 형태를 기준으로 잡는다.

- 12시 방향을 lane 쪽으로 본다.
- 6시 방향을 boss 위치로 본다.
- 각 lane은 12시 쪽에서 시작해 6시 boss 쪽으로 연결되도록 만든다.
- `semion:lane_spawn`은 각 lane의 시작점에 작게 잡는다.
- `semion:lane_path`는 해당 lane의 실제 전투 구역 전체를 감싸게 잡는다.
- 11시, 1시, 5시, 7시 방향에서 들어오는 lane은 꺾이는 지점에 `semion:lane_waypoint`를 둔다.
- `semion:boss_spawn`은 6시 방향 보스가 서야 할 위치에 잡는다.
- `semion:final_defense_tower`는 보스 근처 최종 방어선의 타워 집결 7x7 영역으로 잡는다.

몬스터는 다음처럼 이동한다.

```text
semion:lane_spawn centerBottom
-> semion:lane_waypoint centerBottom, order 순서
-> semion:boss_spawn centerBottom
```

12시에서 바로 내려오는 중앙 lane은 waypoint를 생략해도 된다. 반대로 11시, 1시, 5시, 7시 라인은 맵 길이 꺾이는 위치에 waypoint를 넣어야 enemy가 벽이나 빈 공간을 직선으로 가로지르지 않는다.

## 권장 Region 크기

`semion:team_spawn`

- 플레이어 여러 명이 겹치지 않게 3x3 이상 권장
- 나중에 lane별 스폰으로 확장할 수 있으므로 주변 여유 공간 확보

`semion:boss_spawn`

- 보스 크기를 고려해 3x3 이상 권장
- 6시 방향 중심에 배치
- 모든 lane에서 이동해 온 enemy가 시각적으로 보스에게 도달한다고 느껴지는 위치에 배치

`semion:final_defense_tower`

- lane마다 7x7 권장
- 바닥 면 전체가 슬롯으로 사용되므로, 타워가 설 수 있는 바닥 블록으로 채운다
- 보스와 겹치지 않게 두되, leaked enemy와 교전할 만큼 충분히 가깝게 둔다

`semion:lane_spawn`

- 1x1부터 3x3 정도 권장
- lane마다 하나씩 생성
- enemy가 처음 나타날 바닥 위치를 포함

`semion:lane_path`

- 해당 lane의 전투/타워 배치 가능 영역 전체를 감싼다
- 다른 lane과 겹치지 않게 만든다
- tower가 설치될 수 있는 바닥 영역을 충분히 포함한다
- enemy 이동 경로가 이 영역을 크게 벗어나지 않게 만든다

`semion:lane_waypoint`

- 1x1부터 3x3 정도 권장
- lane이 꺾이는 지점마다 하나씩 배치
- 같은 lane 안에서는 `order`가 겹치지 않게 배치
- 중앙 직선 lane에는 없어도 된다

## Lane 번호 규칙

lane 번호는 1부터 5까지 사용한다.

```text
lane = 1
lane = 2
lane = 3
lane = 4
lane = 5
```

각 lane마다 아래 두 region이 반드시 있어야 한다.

```text
semion:lane_spawn lane=1
semion:lane_path lane=1
```

```text
semion:lane_spawn lane=2
semion:lane_path lane=2
```

5번 lane까지 동일하게 만든다.

꺾이는 lane에는 waypoint를 추가한다.

```text
semion:lane_waypoint lane=1 order=0
semion:lane_waypoint lane=1 order=1
```

필요한 만큼 추가할 수 있다. 이동 순서는 `order` 기준이다.

## 실수 방지 체크리스트

- `lane`을 문자열 `"1"`로 넣지 않는다. 반드시 NBT int `1`로 넣는다.
- `team` 데이터는 넣지 않는다.
- `order` 데이터는 `semion:lane_waypoint`에만 넣는다.
- `semion:lane_path`는 lane마다 하나만 만든다.
- `semion:lane_spawn`은 lane마다 하나만 만든다.
- `semion:lane_waypoint`는 필요한 lane에만 만들고, 같은 lane 안에서 `order`를 중복시키지 않는다.
- `semion:boss_spawn`은 템플릿당 하나만 만든다.
- `semion:final_defense_tower`는 lane마다 하나씩 만든다.
- `semion:team_spawn`은 템플릿당 하나만 만든다.
- `semion:final_defense`는 만들지 않는다.
- `semion:tower_area`는 만들지 않는다.
- marker id 오타를 주의한다. 예를 들어 `semion:lane_paths`, `semion:line_path`, `semion:lane`은 인식되지 않는다.

## 현재 코드가 읽는 값

현재 구현은 다음 값만 읽는다.

```text
semion:team_spawn.centerBottom
semion:boss_spawn.centerBottom
semion:lane_spawn[data.lane].centerBottom
semion:lane_path[data.lane].bounds
semion:lane_waypoint[data.lane, data.order].centerBottom
semion:final_defense_tower[data.lane].bounds
```

이 외의 NBT 데이터는 현재 사용하지 않는다.
