# Nginx Test Environment

하이브리드 인프라 테스트 환경 - 레거시 온프레미스부터 최신 클라우드 네이티브 구조까지

## 개요

이 프로젝트는 다양한 기술 스택과 배포 방식을 통합 테스트할 수 있는 환경을 제공합니다:

- **레거시**: Tomcat 기반 클래식 온프레미스 배포
- **모던**: Kubernetes 기반 컨테이너화된 마이크로서비스
- **다양한 런타임**: Docker 런타임 / containerd 런타임
- **완전한 Observability**: 메트릭, 로깅, 트레이싱

## 아키텍처

```
                         ┌─────────────────────────────────────┐
                         │        Observability Stack          │
                         │  Prometheus │ Grafana │ Jaeger │ Loki │
                         └─────────────────────────────────────┘
                                          ▲
                    ┌─────────────────────┴─────────────────────┐
                    │              NGINX Load Balancer          │
                    │               (Port 80/443)               │
                    └─────────────────────┬─────────────────────┘
        ┌───────────┬───────────┬─────────┼─────────┬───────────┬───────────┐
        ▼           ▼           ▼         ▼         ▼           ▼           ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
   │ WAS 1,2 │ │ WAS 3,4 │ │ WAS 5,6 │ │  WAS 7  │ │  WAS 8  │ │  Redis  │ │  Kafka  │
   │ Tomcat  │ │ Spring  │ │ Node.js │ │  Flask  │ │   Go    │ │ Cluster │ │ Cluster │
   │ Legacy  │ │  Boot   │ │ Express │ │Gunicorn │ │  Fiber  │ │(6 nodes)│ │(3 nodes)│
   └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘
    On-Prem     K8s+Docker  K8s+CRI     K8s+Docker  K8s+CRI
```

## WAS 구성

| WAS | 프레임워크 | 런타임 | 배포 방식 | 포트 |
|-----|-----------|--------|----------|------|
| 1, 2 | Tomcat 9 + JSP | JDK 11 | Classic (systemd) | 8081, 8082 |
| 3, 4 | Spring Boot 3.2 | JDK 21 | K8s + Docker | 8083, 8084 |
| 5, 6 | Node.js + Express | Node 20 | K8s + containerd | 3001, 3002 |
| 7 | Flask + Gunicorn | Python 3.12 | K8s + Docker | 5001 |
| 8 | Go + Fiber | Go 1.22 | K8s + containerd | 8001 |

## 빠른 시작

### 1. 전체 환경 시작

```bash
# Docker 네트워크 생성
docker network create nginx-test-net --subnet=172.20.0.0/16

# 전체 스택 시작
./scripts/start-all.sh
```

### 2. 개별 컴포넌트 시작

```bash
# 인프라만 시작 (Redis, Kafka)
cd infrastructure/redis && docker-compose up -d
cd infrastructure/kafka && docker-compose up -d

# Observability만 시작
cd infrastructure/observability && docker-compose up -d

# WAS 서비스만 시작
docker-compose up -d
```

### 3. 상태 확인

```bash
./scripts/status.sh
```

## API 엔드포인트

### Nginx 라우팅

