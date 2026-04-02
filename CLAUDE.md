# Project: MigD - PostgreSQL 부분 데이터 이관 툴

## 1. AI Assistant 상호작용 규칙 (엄격 준수)

### 1.1 Communication & Verification
- **정보 검증:** 명확한 증거 없이 가정하거나 추측하지 마십시오.
- **군더더기 제거:** "알겠습니다", "좋은 질문입니다" 같은 서론과 "죄송합니다" 같은 사과는 절대 금지합니다.
- **요약 금지:** 변경된 내용을 말로 요약하지 말고 코드 그 자체로 보여주십시오.
- **이해 여부 확인 금지:** 주석이나 설명에 사용자의 이해 여부를 묻는 피드백을 포함하지 마십시오.
- **한글 사용:** 모든 대화나 코멘트는 한글로 작성하십시오.

### 1.2 Editing & Output Format
- **파일 단위 변경:** 변경 사항은 파일 단위로 명확하게 구분하여 제공하십시오.
- **단일 청크 편집:** 한 파일에 대한 수정은 여러 단계로 나누지 말고, **한 번에** 전체 코드를 제공하십시오. (`// ... existing code` 사용을 절대 지양하고, 복사-붙여넣기 가능하게 전체 코드를 출력하십시오.)
- **임의 변경 금지:** 요청받지 않은 기능을 임의로 추가하거나, 관련 없는 코드를 삭제하지 마십시오. (기존 구조 보존)
- **공백 변경 제안 금지:** 단순 포맷팅이나 공백 변경만 있는 수정 제안은 하지 마십시오.
- **실제 파일 링크:** 실제 파일 경로를 주석이나 제목에 명확히 언급하십시오. (예: `src/main/java/com/migd/service/DataMigrationService.java`)

---

## 2. 기술 스택

- **Backend:** Java 21, Spring Boot 3.x (Gradle)
- **Frontend:** Thymeleaf (SSR, 별도 프론트 서버 없음) + Bootstrap 5
- **프리셋 저장:** MyBatis + H2 File Mode (`./data/preset_db`)
- **이관 작업:** 순수 JDBC (`DriverManager.getConnection()`) + PostgreSQL JDBC Driver
- **SQL 파싱:** JSqlParser

## 3. 아키텍처 핵심 규칙

### 3.1 DB 연결 분리 원칙
- H2는 Spring의 기본 DataSource로만 사용 (프리셋 CRUD, MyBatis)
- Source DB / Target DB는 `JdbcConnectionUtil`을 통해 런타임에 동적으로 커넥션 생성
- `DataMigrationService`는 `@Transactional` 사용 금지 — `setAutoCommit(false)` + 수동 `commit()` / `rollback()`

### 3.2 데이터 이관 구현 원칙
- CopyManager 사용 시 반드시 `PipedOutputStream/PipedInputStream` 두 스레드 패턴 사용
- COPY OUT 스레드의 `finally` 블록에서 `pipedOut.close()` 필수 (데드락 방지)
- PK 중복 에러(SQLState `23505`) 발생 시 즉시 롤백 후 예외 throw — upsert/ignore 금지

### 3.3 pg_dump 실행 원칙
- `ProcessBuilder`로 pg_dump 실행 시 stdout/stderr를 반드시 가상 스레드 2개로 동시에 읽을 것 (OS 파이프 버퍼 블락 방지)
- pg_dump 경로는 `application.yml`의 `migd.pg-dump-path`에서 주입

## 4. 패키지 구조
```
com.migd
├── controller/
├── domain/
├── dto/
├── exception/
├── mapper/
├── service/
└── util/
```
