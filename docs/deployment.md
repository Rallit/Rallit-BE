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
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Google OAuth 클라이언트 ID, 비밀 키 및 BE 콜백 주소를 모두 입력 |
| `FRONTEND_CALLBACK_URL`, `ADMIN_FRONTEND_CALLBACK_URL` | 배포된 일반·운영자 FE의 로그인 완료 주소 |
| `SERVICE_ORIGINS`, `ADMIN_ORIGINS` | 배포된 FE의 정확한 Origin. 여러 개면 쉼표로 구분 |
| `AWS_REGION`, `S3_BUCKET`, `S3_PRESIGNED_*` | 이미지 저장소 설정. [5장](#5-s3-이미지-저장소) 참고 |

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

외부 헬스체크는 `200`이어야 합니다. `local`과 `temp`에서는 로그인 없이 `/swagger-ui/index.html`과 `/v3/api-docs`에 접근할 수 있습니다. `prod`가 활성화된 환경에서는 Swagger 접근을 차단합니다.

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

## 5. S3 이미지 저장소

프로필 사진과 코트 대표 사진은 S3에 저장합니다. 브라우저는 Presigned POST 폼으로 업로드하고 만료형 Presigned GET URL로 조회합니다. 실제 버킷 이름, 계정 ID, 액세스 키, 서명된 URL은 문서·이슈·PR에 남기지 않습니다.

### 사전 준비

| 항목 | 내용 |
| --- | --- |
| 버킷 | 서울 리전, 퍼블릭 액세스 차단. 최종 사진은 `profile-images/`와 `court-thumbnails/`에 저장하고 임시 경로 `image-uploads/`에는 수명 주기 규칙을 설정 |
| IAM 역할 | 이미지 버킷에 `s3:ListBucket`을 부여하고, 세 접두사에 필요한 객체 읽기·쓰기·삭제 권한을 EC2 앱 역할에 부여 |
| CORS | 버킷에서 일반·운영자 프론트 Origin과 브라우저의 `POST` 업로드, `GET` 조회를 허용 |

### 환경변수

| 이름 | 기본값 | 설정 키 | 설명 |
| --- | --- | --- | --- |
| `AWS_REGION` | `ap-northeast-2` | `rallit.s3.region` | 버킷 리전 |
| `S3_BUCKET` | 필수 | `rallit.s3.bucket` | 버킷 이름. 서버 `.env`로 주입 |
| `S3_PRESIGNED_UPLOAD_EXPIRY` | `300` | `rallit.s3.presigned.upload-expiry-seconds` | 업로드 URL 만료(초) |
| `S3_PRESIGNED_DOWNLOAD_EXPIRY` | `600` | `rallit.s3.presigned.download-expiry-seconds` | 조회 URL 만료(초) |

```dotenv
AWS_REGION=ap-northeast-2
S3_BUCKET=<버킷 이름>
S3_PRESIGNED_UPLOAD_EXPIRY=300
S3_PRESIGNED_DOWNLOAD_EXPIRY=600
```

### 인증

AWS SDK의 기본 인증 체인으로 EC2 IAM 역할의 임시 자격 증명을 사용합니다. 기본 체인은 환경변수 자격 증명을 인스턴스 역할보다 먼저 사용하므로 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 서버 `.env`나 코드에 추가하지 않습니다.

배포 전에 EC2 앱 역할로 존재하지 않는 `image-uploads/` 키에 `GetObject`를 요청해 `404 NoSuchKey`가 돌아오는지 확인합니다. 누락을 확인하면 백엔드는 완료 요청에 `409 IMAGE_UPLOAD_NOT_READY`를 반환합니다. AWS는 버킷에 `s3:ListBucket`이 없으면 누락에도 `403 AccessDenied`를 반환하며, 백엔드는 권한 및 저장소 실패를 `503`으로 처리합니다. [AWS GetObject 권한](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html)

app은 Docker 브리지 네트워크에서 실행되므로 컨테이너에서 인스턴스 메타데이터(IMDSv2)에 접근할 수 있도록 메타데이터 옵션의 PUT 응답 홉 제한을 2 이상으로 둡니다. 아래 명령이 역할 이름을 출력하면 정상입니다. 자격 증명 값 자체는 조회하거나 기록하지 않습니다.

```bash
docker compose exec -T app sh -c 'token=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 60") && curl -fsS -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/iam/security-credentials/'
```

### S3 전환

1. 일반·운영자 프론트의 Presigned 연동, S3 전용 백엔드 빌드, 운영 버킷·IAM·CORS 설정을 준비합니다.
2. 기존 이미지 등록·교체·삭제를 잠시 막고 처리 중인 요청이 끝났는지 확인합니다. 운영 DB의 기존 프로필 키를 백업한 뒤, 새 S3 업로드를 받기 전에 키를 초기화합니다. 가입 상태와 나머지 프로필 정보는 유지되고 기존 사진은 기본 이미지로 전환됩니다. 코트 키는 운영에 값이 없는지 확인합니다.
3. 아래 SQL을 운영 전환 시점에 한 번만 실행합니다.

```sql
UPDATE users SET profile_image_key = NULL WHERE profile_image_key IS NOT NULL;
```

4. S3 전용 백엔드와 Presigned 방식의 프론트를 배포합니다. 시험 계정으로 프로필 등록·교체·조회·삭제, 운영자 코트 등록과 회원의 조회를 확인한 뒤 쓰기를 재개합니다.
5. 전환 검증이 끝나면 기존 사진 볼륨을 정리합니다.

### 첫 전환 롤백

- S3 전용 배포 전에 마지막 파일시스템 배포의 앱 이미지 태그, `compose.yml`, `deploy.sh`, 이미지 볼륨 연결 및 경로 설정을 확보합니다. 기존 볼륨과 프로필 키 백업은 전환 검증까지 보존합니다.
- 자동 복구는 이전 앱 이미지를 현재 Compose 설정으로 실행합니다. 첫 전환에서 파일시스템 방식으로 돌아가야 하면 이미지 쓰기를 멈춘 상태에서 당시 Compose·배포 스크립트와 이미지 경로 설정을 복구하고, 기존 볼륨을 마운트한 뒤 백업한 프로필 키를 복원합니다. 이전 백엔드·프론트의 업로드 및 조회 계약을 맞추고 시험 계정으로 사진을 조회한 뒤 쓰기를 재개합니다.
- S3 쓰기를 재개한 뒤에는 새 이미지 키와 S3 객체를 읽을 수 있는 버전으로 복구합니다.

### Presigned API 검증

- 프로필 사진은 `POST /api/v1/users/me/profile-image/uploads`, 코트 대표 사진은 운영자 세션의 `POST /api/v1/admin/courts/{courtId}/thumbnail/uploads`에서 발급합니다. 요청 데이터는 파일명, MIME 타입과 바이트 크기입니다.
- 발급 응답의 `url`과 서명 폼 필드를 사용해 브라우저가 S3에 파일을 POST합니다. 성공 후 각 발급 경로 뒤에 `/{uploadId}/complete`를 붙여 인증 호출합니다. 백엔드는 업로드한 이미지에 크기·실제 형식·픽셀 제한을 적용하고, 리사이즈와 촬영 메타데이터 제거를 한 뒤 최종 키를 DB에 반영합니다.
- 조회는 응답에 담긴 이미지 경로 뒤에 `/url`을 붙여 인증 호출하고, 결과의 만료형 URL을 브라우저가 사용합니다.

### 로컬 MinIO 검증

MinIO에서 테스트 버킷을 생성하고 로컬 백엔드 실행 환경에 `S3_BUCKET`, `S3_ENDPOINT`와 테스트용 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 전달합니다. `S3_ENDPOINT`에는 백엔드와 브라우저가 같은 주소로 접근할 수 있는 URL을 지정합니다. 사진은 운영과 같은 발급 → MinIO POST → 완료 호출로 연결합니다.

온보딩 완료 회원 세션으로 `GET /api/v1/courts/{courtId}/thumbnail/url`이 반환한 서명 URL을 사용해 코트 사진을 조회합니다. 매치 카드에는 `courtThumbnailUrl`, 상세에는 `court.thumbnailUrl`이 포함됩니다. 사진이 없으면 공통 이미지를 표시합니다.

### AWS 계정 이전

DB에는 URL이 아닌 객체 키를 저장하므로 DB는 변경하지 않습니다. 새 계정에 버킷·CORS·IAM 역할을 같은 구성으로 만들고 기존 객체를 같은 키로 복사한 뒤, 서버 `.env`의 `S3_BUCKET`과 필요하면 `AWS_REGION`을 바꿔 다시 배포합니다. EC2도 옮기면 새 인스턴스에 IAM 역할과 메타데이터 옵션을 다시 설정합니다.
