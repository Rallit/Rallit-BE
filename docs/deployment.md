# 배포 가이드

```text
외부 사용자 → https://api.rallit.co.kr → EC2 Nginx(443) → 127.0.0.1:8080 → app
                                                                      ├→ db 또는 RDS
                                                                      └→ redis:6379
```

| 워크플로 | 실행 조건 | 이미지 태그 |
| --- | --- | --- |
| `cd.yml` | `main` Push 시 자동 실행 | `<sha>` |
| `deploy-temp.yml` | Actions에서 수동 실행 | `temp-<sha>` |

두 워크플로 모두 테스트 → 이미지 Push → EC2 배포 순서로 진행되며, 테스트가 실패하면 배포하지 않습니다. `ci.yml`은 PR 검증만 하고 배포하지 않습니다.

## 1. 사전 설정

### Docker Hub

1. `rallit-backend` 저장소를 만듭니다.
2. Read & Write 권한의 Access Token을 발급합니다.
3. Private 저장소라면 EC2에서 Read-only 토큰으로 `docker login`을 한 번 실행합니다.

### GitHub

Settings → Secrets and variables → Actions에 등록합니다. DB 비밀번호 등 앱 비밀값은 EC2 `.env`에만 둡니다.

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| Secret | `EC2_HOST` | 탄력적 IP 또는 도메인 |
| Secret | `EC2_USERNAME` | SSH 사용자 (`ubuntu`, `ec2-user` 등) |
| Secret | `EC2_SSH_KEY` | 개인키 전체 내용 |
| Secret | `EC2_KNOWN_HOSTS` | 아래 명령의 출력 |
| Variable | `DOCKERHUB_USERNAME` | Docker Hub 사용자명 |
| Variable | `DOCKER_IMAGE` | `<도커허브ID>/rallit-backend` |
| Variable | `DEPLOY_PATH` | 절대 경로. 예: `/home/ubuntu/rallit` |
| Variable | `TEMP_DEPLOY_ENABLED` | 임시 배포를 허용할 때 `true` |
| Variable | `DOCKER_PLATFORM` | 선택. ARM 인스턴스만 `linux/arm64` |

```bash
ssh-keyscan -t ed25519 <EC2_HOST>                     # 로컬: 출력을 EC2_KNOWN_HOSTS에 등록
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub      # EC2: 지문 확인
ssh-keyscan -t ed25519 <EC2_HOST> | ssh-keygen -lf -  # 로컬: 위 지문과 일치해야 함
```

### 보안 그룹

| 포트 | 소스 | 비고 |
| --- | --- | --- |
| 80, 443 | `0.0.0.0/0` | Nginx |
| 22 | 키 인증만 허용한 상태로 개방 | GitHub 러너 IP가 고정이 아니므로 개인 IP로 제한하면 자동 배포가 실패합니다 |
| 8080, 5432, 6379 | 열지 않음 | app은 loopback, DB·Redis는 Docker 내부 네트워크 |

## 2. EC2 최초 배포

### 서버 상태 확인

```bash
uname -m                       # aarch64면 DOCKER_PLATFORM을 linux/arm64로 설정
free -h                        # app/DB/Redis와 OS의 메모리 예산 확인
docker compose version         # 2.20 이상 필요
flock --version                # 동시 배포 잠금에 필요
sudo ss -tlnp                  # 80, 443, 8080 사용 여부
sudo nginx -T                  # 기존 Nginx 설정
dig +short api.rallit.co.kr    # 탄력적 IP와 일치해야 함
```

Compose의 기본 메모리 한도는 app 700m + DB 256m + Redis 128m으로 합계 1084MiB이며 OS 메모리는 별도입니다. 실제 EC2 RAM을 확인하고 조정합니다. Redis 데이터 상한 `REDIS_MAXMEMORY`는 컨테이너 한도 `REDIS_MEM_LIMIT`보다 작게 둬 AOF·버퍼·프로세스 여유를 확보합니다. 아래 스왑은 필요한 경우에만 추가하며 RAM 예산을 대체하지 않습니다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### `.env` 작성

```bash
mkdir -p ~/rallit && cd ~/rallit
openssl rand -hex 32       # DB_PASSWORD용
openssl rand -hex 32       # 별도의 REDIS_PASSWORD용
openssl rand -base64 32    # JWT_SERVICE_KEY용
openssl rand -base64 32    # 별도의 JWT_ADMIN_KEY용
nano .env                 # .env.example 내용을 붙여넣고 EC2 환경에 맞게 값 설정
chmod 600 .env
```

