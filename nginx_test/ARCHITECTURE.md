# Nginx Test Environment - Hybrid Infrastructure

## 아키텍처 개요

```
                                    ┌─────────────────────────────────────────────────────────────┐
                                    │                    Observability Stack                       │
                                    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
                                    │  │Prometheus│  │ Grafana  │  │  Jaeger  │  │   Loki   │    │
                                    │  │ :9090    │  │  :3000   │  │  :16686  │  │  :3100   │    │
                                    │  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
                                    └─────────────────────────────────────────────────────────────┘
                                                              ▲
                                                              │ Metrics/Traces/Logs
                                                              │
┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         NGINX Load Balancer                                          │
│                                            :80 / :443                                                │
│                                                                                                      │
│   upstream was_tomcat { server was1:8080; server was2:8080; }      ← Round Robin                   │
│   upstream was_spring { server was3:8080; server was4:8080; }      ← Least Connections             │
│   upstream was_node   { server was5:3000; server was6:3000; }      ← IP Hash                       │
│   upstream was_flask  { server was7:5000; }                        ← Weight Based                  │
│   upstream was_go     { server was8:8000; }                        ← Health Check                  │
└─────────────────────────────────────────────────────────────────────────────────────────────────────┘
          │              │              │              │              │
          ▼              ▼              ▼              ▼              ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  LEGACY     │  │  MODERN     │  │  MODERN     │  │  MODERN     │  │  MODERN     │
│  On-Premise │  │  K8s+Docker │  │  K8s+CRI    │  │  K8s+Docker │  │  K8s+CRI    │
├─────────────┤  ├─────────────┤  ├─────────────┤  ├─────────────┤  ├─────────────┤
│ WAS 1,2     │  │ WAS 3,4     │  │ WAS 5,6     │  │ WAS 7       │  │ WAS 8       │
│ Tomcat 9    │  │ Spring Boot │  │ Node.js     │  │ Flask       │  │ Go (Gin)    │
│ Classic     │  │ + Gradle    │  │ + Express   │  │ + Gunicorn  │  │ + Fiber     │
│ Deployment  │  │ 3 Replicas  │  │ 3 Replicas  │  │ 3 Replicas  │  │ 3 Replicas  │
└─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
          │              │              │              │              │
          └──────────────┴──────────────┴──────────────┴──────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
            ┌─────────────┐                         ┌─────────────┐
            │    Redis    │                         │    Kafka    │
            │   Cluster   │                         │   Cluster   │
            │  (3 nodes)  │                         │  (3 nodes)  │
            │   :6379     │                         │   :9092     │
            └─────────────┘                         └─────────────┘

## 기술 스택

### WAS 구성
| WAS   | Framework          | Runtime      | Deploy Method           |
|-------|-------------------|--------------|-------------------------|
| 1, 2  | Tomcat 9 + JSP    | JDK 11       | Classic (systemd)       |
| 3, 4  | Spring Boot 3.2   | JDK 21       | K8s + Docker Runtime    |
| 5, 6  | Node.js + Express | Node 20 LTS  | K8s + containerd        |
| 7     | Flask + Gunicorn  | Python 3.12  | K8s + Docker Runtime    |
| 8     | Go + Fiber        | Go 1.22      | K8s + containerd        |

### Kubernetes 환경
- **Docker Runtime**: was-3, was-4, was-7
- **containerd Runtime**: was-5, was-6, was-8
- **Replicas**: 각 서비스 3개 (권장 구성)

### Observability
- **Metrics**: Prometheus + Grafana
- **Tracing**: Jaeger + OpenTelemetry
- **Logging**: Loki + Promtail

### Message Queue & Cache
- **Redis Cluster**: 3 master nodes + 3 replica nodes
- **Kafka Cluster**: 3 brokers + ZooKeeper (or KRaft)

### CI/CD
- **GitLab CI**: 각 WAS별 독립 파이프라인
- **Stages**: build → test → scan → deploy

## 네트워크 구성

```
Network: 172.20.0.0/16

├── nginx:        172.20.0.10
├── was-1-tomcat: 172.20.1.1
├── was-2-tomcat: 172.20.1.2
├── was-3-spring: 172.20.2.0/24 (K8s Pod Network)
├── was-4-spring: 172.20.2.0/24 (K8s Pod Network)
├── was-5-node:   172.20.3.0/24 (K8s Pod Network)
├── was-6-node:   172.20.3.0/24 (K8s Pod Network)
├── was-7-flask:  172.20.4.0/24 (K8s Pod Network)
├── was-8-go:     172.20.5.0/24 (K8s Pod Network)
├── redis:        172.20.10.0/24
├── kafka:        172.20.11.0/24
└── observability: 172.20.20.0/24
```

## 실행 방법

```bash
# 1. 전체 환경 시작
./scripts/start-all.sh

# 2. 개별 환경 시작
./scripts/start-legacy.sh    # Tomcat only
./scripts/start-k8s.sh       # K8s services
./scripts/start-infra.sh     # Redis, Kafka, Observability

# 3. 상태 확인
./scripts/status.sh
```