| 경로 | 대상 WAS | 로드밸런싱 |
|------|---------|-----------|
| /tomcat/* | WAS 1,2 | Round Robin |
| /springboot/* | WAS 3,4 | Least Connections |
| /nodejs/* | WAS 5,6 | IP Hash |
| /flask/* | WAS 7 | Single |
| /go/* | WAS 8 | Single |
| /api/* | All WAS | Weighted |

### 공통 API

모든 WAS는 동일한 API 인터페이스를 제공합니다:

```
GET  /health          - 헬스 체크
GET  /metrics         - Prometheus 메트릭
GET  /api/info        - 서버 정보
GET  /api/cache/:key  - Redis 캐시 조회
PUT  /api/cache/:key  - Redis 캐시 저장
DELETE /api/cache/:key - Redis 캐시 삭제
POST /api/message     - Kafka 메시지 발행
GET  /api/slow?delay=1000 - 지연 테스트
GET  /api/error?code=500  - 에러 테스트
```

## Observability

### 접속 정보

| 서비스 | URL | 용도 |
|--------|-----|------|
| Prometheus | http://localhost:9090 | 메트릭 수집/쿼리 |
| Grafana | http://localhost:3000 | 대시보드 (admin/admin123) |
| Jaeger | http://localhost:16686 | 분산 트레이싱 |
| Loki | http://localhost:3100 | 로그 수집 |
| Kafka UI | http://localhost:8090 | Kafka 관리 |

### 트레이싱

모든 WAS는 OpenTelemetry를 통해 Jaeger로 트레이스를 전송합니다:

- W3C Trace Context 지원
- B3 (Zipkin) 헤더 호환
- 자동 계측 (HTTP, Redis, Kafka)

## Kubernetes 배포

### 네임스페이스 생성

```bash
kubectl apply -f k8s/common/namespace.yaml
```

### Docker 런타임 서비스 배포

```bash
kubectl apply -f k8s/common/configmaps.yaml
kubectl apply -f k8s/docker-runtime/
```

### containerd 런타임 서비스 배포

```bash
kubectl apply -f k8s/containerd-runtime/
```

### Nginx Ingress 배포

```bash
kubectl apply -f k8s/common/nginx-ingress.yaml
```

## CI/CD

GitLab CI 파이프라인이 구성되어 있습니다:

```yaml
stages:
  - validate    # 설정 파일 검증
  - build       # 각 WAS 빌드
  - test        # 단위/통합 테스트
  - scan        # 보안 스캔
  - deploy-staging
  - deploy-production
```

## 디렉토리 구조

```
nginx_test/
├── nginx/                    # Nginx 설정
├── was-1-tomcat/            # Tomcat WAS 1
├── was-2-tomcat/            # Tomcat WAS 2
├── was-3-springboot/        # Spring Boot (Kotlin)
├── was-4-springboot/        # Spring Boot (Java)
├── was-5-nodejs/            # Node.js (JavaScript)
├── was-6-nodejs/            # Node.js (TypeScript)
├── was-7-flask/             # Flask + Gunicorn
├── was-8-go/                # Go + Fiber
├── infrastructure/
│   ├── redis/               # Redis Cluster (6 nodes)
│   ├── kafka/               # Kafka Cluster (3 nodes, KRaft)
│   └── observability/       # Prometheus, Grafana, Jaeger, Loki
├── k8s/
│   ├── common/              # 공통 설정
│   ├── docker-runtime/      # Docker 런타임용 매니페스트
│   └── containerd-runtime/  # containerd 런타임용 매니페스트
├── gitlab-ci/               # GitLab CI/CD
└── scripts/                 # 유틸리티 스크립트
```

## 테스트

### 부하 테스트

```bash
# hey 설치 필요: go install github.com/rakyll/hey@latest
./scripts/test-load.sh http://localhost 30 10
```

### 트레이싱 테스트

```bash
# 요청 전송
curl -H "X-Trace-Id: test-trace-001" http://localhost/api/info

# Jaeger에서 확인
open http://localhost:16686
```

### 장애 주입 테스트

```bash
# 지연 테스트
curl "http://localhost/api/slow?delay=5000"

# 에러 테스트
curl "http://localhost/api/error?code=503"
```

## 기술 스택 요약

- **런타임**: JDK 11/21, Node.js 20, Python 3.12, Go 1.22
- **컨테이너**: Docker, containerd
- **오케스트레이션**: Kubernetes
- **로드밸런싱**: Nginx
- **캐시**: Redis Cluster
- **메시지 큐**: Apache Kafka (KRaft)
- **Observability**: Prometheus, Grafana, Jaeger, Loki, OpenTelemetry
- **CI/CD**: GitLab CI

## 라이선스

MIT License
