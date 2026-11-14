# 권한 관리 시스템

`projectDesign/permission_management_system_plan.md`와 화면 시안 `1.png`~`9.png`를 바탕으로 만든 Java 21 / Spring Boot / Spring Data JPA / MySQL MVP입니다. 로그인, 사용자·메뉴·콘텐츠 관리, 역할/개별 권한, 관리자 위임 범위 및 감사 로그를 제공합니다.

## 실행

1. `.env.example`을 `.env`로 복사하고 **서로 다른 강력한 비밀번호** 3개를 입력합니다. `.env`는 Git에서 제외됩니다.
2. `docker compose up --build -d`
3. `http://localhost:8080` 접속 후 `BOOTSTRAP_ADMIN_LOGIN`과 `BOOTSTRAP_ADMIN_PASSWORD`로 로그인합니다.
4. 종료: `docker compose down` (MySQL 볼륨 유지). 데이터를 포함해 지우려면 별도로 볼륨 삭제를 결정해야 합니다.

첫 실행에서만 시스템 관리자 계정과 기본 메뉴를 생성합니다. 최초 관리자 비밀번호는 12자 이상이어야 합니다. DB 포트는 호스트에 공개하지 않습니다. 운영 시 HTTPS 역방향 프록시 뒤에 배치하고 `Secure` 세션 쿠키를 활성화해야 합니다.

## 권한 운영 순서

1. 시스템 관리자가 메뉴 관리에서 콘텐츠 메뉴 등을 추가합니다.
2. 권한 관리의 **역할 권한**에서 `ADMIN`/`USER` 메뉴 기능을 허용합니다. 초기 상태는 대시보드에 대한 `USER VIEW` 외 모두 거부입니다.
3. 시스템 관리자가 관리자 계정을 생성합니다.
4. **관리자 범위**에서 해당 관리자가 위임할 수 있는 메뉴/기능을 지정합니다. 위임하려면 관리자 본인에게도 동일 메뉴/기능에 대한 실제 접근 권한이 있어야 합니다.
5. 관리자는 사용자 관리 메뉴에 대한 `VIEW/CREATE/UPDATE/DELETE` 권한 및 관리 범위가 있을 때 자신이 생성한 일반 사용자만 관리할 수 있습니다.

권한 판정은 `SYSTEM_ADMIN` → 개별 `DENY`/`ALLOW` → 역할 권한 → 기본 거부 순서입니다. 메뉴의 비활성 상태 및 계정의 비활성 상태는 최상위 거부 조건입니다. UI 숨김 외에도 각 API에서 권한을 확인합니다.

## API 개요

| 경로 | 용도 |
|---|---|
| `GET /api/csrf`, `POST /api/login`, `POST /api/logout` | 세션 인증 |
| `GET /api/me`, `POST /api/me/password` | 내 정보/비밀번호 |
| `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}` | 사용자 |
| `GET/POST /api/menus`, `PUT/DELETE /api/menus/{id}` | 메뉴 (시스템 관리자) |
| `GET /api/my-menus` | 접근 가능한 메뉴 |
| `GET/PUT /api/permissions/user`, `/role`, `/scope` | 권한/범위 (`GET`은 ID/역할 경로 사용) |
| `GET/POST /api/contents`, `GET/PUT/DELETE /api/contents/{id}` | 콘텐츠 |
| `GET /api/audits` | 감사 로그 (시스템 관리자) |

모든 변경 요청은 `GET /api/csrf`에서 받은 헤더명/토큰을 함께 전송해야 합니다. 로그인 입력은 `username`/`password` 폼 필드입니다.

## 개발 및 검증

Java 21과 Maven 3.6.1 이상이 필요합니다. `mvn test`로 권한 정책 단위 테스트를 실행합니다. Docker 이미지 빌드에서는 Maven 3.9.11을 사용합니다.

## MVP 경계

- DB 스키마는 JPA `ddl-auto: update`로 생성합니다. 운영 배포 전 Flyway/Liquibase 마이그레이션 및 DB 백업 정책을 마련해야 합니다.
- 관리자별 담당 사용자는 해당 관리자가 직접 생성한 계정으로 한정합니다. 담당자 재배정, 조직 단위 관리, MFA/SSO, 비밀번호 재설정, 재인증, 정교한 검색/페이징은 후속 범위입니다.
- 삭제는 사용자/메뉴/콘텐츠를 논리 삭제합니다. 감사 로그는 계속 남습니다.
- 콘텐츠 본문은 화면에서 일반 텍스트로 표시하여 HTML 실행을 피합니다.
