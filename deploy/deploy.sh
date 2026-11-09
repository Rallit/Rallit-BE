#!/usr/bin/env bash
set -euo pipefail

image="${1:-}"
if [[ ! "$image" =~ ^[A-Za-z0-9._/:-]+:[A-Za-z0-9._-]+$ ]]; then
  echo "사용법: bash deploy.sh <이미지>:<태그>" >&2
  exit 1
fi

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "$PWD/.env 파일이 없습니다. docs/deployment.md의 EC2 최초 배포를 먼저 진행하세요." >&2
  exit 1
fi
if [[ "$(stat -c %a .env)" != "600" ]]; then
  echo "경고: .env 권한이 600이 아닙니다. chmod 600 .env 를 실행하세요." >&2
fi

history_file=deploy-history.log
images_to_keep=3

previous=""
if [[ -f "$history_file" ]]; then
  previous="$(awk '{ last = $2 } END { print last }' "$history_file")"
fi
export APP_IMAGE="$image"

remove_old_images() {
  local keep candidates candidate
  keep="$(awk -v limit="$images_to_keep" '
    { images[NR] = $2 }
    END {
      for (i = NR; i > 0 && kept < limit; i--) {
        if (!seen[images[i]]++) {
          print images[i]
          kept++
        }
      }
    }' "$history_file")" || return 1
  candidates="$(docker image ls --format '{{.Repository}}:{{.Tag}}' "${image%:*}")" || return 1
  for candidate in $candidates; do
    if [[ "$candidate" != *:"<none>" ]] && ! grep -qxF "$candidate" <<< "$keep"; then
      docker image rm "$candidate" > /dev/null || echo "경고: $candidate 이미지를 삭제하지 못했습니다." >&2
    fi
  done
}

docker compose config --quiet
docker compose pull --quiet app

services="$(docker compose config --services)"
if grep -qx db <<< "$services"; then
  echo "db 컨테이너가 정상 상태인지 확인합니다."
  docker compose up -d --wait db
fi

echo "$image 이미지로 app을 교체합니다."
if ! docker compose up -d --wait --wait-timeout 180 --no-deps app; then
  echo "배포 실패: app이 정상 상태가 되지 않았습니다. 최근 로그를 출력합니다." >&2
  docker compose logs --tail 100 app >&2 || true
  if [[ -z "$previous" || "$previous" == "$image" ]]; then
    echo "자동 복구 불가: $history_file 에 되돌릴 이전 이미지가 없습니다." >&2
  elif APP_IMAGE="$previous" docker compose up -d --wait --wait-timeout 180 --no-deps app; then
    echo "자동 복구 성공: $previous 이미지로 되돌렸습니다. $image 배포는 실패입니다." >&2
  else
    echo "자동 복구 실패: $previous 이미지도 정상 상태가 되지 않았습니다. 즉시 확인이 필요합니다." >&2
    docker compose logs --tail 100 app >&2 || true
  fi
  exit 1
fi

if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$image|" .env
else
  echo "APP_IMAGE=$image" >> .env
fi
echo "$(date -u +%FT%TZ) $image" >> "$history_file"

remove_old_images || echo "경고: 이전 이미지 정리에 실패했습니다. 배포는 성공했습니다." >&2

docker compose ps
echo "배포 성공: $image"
