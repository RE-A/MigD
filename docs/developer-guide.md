# MigD 개발자 가이드

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [아키텍처](#3-아키텍처)
4. [패키지 구조](#4-패키지-구조)
5. [핵심 설계 원칙](#5-핵심-설계-원칙)
6. [환경 설정](#6-환경-설정)
7. [빌드 및 실행](#7-빌드-및-실행)
8. [테스트](#8-테스트)
9. [API 엔드포인트](#9-api-엔드포인트)
10. [이관 흐름 상세](#10-이관-흐름-상세)
11. [오류 처리](#11-오류-처리)
12. [로그](#12-로그)

---

## 1. 프로젝트 개요

MigD는 PostgreSQL 데이터베이스 간 **부분 데이터 이관** 도구다.

**핵심 시나리오**: QA DB(소스)의 특정 테이블만 골라 대상 DB로 복사한다. WHERE 조건으로 데이터 범위를 제한할 수 있고, 자주 쓰는 테이블 조합은 프리셋으로 저장해 재사용한다.

**주요 기능**:
- 테이블 단위 데이터 이관 (PostgreSQL COPY BINARY 스트리밍)
- WHERE 조건 부분 이관
- pg_dump 기반 스키마 이관
- 프리셋(테이블 목록) 저장/불러오기
- AWS 도메인 대상 호스트 차단

---

## 2. 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.5 |
| Build | Gradle | 8.x |
| Web/View | Spring MVC + Thymeleaf | SSR |
| CSS Framework | Bootstrap | 5.3.3 |
| 프리셋 저장소 | H2 File Mode + MyBatis | - |
| 이관 실행 | 순수 JDBC + PostgreSQL JDBC Driver | 42.x |
| SQL 파싱 | JSqlParser | 4.9 |
| 테스트 | JUnit 5 + Testcontainers | - |

---

## 3. 아키텍처

### 3.1 전체 구조

```
브라우저
    │
    ▼
Controller (Spring MVC)
    │
    ├── PresetService ──── H2 File DB (MyBatis)
    │                       └── PRESET / PRESET_TABLE
    │
    ├── SchemaService ──── pg_dump (ProcessBuilder, 로컬 바이너리)
    │                       └── Source DB → DDL 추출 → Target DB 적용
    │
    └── DataMigrationService ── JdbcConnectionUtil.open()
                                 ├── Source DB (COPY OUT)
                                 └── Target DB (COPY IN)
```

### 3.2 DB 연결 분리 원칙

MigD는 두 종류의 DB 연결을 완전히 분리한다:

| 연결 유형 | 사용 목적 | 생성 방식 |
|----------|---------|---------|
| **H2** | 프리셋 CRUD, MyBatis | Spring의 DataSource (커넥션 풀) |
| **소스 DB** | COPY OUT, DDL 추출 | `JdbcConnectionUtil.open(DbConnInfo)` — 런타임 동적 생성 |
| **대상 DB** | COPY IN, DDL 적용 | `JdbcConnectionUtil.open(DbConnInfo)` — 런타임 동적 생성 |

소스 DB 접속 정보는 `application.yml`의 `migd.source-db.*`에서만 읽으며, 사용자가 화면에서 변경할 수 없다. 대상 DB 접속 정보는 이관 실행 화면에서 매번 입력받는다.

### 3.3 데이터 이관 흐름

```
Source DB                           Target DB
    │                                   │
    │  COPY (SELECT * FROM tbl          │
    │         [WHERE ...])              │
    │  TO STDOUT WITH (FORMAT BINARY)   │
    │                                   │
    ├──→ CopyManager.copyOut()          │
    │         │                         │
    │    PipedOutputStream (64KB)       │
    │         │                         │
    │    PipedInputStream               │
    │         │                         │
    │    CopyManager.copyIn() ─────────→│
    │                         COPY tbl  │
    │                         FROM STDIN│
    │                         WITH BINARY│
```

- Thread A (가상 스레드): COPY OUT → `PipedOutputStream`
- Thread B (현재 스레드): `PipedInputStream` → COPY IN
- 버퍼: 64KB (`PipedInputStream` 생성자에 전달)
- `finally` 블록에서 `pipedOut.close()` 필수 — 닫지 않으면 Thread B가 영원히 대기

### 3.4 스키마 이관 흐름

```
이관 실행 화면 또는 스키마 이관 화면
        │
        ▼
SchemaService
        │
        ├── ensureSchemas(): 테이블별
        │       ├── tableExistsOnTarget() == true  → SKIPPED
        │       └── tableExistsOnTarget() == false → pg_dump -s -t → applyDdlToTarget()
        │
        └── applyFullSchemaDump(): 스키마 전체
                pg_dump -s -n schemaName → applyDdlToTarget()
```

`pg_dump` 실행 시 stdout/stderr를 가상 스레드 2개로 동시에 읽어 OS 파이프 버퍼 블록을 방지한다.

---

## 4. 패키지 구조

```
src/main/java/com/migd/
├── MigdApplication.java              Spring Boot 진입점
│
├── config/
│   └── MigdProperties.java           @ConfigurationProperties("migd")
│                                     pg-dump-path, source-db.* 바인딩
│
├── controller/
│   ├── HomeController.java           GET /
│   ├── MigrationController.java      GET/POST /migration
│   ├── SchemaController.java         GET/POST /schema
│   └── PresetController.java         /presets CRUD + AJAX
│
├── service/
│   ├── DataMigrationService.java     COPY BINARY 이관 실행
│   ├── SchemaService.java            pg_dump 스키마 이관
│   └── PresetService.java            프리셋 CRUD (H2+MyBatis)
│
├── domain/
│   ├── Preset.java                   프리셋 마스터 (id, name, description)
│   └── PresetTable.java              테이블 목록 (schema, table, where, orderNum)
│
├── dto/
│   ├── DbConnInfo.java               record — DB 접속 정보
│   ├── MigrationRequest.java         이관 실행 폼 + toTgtConnInfo()
│   ├── MigrationResult.java          전체 이관 결과 집계
│   ├── TableMigrationResult.java     테이블 단위 결과 (record)
│   ├── SchemaExecuteRequest.java     스키마 이관 폼
│   ├── SchemaResult.java             테이블 스키마 결과 (record, Status enum)
│   └── FullSchemaDumpResult.java     전체 스키마 덤프 결과 (record)
│
├── exception/
│   ├── MigrationException.java       RuntimeException 래퍼
│   └── PkDuplicateException.java     PK 중복 전용 (미사용, SQLState 23505로 직접 처리)
│
├── mapper/
│   ├── PresetMapper.java             MyBatis 인터페이스
│   └── PresetTableMapper.java        MyBatis 인터페이스
│
└── util/
    └── JdbcConnectionUtil.java       JDBC URL 생성 + Connection 오픈

src/main/resources/
├── application.yml
├── schema.sql                        H2 초기화 DDL (PRESET, PRESET_TABLE)
├── mapper/
│   ├── PresetMapper.xml
│   └── PresetTableMapper.xml
└── templates/
    ├── fragments/layout.html         공통 레이아웃 (navbar, Bootstrap)
    ├── index.html                    대시보드
    ├── migration/
    │   ├── execute.html              이관 실행 화면
    │   └── result.html              이관 결과 화면
    ├── preset/
    │   ├── list.html                 프리셋 목록
    │   └── form.html                 프리셋 생성/수정
    └── schema/
        └── execute.html             스키마 이관 화면
```

---

## 5. 핵심 설계 원칙

### 5.1 DataMigrationService — 트랜잭션 수동 제어

`@Transactional` 사용 금지. 대신:

```java
tgtConn.setAutoCommit(false);
// ...
tgtConn.commit();   // 성공 시
tgtConn.rollback(); // 실패 시
```

이유: PostgreSQL COPY 프로토콜은 Spring의 트랜잭션 추상화 밖에서 동작하므로, `@Transactional`은 실질적인 제어권이 없다.

### 5.2 PK 중복 처리

SQLState `23505` 감지 → 즉시 롤백 → 사용자 친화적 메시지 반환. upsert나 ignore는 적용하지 않는다. 이미 데이터가 있는 테이블에 덮어쓰는 것은 위험하기 때문.

```java
if (primary instanceof SQLException sqle && "23505".equals(sqle.getSQLState())) {
    String msg = String.format("PK 중복 오류 [%s.%s]: ...", schema, table);
    return new TableMigrationResult(table, 0, msg);
}
```

### 5.3 테이블 이관 실패 시 동작

한 테이블이 실패하면 이후 테이블은 모두 "건너뜀" 처리 후 즉시 중단. 실패한 테이블의 변경은 롤백되지만, 이미 성공한 테이블의 데이터는 커밋된 상태로 유지된다.

### 5.4 AWS 도메인 차단

대상 DB 호스트에 AWS 도메인이 감지되면 서비스 계층 진입 전에 예외를 던진다.

```
*.amazonaws.com
*.rds.*
*.elasticache.*
*.aws.*
```

---

## 6. 환경 설정

### 6.1 application.yml 전체 항목

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/preset_db;AUTO_SERVER=TRUE;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  h2:
    console:
      enabled: true
      path: /h2-console
  thymeleaf:
    cache: false
    encoding: UTF-8

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.migd.domain
  configuration:
    map-underscore-to-camel-case: true

server:
  port: 8080

logging:
  level:
    com.migd: DEBUG
    org.mybatis: INFO
  file:
    name: logs/migd.log
  logback:
    rollingpolicy:
      file-name-pattern: logs/migd.%d{yyyy-MM-dd}.%i.log
      max-file-size: 10MB
      max-history: 30
      total-size-cap: 500MB

migd:
  pg-dump-path: "C:/Program Files/PostgreSQL/16/bin/pg_dump.exe"
  source-db:
    host: "qa-db.example.com"
    port: 5432
    db: "qadb"
    user: "qauser"
    password: "qapassword"
```

### 6.2 필수 변경 항목

서비스를 운영하려면 다음 두 항목을 실제 환경에 맞게 수정해야 한다:

**pg_dump 경로**
```yaml
migd:
  pg-dump-path: "C:/Program Files/PostgreSQL/16/bin/pg_dump.exe"
  # Linux: "/usr/bin/pg_dump"
  # Mac (Homebrew): "/opt/homebrew/bin/pg_dump"
```

**소스 DB 접속 정보**
```yaml
migd:
  source-db:
    host: "실제-qa-db-호스트"
    port: 5432
    db: "데이터베이스명"
    user: "사용자명"
    password: "비밀번호"
```

### 6.3 H2 콘솔

`http://localhost:8080/h2-console`에서 H2 인메모리 DB를 직접 조회할 수 있다.
- JDBC URL: `jdbc:h2:file:./data/preset_db;MODE=PostgreSQL`
- User Name: `sa`
- Password: (빈 값)

---

## 7. 빌드 및 실행

### 7.1 사전 요구사항

| 항목 | 버전 |
|------|------|
| JDK | 21 이상 |
| pg_dump | PostgreSQL 14+ (로컬 설치 또는 경로 지정) |
| Docker | 통합 테스트 실행 시 필요 |

### 7.2 빌드

```bash
# 테스트 제외 빌드 (Docker 없는 환경)
./gradlew build -x test

# 전체 빌드 (Docker 필요)
./gradlew build
```

### 7.3 실행

```bash
# 개발 실행
./gradlew bootRun

# JAR 실행
./gradlew build -x test
java -jar build/libs/migd-0.0.1-SNAPSHOT.jar
```

접속: `http://localhost:8080`

### 7.4 포트 변경

```bash
# 실행 시 포트 오버라이드
java -jar build/libs/migd-*.jar --server.port=9090

# 또는 application.yml
server:
  port: 9090
```

---

## 8. 테스트

### 8.1 테스트 구성

| 클래스 | 종류 | 목적 |
|--------|------|------|
| `DataMigrationServiceQueryTest` | 단위 | `buildCopyOutQuery` / `buildCopyInQuery` SQL 조립 검증 (리플렉션으로 private 메서드 접근) |
| `PresetServiceTest` | 통합 (H2) | WHERE 조건 저장/로드, ORDER 정렬, 테이블 교체, CASCADE 삭제 |
| `MigrationControllerTest` | 통합 (MockMvc) | AWS 호스트 차단, GET 화면 렌더링 |
| `SchemaControllerTest` | 통합 (MockMvc) | AWS 호스트 차단 |
| `MigrationRequestTest` | 단위 | DTO 필드 매핑, 방어적 복사 |
| `PostgresMigrationIntegrationTest` | Docker 통합 | 실제 PostgreSQL 두 대로 end-to-end 이관 흐름 검증 |

### 8.2 단위/통합 테스트 실행

```bash
./gradlew test
```

테스트 리포트: `build/reports/tests/test/index.html`

### 8.3 Docker 통합 테스트

`PostgresMigrationIntegrationTest`는 Docker를 사용해 PostgreSQL 14 컨테이너 2개(소스/타겟)를 자동으로 기동한다. Docker가 설치되어 있어야 실행된다.

**테스트 데이터**: HR 스키마 20개 테이블, 시퀀스 4개, plpgsql 함수 3개, 프로시저 4개, 샘플 데이터 219건

**실행 순서**:

| Order | 테스트 | 검증 내용 |
|-------|--------|---------|
| 1 | `test1_schemaDump_applyToTarget` | pg_dump로 hr 스키마 덤프 → 타겟에 적용 → 20테이블 + 함수/프로시저 존재, 데이터 0건 |
| 2 | `test2_ensureSchemas_skippedWhenExists` | 이미 존재하는 테이블 → `Status.SKIPPED` 반환 |
| 3 | `test3_migrateAll_fullTransfer` | 20개 테이블 전체 이관 → 테이블별 행 수 일치, 합계 219건 |
| 4 | `test4_migrateAll_whereCondition` | `WHERE is_active=TRUE` → 비활성 직원 3명 제외, 17건만 이관 |
| 5 | `test5_migrateAll_pkDuplicateRollback` | 중복 재이관 → 에러 메시지 "PK 중복 오류", 행 수 불변 |

**특이사항**: pg_dump는 소스 컨테이너 내부 바이너리를 `execInContainer()`로 실행한다. 로컬에 pg_dump가 없어도 동작한다.

```bash
# 통합 테스트만 실행
./gradlew test --tests "com.migd.integration.*"
```

### 8.4 테스트 설정 파일

```
src/test/resources/
├── application.yml              H2 인메모리 오버라이드, migd.source-db dummy값
└── db/
    ├── init-source.sql          소스 컨테이너 초기화 DDL+DML (HR 스키마)
    └── create-target-schema.sql 타겟 스키마 생성 (IF NOT EXISTS, FK 없음)
```

---

## 9. API 엔드포인트

### 9.1 화면 (Thymeleaf SSR)

| Method | URL | 역할 |
|--------|-----|------|
| GET | `/` | 대시보드 — 최근 프리셋 목록 |
| GET | `/migration` | 이관 실행 화면 (옵션: `?presetId=N`) |
| POST | `/migration/run` | 이관 실행 |
| GET | `/migration/result` | 이관 결과 화면 |
| GET | `/schema` | 스키마 이관 화면 |
| POST | `/schema/run` | 스키마 이관 실행 |
| GET | `/presets` | 프리셋 목록 |
| GET | `/presets/new` | 프리셋 생성 폼 |
| GET | `/presets/{id}/edit` | 프리셋 수정 폼 |
| POST | `/presets` | 프리셋 저장 (폼 제출) |
| POST | `/presets/{id}/delete` | 프리셋 삭제 |

### 9.2 AJAX (JSON)

| Method | URL | 역할 | 요청/응답 |
|--------|-----|------|---------|
| GET | `/presets/{id}/tables` | 프리셋 테이블 목록 조회 | `→ List<PresetTable>` |
| POST | `/presets/save` | 프리셋 AJAX 저장 | `← Preset JSON` / `→ {id, name}` |
| POST | `/presets/test-connection` | 대상 DB 연결 테스트 | `← host/port/db/user/password` / `→ {status, version}` |

### 9.3 MigrationRequest 구조

`POST /migration/run`의 폼 파라미터:

```
tgtHost       대상 DB 호스트
tgtPort       대상 DB 포트 (기본값 5432)
tgtDb         대상 DB 이름
tgtUser       대상 DB 사용자
tgtPassword   대상 DB 비밀번호
tables[0].schemaName   테이블 스키마
tables[0].tableName    테이블 이름
tables[0].whereCondition  WHERE 조건 (선택)
tables[0].orderNum     실행 순서
tables[1]...
```

---

## 10. 이관 흐름 상세

### 10.1 이관 실행 단계

`POST /migration/run` 처리 순서:

1. `validateTargetHost()` — AWS 도메인 차단
2. `migdProperties.getSourceDb()` → `DbConnInfo src` 생성
3. `request.toTgtConnInfo()` → `DbConnInfo tgt` 생성
4. `SchemaService.ensureSchemas()` — 테이블별 DDL 확인/생성
5. `DataMigrationService.migrateAll()` — 순서대로 테이블 이관
6. Flash attribute로 결과 전달 → `redirect:/migration/result`

### 10.2 COPY BINARY 프로토콜 선택 이유

| 방식 | 장점 | 단점 |
|------|------|------|
| COPY TEXT | 가독성 | 인코딩, NULL 처리 복잡 |
| **COPY BINARY** | 타입 무손실, 성능 우수 | 가독성 없음 |
| INSERT 반복 | 단순 | 느림, 메모리 문제 |

타입 변환 없이 PostgreSQL 내부 바이너리 포맷으로 전달하므로, `TIMESTAMPTZ`, `NUMERIC`, `JSONB` 등 복잡한 타입도 손실 없이 이관된다.

### 10.3 WHERE 조건 처리

```java
// whereCondition == null 또는 blank
"SELECT * FROM \"schema\".\"table\""

// whereCondition = "is_active = TRUE"
"SELECT * FROM \"schema\".\"table\" WHERE is_active = TRUE"

// COPY OUT 최종 형태
"COPY (SELECT * FROM \"schema\".\"table\" [WHERE ...]) TO STDOUT WITH (FORMAT BINARY)"
```

WHERE 조건 문자열은 사용자가 직접 입력한 SQL 조각이므로, 신뢰할 수 있는 내부 환경에서만 사용해야 한다.

---

## 11. 오류 처리

| 상황 | 처리 방식 |
|------|---------|
| 대상 DB 연결 실패 | `SchemaService`: 전체 테이블 FAILED 반환 |
| 테이블 스키마 없음 (pg_dump 사용) | `SchemaResult.Status.CREATED` |
| 테이블 스키마 이미 존재 | `SchemaResult.Status.SKIPPED` |
| COPY 도중 오류 | 롤백 후 `TableMigrationResult.errorMessage` 설정 |
| PK 중복 (SQLState 23505) | 롤백 + "PK 중복 오류 [schema.table]" 메시지 |
| 한 테이블 실패 | 이후 테이블 전부 "이전 테이블 실패로 건너뜀" |
| AWS 도메인 | `IllegalArgumentException` → 화면으로 오류 메시지 표시 |

---

## 12. 로그

로그 파일 위치: `logs/migd.log`

| Logger | 레벨 |
|--------|------|
| `com.migd` | DEBUG |
| `org.mybatis` | INFO |

주요 로그 포인트:
- 이관 시작/완료: `이관 시작: schema.table | COPY OUT SQL: ...`
- 이관 결과: `이관 완료: schema.table - N건`
- 롤백: `롤백 완료: schema.table`
- pg_dump 명령: DEBUG 레벨 (`pg_dump 명령: ...`)
- AWS 차단: WARN 레벨 없음, 예외로 직접 처리됨

일별 롤링 (`logs/migd.2026-01-01.0.log`), 파일당 10MB, 30일 보관, 총 500MB 상한.