`DB_URL`의 DB 이름은 `POSTGRES_DB`와 같아야 합니다. 볼륨이 이미 초기화된 뒤에는 `.env`를 바꿔도 DB 이름과 사용자, 비밀번호는 바뀌지 않습니다.

`.env.example` 주석에 따라 배포 프로필과 주소를 설정하고 빈 비밀값을 채웁니다. JWT 키는 각각 최소 32바이트의 독립된 Base64 키를 사용하며, 기존 배포 키와 Redis 비밀번호를 재배포마다 새로 생성하지 않습니다.

| 항목 | 서버 `.env`에 설정할 값 |
| --- | --- |
| `REDIS_PASSWORD`, `REDIS_DATABASE` | Redis 인증 비밀번호와 논리 DB 번호. 앱 접속 주소는 Compose의 `redis:6379`로 고정 |
| `REDIS_MEM_LIMIT`, `REDIS_MAXMEMORY` | 컨테이너 한도와 Redis 데이터 상한 |
| `JWT_SERVICE_KEY`, `JWT_ADMIN_KEY` | 일반·운영자 인증용 독립된 서명 키 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI` | 카카오 앱의 키 및 BE 콜백. Client Secret 활성화 앱은 해당 값 필수 |
| `GOOGLE_LOGIN_ENABLED`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Google 로그인 사용 시 활성화하고 세 자격 항목 모두 입력 |
| `FRONTEND_CALLBACK_URL`, `ADMIN_FRONTEND_CALLBACK_URL` | 배포된 일반·운영자 FE의 로그인 완료 주소 |
| `SERVICE_ORIGINS`, `ADMIN_ORIGINS` | 배포된 FE의 정확한 Origin. 여러 개면 쉼표로 구분 |

`SERVICE_ORIGINS`와 `ADMIN_ORIGINS`는 `temp`와 `prod` 모두에서 반영됩니다. 서비스·운영자 목적에 맞는 Origin만 각각 허용합니다. 로그인 완료 콜백은 별도 검증을 거치며, Secure 설정에서는 일반 FE는 `rallit.co.kr`, 운영자 FE는 `admin.rallit.co.kr`의 HTTPS(기본 포트 또는 443) 주소만 허용합니다. 다른 테스트 도메인을 콜백으로 넣으면 앱이 시작되지 않습니다.

FE 관련 항목은 서버에서 사용하는 배포 설정입니다. 개인 테스트 프론트 파일과 주소는 공유 설정에 포함하지 않습니다. 인증 쿠키는 배포 Compose에서 Secure로 강제하므로 HTTPS와 등록한 OAuth 콜백이 준비돼 있어야 합니다.

`temp`는 `application-temp.yml`을 유지하고, `prod`는 공통 `application.yml`을 사용합니다. 두 환경 모두 Flyway와 `ddl-auto: validate`를 사용합니다. 엔티티 변경에는 새 마이그레이션이 필요하며 이미 적용한 SQL을 수정하지 않습니다.


### 첫 배포

`.env`를 작성한 뒤 [3장](#3-배포-실행)의 방법으로 배포하면 선택적 DB와 Redis를 먼저 준비하고 앱을 실행합니다. 결과는 EC2에서 확인합니다.

```bash
cd ~/rallit
docker compose ps
docker compose logs --tail 100 app
docker compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --no-auth-warning ping'
curl http://127.0.0.1:8080/actuator/health
```

### Redis 동작과 데이터 보존

Redis는 `local-db` 프로필과 독립적으로 항상 실행됩니다. RDS로 전환해 DB 컨테이너를 끄더라도 Redis는 유지됩니다. 호스트 포트를 게시하지 않으며 백엔드만 Docker 네트워크로 접근합니다.

비밀번호는 컨테이너 시작 시 해시 ACL 설정으로 변환되고 Redis는 전용 사용자로 실행됩니다. 인증 없는 명령은 거절하며 헬스체크도 인증 후 수행합니다. 데이터는 `redis-data` 볼륨에 AOF로 기록하므로 Redis 컨테이너를 재생성해도 유지됩니다. `noeviction`을 사용하므로 메모리가 가득 차면 임의 세션 삭제 대신 쓰기가 실패합니다. 재시작·장애·파일 동기화 시점에 따른 데이터 손실 가능성까지 없애는 고가용성 구성은 아닙니다.

### Nginx와 HTTPS

`sudo nginx -T`에 `api.rallit.co.kr` 블록이 이미 있으면 덮어쓰지 말고 `location`만 `deploy/nginx/api.rallit.co.kr.conf`에 맞춥니다. 없으면 이 파일을 추가합니다. 위치는 Ubuntu의 경우 `sites-available`에 넣고 `sites-enabled`에 링크하고, Amazon Linux의 경우 `/etc/nginx/conf.d/`입니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.rallit.co.kr --redirect
sudo certbot renew --dry-run
```

