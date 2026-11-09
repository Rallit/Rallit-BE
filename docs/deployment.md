# 배포 가이드

```text
외부 사용자 → https://api.rallit.co.kr → EC2 Nginx(443) → 127.0.0.1:8080 → app 컨테이너 → db 컨테이너
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
| 8080, 5432 | 열지 않음 | 호스트 내부 전용 |

## 2. EC2 최초 배포

### 서버 상태 확인

```bash
uname -m                       # aarch64면 DOCKER_PLATFORM을 linux/arm64로 설정
free -h                        # 2GB 미만이면 아래 스왑 설정
docker compose version         # 2.20 이상 필요
sudo ss -tlnp                  # 80, 443, 8080 사용 여부
sudo nginx -T                  # 기존 Nginx 설정
dig +short api.rallit.co.kr    # 탄력적 IP와 일치해야 함
```

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### `.env` 작성

```bash
mkdir -p ~/rallit && cd ~/rallit
openssl rand -hex 32   # DB_PASSWORD에 사용
nano .env              # .env.deploy.example 내용을 붙여넣고 값 수정
chmod 600 .env
```

`DB_URL`의 DB 이름은 `POSTGRES_DB`와 같아야 합니다. 볼륨이 이미 초기화된 뒤에는 `.env`를 바꿔도 DB 이름과 사용자, 비밀번호는 바뀌지 않습니다.

### 첫 배포

`.env`를 작성한 뒤 [3장](#3-배포-실행)의 방법으로 배포하면 DB 초기화와 앱 실행까지 진행됩니다. 결과는 EC2에서 확인합니다.

```bash
cd ~/rallit
docker compose ps
docker compose logs --tail 100 app
curl http://127.0.0.1:8080/actuator/health
```

### Nginx와 HTTPS

`sudo nginx -T`에 `api.rallit.co.kr` 블록이 이미 있으면 덮어쓰지 말고 `location`만 `deploy/nginx/api.rallit.co.kr.conf`에 맞춥니다. 없으면 이 파일을 추가합니다. 위치는 Ubuntu의 경우 `sites-available`에 넣고 `sites-enabled`에 링크하고, Amazon Linux의 경우 `/etc/nginx/conf.d/`입니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.rallit.co.kr --redirect
sudo certbot renew --dry-run
```

외부에서 확인합니다. 모두 `200`이어야 합니다. 프론트엔드에는 `https://api.rallit.co.kr/swagger-ui/index.html`을 공유합니다.

```bash
for path in /actuator/health /swagger-ui/index.html /v3/api-docs; do
  curl -s -o /dev/null -w "%{http_code} $path\n" "https://api.rallit.co.kr$path"
done
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
ssh -i <키 파일> <사용자>@<EC2_HOST> "bash ~/rallit/deploy.sh <도커허브ID>/rallit-backend:temp-manual-1"
```

정식 배포와 임시 배포는 같은 EC2를 사용하므로 나중에 실행된 쪽이 남습니다. 배포는 `app`만 교체하며 `db` 컨테이너와 데이터는 유지합니다.

> ⚠️ `docker compose down -v`, `docker volume rm`, `docker system prune --volumes`는 DB 데이터를 삭제합니다.

## 4. 실패와 롤백

Actions의 실패한 Job 로그를 먼저 확인합니다. `deploy.sh`는 실패 시 앱 로그와 롤백 명령을 출력합니다. `.env`의 `APP_IMAGE`는 배포가 성공했을 때만 갱신됩니다.

```bash
cd ~/rallit
docker compose logs --tail 200 app
tail deploy-history.log               # 성공한 배포 이력
bash deploy.sh <이미지>:<이전 태그>
```

이미 변경된 DB 스키마는 롤백되지 않습니다.
