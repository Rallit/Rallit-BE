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

previous="$(sed -n 's/^APP_IMAGE=//p' .env)"
export APP_IMAGE="$image"

docker compose config --quiet
docker compose pull --quiet app

if docker compose config --services | grep -qx db \
  && ! docker compose ps --status running --services | grep -qx db; then
  echo "db 컨테이너를 시작합니다."
  docker compose up -d --wait db
fi

echo "$image 이미지로 app을 교체합니다."
if ! docker compose up -d --wait --wait-timeout 180 --no-deps app; then
  echo "배포 실패: app이 정상 상태가 되지 않았습니다. 최근 로그를 출력합니다." >&2
  docker compose logs --tail 100 app >&2
  echo "이전 이미지로 되돌리려면: bash $PWD/deploy.sh $previous" >&2
  exit 1
fi

if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$image|" .env
else
  echo "APP_IMAGE=$image" >> .env
fi
echo "$(date -u +%FT%TZ) $image" >> deploy-history.log

docker compose ps
echo "배포 성공: $image"