외부 헬스체크는 `200`이어야 합니다. Swagger는 `local` 프로필에서만 허용하므로 배포된 `temp`/`prod`의 문서 경로는 공개하지 않습니다. 프론트엔드에는 별도로 관리하는 API 명세를 공유합니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://api.rallit.co.kr/actuator/health
```

## 3. 배포 실행

- **정식 배포:** `main`에 병합하면 자동으로 실행됩니다.
- **임시 배포:** Actions → **Deploy Temp** → Run workflow에서 브랜치를 선택합니다. `develop`, `release/*`, `feat/*`, `fix/*`, `chore/*`만 허용합니다.

임시 배포는 워크플로 파일이 기본 브랜치(`develop`)에 있어야 화면에 나타나고, 선택한 브랜치에도 같은 파일이 있어야 합니다. 그 전에 배포해야 하면 로컬에서 직접 진행합니다.

```bash
scp -i <키 파일> compose.yml deploy/deploy.sh <사용자>@<EC2_HOST>:~/rallit/
./gradlew test bootJar
docker login
docker buildx build --platform linux/amd64 -t <도커허브ID>/rallit-backend:temp-manual-1 --push .
ssh -i <키 파일> <사용자>@<EC2_HOST> "cd ~/rallit && flock -w 600 .deploy.lock bash deploy.sh <도커허브ID>/rallit-backend:temp-manual-1"
```

정식 배포와 임시 배포는 같은 EC2를 사용하므로 서버의 `.deploy.lock`으로 순서대로 실행되고, 나중에 실행된 쪽이 남습니다. 배포 스크립트는 선택적 DB와 Redis의 정상 상태를 먼저 확인한 뒤 `app` 이미지를 교체합니다. 설정이 바뀐 서비스는 Compose가 재생성할 수 있지만 기존 데이터 볼륨은 유지합니다.

> ⚠️ `docker compose down -v`, `docker volume rm`, `docker system prune --volumes`는 DB·Redis 데이터를 삭제합니다.

## 4. 실패와 롤백

Actions의 실패한 Job 로그를 먼저 확인합니다. DB·Redis 선기동 단계가 실패하면 app 이미지를 교체하지 않습니다. 다만 실패 전에 Redis 설정이 변경됐다면 기존 앱도 Redis 장애의 영향을 받을 수 있습니다. 앱 교체 후 헬스체크가 실패하면 `deploy.sh`가 앱 로그를 출력하고 `deploy-history.log`의 마지막 성공 이미지로 자동 복구합니다. 복구에 성공해도 Job은 실패로 표시됩니다. 첫 배포처럼 이력이 없으면 복구하지 않습니다. `.env`의 `APP_IMAGE`는 배포가 성공했을 때만 갱신됩니다.

자동 복구가 실패했거나 다른 버전으로 되돌려야 하면 직접 실행합니다.

```bash
cd ~/rallit
docker compose logs --tail 200 app
tail deploy-history.log               # 성공한 배포 이력
flock -w 600 .deploy.lock bash deploy.sh <이미지>:<이전 태그>
```

복구는 app 이미지만 되돌리며 `compose.yml`, `deploy.sh`, 서버 환경변수와 Redis 설정은 새 버전을 유지합니다. Flyway가 이미 적용한 DB 스키마도 롤백되지 않습니다. Redis 볼륨은 삭제하지 않습니다. 복구 후 헬스체크뿐 아니라 이전 이미지의 스키마 호환성과 로그인·재발급 동작도 확인합니다.

배포에 성공하면 최근 성공 이미지 3개만 남기고 같은 저장소의 나머지 태그를 삭제합니다. 정리에 실패하면 경고만 남기고, 볼륨은 건드리지 않습니다. 디스크가 이미 가득 차서 이미지 Pull이 실패하면 정리까지 진행되지 않으므로 직접 공간을 확보합니다.
