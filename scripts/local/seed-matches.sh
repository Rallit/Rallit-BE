#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )) || { (( $# == 1 )) && [[ ! "$1" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; }; then
    printf 'Usage: %s [YYYY-MM-DD]\n' "$0" >&2
    exit 2
fi

container=rallit-local-postgres-1
database=rallit_match_discovery
labels=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}/{{ index .Config.Labels "com.docker.compose.service" }}' "$container")
if [[ "$labels" != rallit-local/postgres ]]; then
    printf 'Expected the rallit-local PostgreSQL Compose container.\n' >&2
    exit 1
fi

script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
demo_date=${1:-$(docker exec "$container" sh -c 'exec psql -XAt -U "$POSTGRES_USER" -d "$1" -c "SELECT (CURRENT_TIMESTAMP AT TIME ZONE '\''Asia/Seoul'\'')::date + 1"' sh "$database")}
{
    printf 'BEGIN;\n'
    printf "SET LOCAL rallit.demo_date = '%s';\n" "$demo_date"
    cat "$script_dir/seed-matches.sql"
    printf 'COMMIT;\n'
} | docker exec -i "$container" sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' sh "$database"

printf 'Seeded demo matches for %s (Asia/Seoul) in %s.\n' "$demo_date" "$database"
