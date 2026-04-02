# MigD

PostgreSQL 테이블 단위 부분 데이터 이관 툴.

## 개요

QA DB(소스)에서 특정 테이블만 골라 대상 DB로 복사한다.
WHERE 조건으로 데이터 범위를 제한할 수 있고, 프리셋으로 테이블 목록을 저장해 재사용할 수 있다.

## 기능

- **데이터 이관** — 테이블 목록 + WHERE 조건 지정 후 실행. PostgreSQL `COPY BINARY` 방식으로 전송
- **스키마 이관** — `pg_dump`로 스키마만 추출해 대상 DB에 적용
- **프리셋** — 자주 쓰는 테이블 목록을 저장/불러오기

## 기술 스택

- Java 21, Spring Boot 3.2, Gradle
- Thymeleaf + Bootstrap 5 (SSR)
- MyBatis + H2 File Mode (프리셋 저장)
- 순수 JDBC + PostgreSQL JDBC Driver (이관 실행)

## 설정

`src/main/resources/application.yml`에서 소스 DB와 pg_dump 경로 설정:

```yaml
migd:
  pg-dump-path: "C:/Program Files/PostgreSQL/16/bin/pg_dump.exe"
  source-db:
    host: "qa-db.example.com"
    port: 5432
    db: "qadb"
    user: "qauser"
    password: "qapassword"
```

대상 DB는 이관 실행 화면에서 직접 입력한다.

## 실행

```bash
./gradlew bootRun
```

접속: http://localhost:8080

## 테스트

```bash
./gradlew test
```
