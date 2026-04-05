-- ============================================================
-- HR 스키마 — 소스 PostgreSQL 컨테이너 초기화 (FK 없음)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS hr;

-- ── 명시적 시퀀스 4개 ──────────────────────────────────────────
CREATE SEQUENCE hr.departments_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE hr.employees_seq   START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE hr.projects_seq    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE hr.announcements_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- ── 테이블 ────────────────────────────────────────────────────

-- 1. departments
CREATE TABLE hr.departments (
    dept_id    BIGINT       NOT NULL DEFAULT nextval('hr.departments_seq'),
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(100),
    budget     NUMERIC(15,2) CHECK (budget >= 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_departments PRIMARY KEY (dept_id),
    CONSTRAINT uq_dept_name   UNIQUE (name)
);
ALTER SEQUENCE hr.departments_seq OWNED BY hr.departments.dept_id;

-- 2. job_titles
CREATE TABLE hr.job_titles (
    job_id     BIGSERIAL    NOT NULL,
    title      VARCHAR(100) NOT NULL,
    grade      SMALLINT     NOT NULL CHECK (grade BETWEEN 1 AND 10),
    min_salary NUMERIC(12,2) NOT NULL CHECK (min_salary > 0),
    max_salary NUMERIC(12,2) NOT NULL CHECK (max_salary >= min_salary),
    CONSTRAINT pk_job_titles PRIMARY KEY (job_id),
    CONSTRAINT uq_job_title  UNIQUE (title)
);

-- 3. employees
CREATE TABLE hr.employees (
    emp_id     BIGINT       NOT NULL DEFAULT nextval('hr.employees_seq'),
    dept_id    BIGINT       NOT NULL,
    job_id     BIGINT       NOT NULL,
    first_name VARCHAR(50)  NOT NULL,
    last_name  VARCHAR(50)  NOT NULL,
    email      VARCHAR(150) NOT NULL,
    hire_date  DATE         NOT NULL DEFAULT CURRENT_DATE,
    salary     NUMERIC(12,2) NOT NULL CHECK (salary > 0),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_employees  PRIMARY KEY (emp_id),
    CONSTRAINT uq_emp_email  UNIQUE (email)
);
ALTER SEQUENCE hr.employees_seq OWNED BY hr.employees.emp_id;

-- 4. salary_history
CREATE TABLE hr.salary_history (
    history_id BIGSERIAL    NOT NULL,
    emp_id     BIGINT       NOT NULL,
    old_salary NUMERIC(12,2) NOT NULL CHECK (old_salary > 0),
    new_salary NUMERIC(12,2) NOT NULL CHECK (new_salary > 0),
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by VARCHAR(100) NOT NULL,
    CONSTRAINT pk_salary_history PRIMARY KEY (history_id)
);

-- 5. projects
CREATE TABLE hr.projects (
    project_id  BIGINT       NOT NULL DEFAULT nextval('hr.projects_seq'),
    name        VARCHAR(200) NOT NULL,
    dept_id     BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED','ON_HOLD')),
    start_date  DATE         NOT NULL,
    end_date    DATE,
    budget      NUMERIC(15,2) CHECK (budget >= 0),
    CONSTRAINT pk_projects   PRIMARY KEY (project_id),
    CONSTRAINT uq_proj_name  UNIQUE (name)
);
ALTER SEQUENCE hr.projects_seq OWNED BY hr.projects.project_id;

-- 6. project_assignments
CREATE TABLE hr.project_assignments (
    assign_id   BIGSERIAL    NOT NULL,
    project_id  BIGINT       NOT NULL,
    emp_id      BIGINT       NOT NULL,
    role        VARCHAR(100),
    assigned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_project_assignments PRIMARY KEY (assign_id),
    CONSTRAINT uq_proj_emp UNIQUE (project_id, emp_id)
);

-- 7. leave_requests
CREATE TABLE hr.leave_requests (
    leave_id    BIGSERIAL    NOT NULL,
    emp_id      BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('ANNUAL','SICK','MATERNITY','UNPAID')),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    approved_by BIGINT,
    CONSTRAINT pk_leave_requests PRIMARY KEY (leave_id)
);

-- 8. attendance
CREATE TABLE hr.attendance (
    attend_id  BIGSERIAL   NOT NULL,
    emp_id     BIGINT      NOT NULL,
    work_date  DATE        NOT NULL,
    check_in   TIME,
    check_out  TIME,
    status     VARCHAR(20) NOT NULL DEFAULT 'PRESENT' CHECK (status IN ('PRESENT','ABSENT','LATE','HALF')),
    CONSTRAINT pk_attendance        PRIMARY KEY (attend_id),
    CONSTRAINT uq_attend_emp_date   UNIQUE (emp_id, work_date)
);

-- 9. performance_reviews
CREATE TABLE hr.performance_reviews (
    review_id   BIGSERIAL    NOT NULL,
    emp_id      BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    period      VARCHAR(20)  NOT NULL,
    score       SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     TEXT,
    reviewed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_performance_reviews PRIMARY KEY (review_id)
);

-- 10. training_courses
CREATE TABLE hr.training_courses (
    course_id      BIGSERIAL    NOT NULL,
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50),
    duration_hours SMALLINT     NOT NULL CHECK (duration_hours > 0),
    instructor     VARCHAR(100),
    CONSTRAINT pk_training_courses PRIMARY KEY (course_id)
);

-- 11. training_enrollments
CREATE TABLE hr.training_enrollments (
    enroll_id    BIGSERIAL    NOT NULL,
    course_id    BIGINT       NOT NULL,
    emp_id       BIGINT       NOT NULL,
    enrolled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    score        SMALLINT     CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT pk_training_enrollments PRIMARY KEY (enroll_id),
    CONSTRAINT uq_enroll_course_emp    UNIQUE (course_id, emp_id)
);

-- 12. benefits
CREATE TABLE hr.benefits (
    benefit_id     BIGSERIAL    NOT NULL,
    name           VARCHAR(100) NOT NULL,
    type           VARCHAR(50)  NOT NULL,
    description    TEXT,
    monthly_amount NUMERIC(10,2) NOT NULL CHECK (monthly_amount >= 0),
    CONSTRAINT pk_benefits  PRIMARY KEY (benefit_id),
    CONSTRAINT uq_ben_name  UNIQUE (name)
);

-- 13. employee_benefits
CREATE TABLE hr.employee_benefits (
    eb_id      BIGSERIAL NOT NULL,
    emp_id     BIGINT    NOT NULL,
    benefit_id BIGINT    NOT NULL,
    start_date DATE      NOT NULL,
    end_date   DATE,
    CONSTRAINT pk_employee_benefits PRIMARY KEY (eb_id),
    CONSTRAINT uq_emp_benefit UNIQUE (emp_id, benefit_id, start_date)
);

-- 14. assets
CREATE TABLE hr.assets (
    asset_id      BIGSERIAL    NOT NULL,
    name          VARCHAR(200) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    serial_no     VARCHAR(100),
    purchase_date DATE         NOT NULL,
    value         NUMERIC(12,2) NOT NULL CHECK (value >= 0),
    CONSTRAINT pk_assets        PRIMARY KEY (asset_id),
    CONSTRAINT uq_asset_serial  UNIQUE (serial_no)
);

-- 15. asset_assignments
CREATE TABLE hr.asset_assignments (
    aa_id       BIGSERIAL    NOT NULL,
    asset_id    BIGINT       NOT NULL,
    emp_id      BIGINT       NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    returned_at TIMESTAMPTZ,
    CONSTRAINT pk_asset_assignments PRIMARY KEY (aa_id)
);

-- 16. announcements
CREATE TABLE hr.announcements (
    ann_id       BIGINT       NOT NULL DEFAULT nextval('hr.announcements_seq'),
    title        VARCHAR(300) NOT NULL,
    content      TEXT         NOT NULL,
    author_id    BIGINT       NOT NULL,
    published_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_pinned    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_announcements PRIMARY KEY (ann_id)
);
ALTER SEQUENCE hr.announcements_seq OWNED BY hr.announcements.ann_id;

-- 17. documents
CREATE TABLE hr.documents (
    doc_id     BIGSERIAL    NOT NULL,
    title      VARCHAR(300) NOT NULL,
    category   VARCHAR(50),
    file_path  VARCHAR(500) NOT NULL,
    owner_id   BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_documents PRIMARY KEY (doc_id)
);

-- 18. expense_reports
CREATE TABLE hr.expense_reports (
    report_id    BIGSERIAL    NOT NULL,
    emp_id       BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    submitted_at TIMESTAMPTZ,
    approved_by  BIGINT,
    CONSTRAINT pk_expense_reports PRIMARY KEY (report_id)
);

-- 19. expense_items
CREATE TABLE hr.expense_items (
    item_id     BIGSERIAL    NOT NULL,
    report_id   BIGINT       NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    amount      NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(300),
    receipt_no  VARCHAR(100),
    CONSTRAINT pk_expense_items PRIMARY KEY (item_id)
);

-- 20. audit_logs
CREATE TABLE hr.audit_logs (
    log_id     BIGSERIAL    NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    operation  VARCHAR(10)  NOT NULL CHECK (operation IN ('INSERT','UPDATE','DELETE')),
    row_id     BIGINT       NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    detail     JSONB,
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id)
);

-- ── 인덱스 ────────────────────────────────────────────────────
CREATE INDEX idx_employees_dept      ON hr.employees(dept_id);
CREATE INDEX idx_employees_active    ON hr.employees(is_active);
CREATE INDEX idx_employees_job       ON hr.employees(job_id);
CREATE INDEX idx_proj_assign_project ON hr.project_assignments(project_id);
CREATE INDEX idx_proj_assign_emp     ON hr.project_assignments(emp_id);
CREATE INDEX idx_attendance_emp_date ON hr.attendance(emp_id, work_date);
CREATE INDEX idx_salary_history_emp  ON hr.salary_history(emp_id);
CREATE INDEX idx_audit_logs_table    ON hr.audit_logs(table_name);
CREATE INDEX idx_audit_logs_time     ON hr.audit_logs(changed_at);

-- ── 테이블·컬럼 한글 코멘트 ──────────────────────────────────

COMMENT ON TABLE hr.departments IS '부서';
COMMENT ON COLUMN hr.departments.dept_id    IS '부서 ID';
COMMENT ON COLUMN hr.departments.name       IS '부서명';
COMMENT ON COLUMN hr.departments.location   IS '소재지';
COMMENT ON COLUMN hr.departments.budget     IS '연간 예산';
COMMENT ON COLUMN hr.departments.created_at IS '생성일시';

COMMENT ON TABLE hr.job_titles IS '직무';
COMMENT ON COLUMN hr.job_titles.job_id     IS '직무 ID';
COMMENT ON COLUMN hr.job_titles.title      IS '직무명';
COMMENT ON COLUMN hr.job_titles.grade      IS '직급 (1~10)';
COMMENT ON COLUMN hr.job_titles.min_salary IS '최저 급여';
COMMENT ON COLUMN hr.job_titles.max_salary IS '최고 급여';

COMMENT ON TABLE hr.employees IS '직원';
COMMENT ON COLUMN hr.employees.emp_id     IS '직원 ID';
COMMENT ON COLUMN hr.employees.dept_id    IS '소속 부서 ID';
COMMENT ON COLUMN hr.employees.job_id     IS '직무 ID';
COMMENT ON COLUMN hr.employees.first_name IS '이름';
COMMENT ON COLUMN hr.employees.last_name  IS '성';
COMMENT ON COLUMN hr.employees.email      IS '이메일';
COMMENT ON COLUMN hr.employees.hire_date  IS '입사일';
COMMENT ON COLUMN hr.employees.salary     IS '월 급여';
COMMENT ON COLUMN hr.employees.is_active  IS '재직 여부';

COMMENT ON TABLE hr.salary_history IS '급여 변경 이력';
COMMENT ON COLUMN hr.salary_history.history_id  IS '이력 ID';
COMMENT ON COLUMN hr.salary_history.emp_id      IS '직원 ID';
COMMENT ON COLUMN hr.salary_history.old_salary  IS '변경 전 급여';
COMMENT ON COLUMN hr.salary_history.new_salary  IS '변경 후 급여';
COMMENT ON COLUMN hr.salary_history.changed_at  IS '변경일시';
COMMENT ON COLUMN hr.salary_history.changed_by  IS '변경자';

COMMENT ON TABLE hr.projects IS '프로젝트';
COMMENT ON COLUMN hr.projects.project_id IS '프로젝트 ID';
COMMENT ON COLUMN hr.projects.name       IS '프로젝트명';
COMMENT ON COLUMN hr.projects.dept_id   IS '담당 부서 ID';
COMMENT ON COLUMN hr.projects.status    IS '상태 (ACTIVE/CLOSED/ON_HOLD)';
COMMENT ON COLUMN hr.projects.start_date IS '시작일';
COMMENT ON COLUMN hr.projects.end_date   IS '종료일';
COMMENT ON COLUMN hr.projects.budget     IS '예산';

COMMENT ON TABLE hr.project_assignments IS '프로젝트 투입 인원';
COMMENT ON COLUMN hr.project_assignments.assign_id   IS '투입 ID';
COMMENT ON COLUMN hr.project_assignments.project_id  IS '프로젝트 ID';
COMMENT ON COLUMN hr.project_assignments.emp_id      IS '직원 ID';
COMMENT ON COLUMN hr.project_assignments.role        IS '역할';
COMMENT ON COLUMN hr.project_assignments.assigned_at IS '투입일시';

COMMENT ON TABLE hr.leave_requests IS '휴가 신청';
COMMENT ON COLUMN hr.leave_requests.leave_id    IS '휴가 신청 ID';
COMMENT ON COLUMN hr.leave_requests.emp_id      IS '직원 ID';
COMMENT ON COLUMN hr.leave_requests.type        IS '휴가 종류 (ANNUAL/SICK/MATERNITY/UNPAID)';
COMMENT ON COLUMN hr.leave_requests.start_date  IS '휴가 시작일';
COMMENT ON COLUMN hr.leave_requests.end_date    IS '휴가 종료일';
COMMENT ON COLUMN hr.leave_requests.status      IS '승인 상태 (PENDING/APPROVED/REJECTED)';
COMMENT ON COLUMN hr.leave_requests.approved_by IS '승인자 ID';

COMMENT ON TABLE hr.attendance IS '근태 기록';
COMMENT ON COLUMN hr.attendance.attend_id IS '근태 ID';
COMMENT ON COLUMN hr.attendance.emp_id    IS '직원 ID';
COMMENT ON COLUMN hr.attendance.work_date IS '근무일';
COMMENT ON COLUMN hr.attendance.check_in  IS '출근 시각';
COMMENT ON COLUMN hr.attendance.check_out IS '퇴근 시각';
COMMENT ON COLUMN hr.attendance.status    IS '근태 상태 (PRESENT/ABSENT/LATE/HALF)';

COMMENT ON TABLE hr.performance_reviews IS '인사 평가';
COMMENT ON COLUMN hr.performance_reviews.review_id   IS '평가 ID';
COMMENT ON COLUMN hr.performance_reviews.emp_id      IS '피평가자 ID';
COMMENT ON COLUMN hr.performance_reviews.reviewer_id IS '평가자 ID';
COMMENT ON COLUMN hr.performance_reviews.period      IS '평가 기간 (예: 2024-H1)';
COMMENT ON COLUMN hr.performance_reviews.score       IS '평가 점수 (1~5)';
COMMENT ON COLUMN hr.performance_reviews.comment     IS '평가 의견';
COMMENT ON COLUMN hr.performance_reviews.reviewed_at IS '평가일시';

COMMENT ON TABLE hr.training_courses IS '교육 과정';
COMMENT ON COLUMN hr.training_courses.course_id      IS '과정 ID';
COMMENT ON COLUMN hr.training_courses.title          IS '과정명';
COMMENT ON COLUMN hr.training_courses.category       IS '분류';
COMMENT ON COLUMN hr.training_courses.duration_hours IS '교육 시간 (시간)';
COMMENT ON COLUMN hr.training_courses.instructor     IS '강사명';

COMMENT ON TABLE hr.training_enrollments IS '교육 수강 내역';
COMMENT ON COLUMN hr.training_enrollments.enroll_id    IS '수강 ID';
COMMENT ON COLUMN hr.training_enrollments.course_id    IS '과정 ID';
COMMENT ON COLUMN hr.training_enrollments.emp_id       IS '직원 ID';
COMMENT ON COLUMN hr.training_enrollments.enrolled_at  IS '수강 신청일시';
COMMENT ON COLUMN hr.training_enrollments.completed_at IS '수료일시';
COMMENT ON COLUMN hr.training_enrollments.score        IS '수료 점수 (0~100)';

COMMENT ON TABLE hr.benefits IS '복리후생 항목';
COMMENT ON COLUMN hr.benefits.benefit_id     IS '복리후생 ID';
COMMENT ON COLUMN hr.benefits.name           IS '항목명';
COMMENT ON COLUMN hr.benefits.type           IS '유형';
COMMENT ON COLUMN hr.benefits.description    IS '설명';
COMMENT ON COLUMN hr.benefits.monthly_amount IS '월 지급액';

COMMENT ON TABLE hr.employee_benefits IS '직원별 복리후생 적용 내역';
COMMENT ON COLUMN hr.employee_benefits.eb_id       IS '적용 ID';
COMMENT ON COLUMN hr.employee_benefits.emp_id      IS '직원 ID';
COMMENT ON COLUMN hr.employee_benefits.benefit_id  IS '복리후생 ID';
COMMENT ON COLUMN hr.employee_benefits.start_date  IS '적용 시작일';
COMMENT ON COLUMN hr.employee_benefits.end_date    IS '적용 종료일';

COMMENT ON TABLE hr.assets IS '자산';
COMMENT ON COLUMN hr.assets.asset_id      IS '자산 ID';
COMMENT ON COLUMN hr.assets.name          IS '자산명';
COMMENT ON COLUMN hr.assets.category      IS '분류';
COMMENT ON COLUMN hr.assets.serial_no     IS '시리얼 번호';
COMMENT ON COLUMN hr.assets.purchase_date IS '구매일';
COMMENT ON COLUMN hr.assets.value         IS '취득 원가';

COMMENT ON TABLE hr.asset_assignments IS '자산 불출 내역';
COMMENT ON COLUMN hr.asset_assignments.aa_id       IS '불출 ID';
COMMENT ON COLUMN hr.asset_assignments.asset_id    IS '자산 ID';
COMMENT ON COLUMN hr.asset_assignments.emp_id      IS '사용자 직원 ID';
COMMENT ON COLUMN hr.asset_assignments.assigned_at IS '불출일시';
COMMENT ON COLUMN hr.asset_assignments.returned_at IS '반납일시';

COMMENT ON TABLE hr.announcements IS '공지사항';
COMMENT ON COLUMN hr.announcements.ann_id       IS '공지 ID';
COMMENT ON COLUMN hr.announcements.title        IS '제목';
COMMENT ON COLUMN hr.announcements.content      IS '내용';
COMMENT ON COLUMN hr.announcements.author_id    IS '작성자 직원 ID';
COMMENT ON COLUMN hr.announcements.published_at IS '게시일시';
COMMENT ON COLUMN hr.announcements.is_pinned    IS '상단 고정 여부';

COMMENT ON TABLE hr.documents IS '문서';
COMMENT ON COLUMN hr.documents.doc_id     IS '문서 ID';
COMMENT ON COLUMN hr.documents.title      IS '문서 제목';
COMMENT ON COLUMN hr.documents.category   IS '분류';
COMMENT ON COLUMN hr.documents.file_path  IS '파일 경로';
COMMENT ON COLUMN hr.documents.owner_id   IS '소유자 직원 ID';
COMMENT ON COLUMN hr.documents.created_at IS '등록일시';

COMMENT ON TABLE hr.expense_reports IS '경비 보고서';
COMMENT ON COLUMN hr.expense_reports.report_id    IS '보고서 ID';
COMMENT ON COLUMN hr.expense_reports.emp_id       IS '신청자 직원 ID';
COMMENT ON COLUMN hr.expense_reports.title        IS '보고서 제목';
COMMENT ON COLUMN hr.expense_reports.status       IS '처리 상태 (DRAFT/SUBMITTED/APPROVED/REJECTED)';
COMMENT ON COLUMN hr.expense_reports.submitted_at IS '제출일시';
COMMENT ON COLUMN hr.expense_reports.approved_by  IS '승인자 ID';

COMMENT ON TABLE hr.expense_items IS '경비 항목';
COMMENT ON COLUMN hr.expense_items.item_id     IS '항목 ID';
COMMENT ON COLUMN hr.expense_items.report_id   IS '경비 보고서 ID';
COMMENT ON COLUMN hr.expense_items.category    IS '항목 분류';
COMMENT ON COLUMN hr.expense_items.amount      IS '금액';
COMMENT ON COLUMN hr.expense_items.description IS '설명';
COMMENT ON COLUMN hr.expense_items.receipt_no  IS '영수증 번호';

COMMENT ON TABLE hr.audit_logs IS '변경 감사 로그';
COMMENT ON COLUMN hr.audit_logs.log_id     IS '로그 ID';
COMMENT ON COLUMN hr.audit_logs.table_name IS '변경된 테이블명';
COMMENT ON COLUMN hr.audit_logs.operation  IS '작업 종류 (INSERT/UPDATE/DELETE)';
COMMENT ON COLUMN hr.audit_logs.row_id     IS '변경된 행 ID';
COMMENT ON COLUMN hr.audit_logs.changed_by IS '변경자';
COMMENT ON COLUMN hr.audit_logs.changed_at IS '변경일시';
COMMENT ON COLUMN hr.audit_logs.detail     IS '변경 상세 내용 (JSONB)';

-- ── plpgsql 함수 3개 ────────────────────────────────────────

-- 함수1: 부서 재직자 수
CREATE OR REPLACE FUNCTION hr.get_dept_headcount(p_dept_id BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE v_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM hr.employees
    WHERE dept_id = p_dept_id AND is_active = TRUE;
    RETURN v_count;
END;
$$;

-- 함수2: 근속연수 (소수점)
CREATE OR REPLACE FUNCTION hr.get_tenure_years(p_emp_id BIGINT)
RETURNS NUMERIC
LANGUAGE plpgsql AS $$
DECLARE
    v_hire DATE;
BEGIN
    SELECT hire_date INTO v_hire FROM hr.employees WHERE emp_id = p_emp_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    RETURN ROUND(EXTRACT(EPOCH FROM (CURRENT_DATE - v_hire)) / 86400.0 / 365.25, 2);
END;
$$;

-- 함수3: 연봉 계산 (월급 * 12)
CREATE OR REPLACE FUNCTION hr.calc_annual_salary(p_emp_id BIGINT)
RETURNS NUMERIC
LANGUAGE plpgsql AS $$
DECLARE v_salary NUMERIC;
BEGIN
    SELECT salary INTO v_salary FROM hr.employees WHERE emp_id = p_emp_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    RETURN v_salary * 12;
END;
$$;

-- ── plpgsql 프로시저 4개 ────────────────────────────────────

-- 프로시저1: 급여 인상 + salary_history 기록
CREATE OR REPLACE PROCEDURE hr.raise_salary(
    p_emp_id     BIGINT,
    p_raise_pct  NUMERIC,
    p_changed_by VARCHAR DEFAULT 'system'
)
LANGUAGE plpgsql AS $$
DECLARE
    v_old NUMERIC(12,2);
    v_new NUMERIC(12,2);
BEGIN
    SELECT salary INTO v_old FROM hr.employees WHERE emp_id = p_emp_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Employee % not found', p_emp_id;
    END IF;
    v_new := ROUND(v_old * (1 + p_raise_pct / 100.0), 2);
    UPDATE hr.employees SET salary = v_new WHERE emp_id = p_emp_id;
    INSERT INTO hr.salary_history(emp_id, old_salary, new_salary, changed_by)
    VALUES (p_emp_id, v_old, v_new, p_changed_by);
END;
$$;

-- 프로시저2: 부서 이동 + audit_logs 기록
CREATE OR REPLACE PROCEDURE hr.transfer_employee(
    p_emp_id      BIGINT,
    p_new_dept_id BIGINT,
    p_changed_by  VARCHAR DEFAULT 'system'
)
LANGUAGE plpgsql AS $$
DECLARE
    v_old_dept BIGINT;
BEGIN
    SELECT dept_id INTO v_old_dept FROM hr.employees WHERE emp_id = p_emp_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Employee % not found', p_emp_id;
    END IF;
    UPDATE hr.employees SET dept_id = p_new_dept_id WHERE emp_id = p_emp_id;
    INSERT INTO hr.audit_logs(table_name, operation, row_id, changed_by, detail)
    VALUES ('employees', 'UPDATE', p_emp_id, p_changed_by,
            jsonb_build_object('old_dept_id', v_old_dept, 'new_dept_id', p_new_dept_id));
END;
$$;

-- 프로시저3: 프로젝트 종료 처리
CREATE OR REPLACE PROCEDURE hr.close_project(
    p_project_id BIGINT,
    p_closed_by  VARCHAR DEFAULT 'system'
)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE hr.projects SET status = 'CLOSED', end_date = CURRENT_DATE
    WHERE project_id = p_project_id AND status != 'CLOSED';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Project % not found or already closed', p_project_id;
    END IF;
    INSERT INTO hr.audit_logs(table_name, operation, row_id, changed_by, detail)
    VALUES ('projects', 'UPDATE', p_project_id, p_closed_by,
            jsonb_build_object('status', 'CLOSED'));
END;
$$;

-- 프로시저4: 지정일 이전 audit_logs 삭제
CREATE OR REPLACE PROCEDURE hr.archive_old_logs(p_before_date TIMESTAMPTZ)
LANGUAGE plpgsql AS $$
DECLARE v_count BIGINT;
BEGIN
    DELETE FROM hr.audit_logs WHERE changed_at < p_before_date;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Archived % audit log rows before %', v_count, p_before_date;
END;
$$;

-- ── 샘플 데이터 ─────────────────────────────────────────────

-- departments (5건)
INSERT INTO hr.departments (name, location, budget) VALUES
    ('Engineering',  'Seoul',  8000000.00),
    ('Marketing',    'Busan',  3000000.00),
    ('HR',           'Seoul',  2000000.00),
    ('Finance',      'Seoul',  4000000.00),
    ('Operations',   'Incheon', 5000000.00);

-- job_titles (8건)
INSERT INTO hr.job_titles (title, grade, min_salary, max_salary) VALUES
    ('Junior Engineer',   2, 3000000, 4500000),
    ('Senior Engineer',   4, 4500000, 7000000),
    ('Lead Engineer',     6, 6500000, 9500000),
    ('Marketing Analyst', 3, 3500000, 5000000),
    ('Marketing Manager', 5, 5000000, 8000000),
    ('HR Specialist',     3, 3200000, 4800000),
    ('Financial Analyst', 4, 4000000, 6500000),
    ('Operations Manager',5, 5500000, 8500000);

-- employees (20건, dept 1→7건 2→4건 3→3건 4→3건 5→3건, is_active=FALSE 3명)
INSERT INTO hr.employees (dept_id, job_id, first_name, last_name, email, hire_date, salary, is_active) VALUES
    (1, 2, 'Alice',   'Kim',    'alice.kim@example.com',    '2020-03-01', 5800000, TRUE),
    (1, 2, 'Bob',     'Lee',    'bob.lee@example.com',      '2021-07-15', 5200000, TRUE),
    (1, 3, 'Carol',   'Park',   'carol.park@example.com',   '2018-11-01', 7500000, FALSE),
    (1, 1, 'Daniel',  'Choi',   'daniel.choi@example.com',  '2023-02-01', 3800000, TRUE),
    (1, 3, 'Emma',    'Jung',   'emma.jung@example.com',    '2017-05-20', 8200000, TRUE),
    (1, 2, 'Frank',   'Yoon',   'frank.yoon@example.com',   '2022-09-10', 5500000, TRUE),
    (1, 1, 'Grace',   'Shin',   'grace.shin@example.com',   '2024-01-15', 3600000, TRUE),
    (2, 4, 'Henry',   'Oh',     'henry.oh@example.com',     '2019-06-01', 4200000, TRUE),
    (2, 5, 'Irene',   'Han',    'irene.han@example.com',    '2018-03-15', 6300000, TRUE),
    (2, 4, 'Jake',    'Kang',   'jake.kang@example.com',    '2022-04-01', 4000000, FALSE),
    (2, 5, 'Kate',    'Lim',    'kate.lim@example.com',     '2020-08-20', 6000000, TRUE),
    (3, 6, 'Leo',     'Nam',    'leo.nam@example.com',      '2021-01-10', 4100000, TRUE),
    (3, 6, 'Mia',     'Seo',    'mia.seo@example.com',      '2019-11-05', 4400000, TRUE),
    (3, 6, 'Noah',    'Jang',   'noah.jang@example.com',    '2023-07-01', 3700000, FALSE),
    (4, 7, 'Olivia',  'Bae',    'olivia.bae@example.com',   '2020-05-15', 5100000, TRUE),
    (4, 7, 'Peter',   'Song',   'peter.song@example.com',   '2021-12-01', 4900000, TRUE),
    (4, 7, 'Quinn',   'Yoo',    'quinn.yoo@example.com',    '2022-03-20', 4700000, TRUE),
    (5, 8, 'Rachel',  'Hwang',  'rachel.hwang@example.com', '2018-09-01', 7000000, TRUE),
    (5, 8, 'Sam',     'Kwon',   'sam.kwon@example.com',     '2019-04-15', 6500000, TRUE),
    (5, 1, 'Tina',    'Moon',   'tina.moon@example.com',    '2023-10-01', 3500000, TRUE);

-- salary_history (10건)
INSERT INTO hr.salary_history (emp_id, old_salary, new_salary, changed_by) VALUES
    (1,  5500000, 5800000, 'admin'),
    (2,  4800000, 5200000, 'admin'),
    (5,  7800000, 8200000, 'admin'),
    (6,  5000000, 5500000, 'admin'),
    (8,  3900000, 4200000, 'admin'),
    (9,  5900000, 6300000, 'admin'),
    (11, 5600000, 6000000, 'admin'),
    (15, 4700000, 5100000, 'admin'),
    (18, 6500000, 7000000, 'admin'),
    (19, 6000000, 6500000, 'admin');

-- projects (6건)
INSERT INTO hr.projects (name, dept_id, status, start_date, end_date, budget) VALUES
    ('Core Platform Rebuild',   1, 'ACTIVE',   '2024-01-01', NULL,         5000000),
    ('Mobile App v2',           1, 'ACTIVE',   '2024-03-01', NULL,         3000000),
    ('Brand Refresh Campaign',  2, 'ACTIVE',   '2024-02-15', NULL,         1500000),
    ('Q2 Financial Audit',      4, 'CLOSED',   '2024-04-01', '2024-06-30', 800000),
    ('Warehouse Automation',    5, 'ACTIVE',   '2024-05-01', NULL,         4000000),
    ('HR System Migration',     3, 'ON_HOLD',  '2024-03-01', NULL,         1200000);

-- project_assignments (15건)
INSERT INTO hr.project_assignments (project_id, emp_id, role) VALUES
    (1, 1,  'Tech Lead'),
    (1, 2,  'Backend Developer'),
    (1, 4,  'Junior Developer'),
    (1, 5,  'Architect'),
    (1, 6,  'Backend Developer'),
    (2, 2,  'Mobile Developer'),
    (2, 7,  'Junior Developer'),
    (2, 5,  'Architect'),
    (3, 8,  'Campaign Manager'),
    (3, 11, 'Marketing Lead'),
    (4, 15, 'Analyst'),
    (4, 16, 'Analyst'),
    (5, 18, 'Project Manager'),
    (5, 19, 'Operations Lead'),
    (6, 12, 'HR Lead');

-- leave_requests (10건)
INSERT INTO hr.leave_requests (emp_id, type, start_date, end_date, status, approved_by) VALUES
    (1,  'ANNUAL',   '2024-07-15', '2024-07-19', 'APPROVED', 5),
    (2,  'ANNUAL',   '2024-08-05', '2024-08-09', 'APPROVED', 5),
    (4,  'SICK',     '2024-06-03', '2024-06-04', 'APPROVED', 5),
    (7,  'ANNUAL',   '2024-09-02', '2024-09-06', 'PENDING',  NULL),
    (8,  'ANNUAL',   '2024-07-22', '2024-07-26', 'APPROVED', 11),
    (12, 'MATERNITY','2024-06-01', '2024-11-30', 'APPROVED', 13),
    (15, 'ANNUAL',   '2024-08-12', '2024-08-16', 'APPROVED', 17),
    (18, 'SICK',     '2024-07-08', '2024-07-09', 'APPROVED', 18),
    (20, 'ANNUAL',   '2024-10-07', '2024-10-11', 'PENDING',  NULL),
    (6,  'UNPAID',   '2024-09-23', '2024-09-27', 'REJECTED', 5);

-- attendance (30건 — emp 1~10 각 3일치)
INSERT INTO hr.attendance (emp_id, work_date, check_in, check_out, status) VALUES
    (1,  '2024-09-02', '09:02', '18:15', 'PRESENT'),
    (1,  '2024-09-03', '08:55', '18:30', 'PRESENT'),
    (1,  '2024-09-04', '09:45', '18:00', 'LATE'),
    (2,  '2024-09-02', '09:00', '18:00', 'PRESENT'),
    (2,  '2024-09-03', '09:05', '18:10', 'PRESENT'),
    (2,  '2024-09-04', NULL,    NULL,    'ABSENT'),
    (3,  '2024-09-02', '09:10', '18:20', 'PRESENT'),
    (3,  '2024-09-03', '09:00', '18:00', 'PRESENT'),
    (3,  '2024-09-04', '09:00', '18:00', 'PRESENT'),
    (4,  '2024-09-02', '10:05', '18:00', 'LATE'),
    (4,  '2024-09-03', '09:00', '18:00', 'PRESENT'),
    (4,  '2024-09-04', '09:00', '13:00', 'HALF'),
    (5,  '2024-09-02', '08:50', '19:00', 'PRESENT'),
    (5,  '2024-09-03', '08:45', '19:30', 'PRESENT'),
    (5,  '2024-09-04', '08:55', '18:00', 'PRESENT'),
    (6,  '2024-09-02', '09:00', '18:00', 'PRESENT'),
    (6,  '2024-09-03', NULL,    NULL,    'ABSENT'),
    (6,  '2024-09-04', '09:00', '18:00', 'PRESENT'),
    (7,  '2024-09-02', '09:15', '18:00', 'LATE'),
    (7,  '2024-09-03', '09:00', '18:00', 'PRESENT'),
    (7,  '2024-09-04', '09:00', '18:00', 'PRESENT'),
    (8,  '2024-09-02', '09:00', '18:00', 'PRESENT'),
    (8,  '2024-09-03', '09:00', '18:00', 'PRESENT'),
    (8,  '2024-09-04', '09:00', '18:00', 'PRESENT'),
    (9,  '2024-09-02', '08:58', '18:05', 'PRESENT'),
    (9,  '2024-09-03', '09:00', '18:00', 'PRESENT'),
    (9,  '2024-09-04', '09:00', '18:00', 'PRESENT'),
    (10, '2024-09-02', '09:00', '18:00', 'PRESENT'),
    (10, '2024-09-03', '09:30', '18:00', 'LATE'),
    (10, '2024-09-04', NULL,    NULL,    'ABSENT');

-- performance_reviews (12건)
INSERT INTO hr.performance_reviews (emp_id, reviewer_id, period, score, comment) VALUES
    (1,  5,  '2023-H2', 4, 'Excellent delivery on core modules'),
    (2,  5,  '2023-H2', 3, 'Good progress, needs more initiative'),
    (4,  1,  '2023-H2', 3, 'Solid start as junior'),
    (5,  5,  '2023-H2', 5, 'Outstanding architectural decisions'),
    (6,  1,  '2023-H2', 4, 'Reliable contributor'),
    (8,  11, '2023-H2', 3, 'Meets expectations'),
    (9,  11, '2023-H2', 5, 'Exceptional campaign results'),
    (11, 9,  '2023-H2', 4, 'Strong analytical skills'),
    (15, 17, '2023-H2', 4, 'Detailed financial reports'),
    (16, 17, '2023-H2', 3, 'Consistent performance'),
    (18, 18, '2023-H2', 5, 'Transformed warehouse operations'),
    (19, 18, '2023-H2', 4, 'Proactive problem solver');

-- training_courses (5건)
INSERT INTO hr.training_courses (title, category, duration_hours, instructor) VALUES
    ('Clean Code & Refactoring',      'Engineering', 16, 'Dr. Lee Jinho'),
    ('Advanced PostgreSQL Tuning',    'Engineering', 8,  'Kim Sangwoo'),
    ('Data-Driven Marketing',         'Marketing',   12, 'Park Jiyeon'),
    ('Employment Law Basics',         'HR',          6,  'Choi Minji'),
    ('Financial Modeling in Excel',   'Finance',     10, 'Jung Hyunwoo');

-- training_enrollments (10건)
INSERT INTO hr.training_enrollments (course_id, emp_id, enrolled_at, completed_at, score) VALUES
    (1, 1,  '2024-03-01', '2024-03-15', 92),
    (1, 2,  '2024-03-01', '2024-03-15', 85),
    (1, 6,  '2024-03-01', '2024-03-15', 88),
    (2, 1,  '2024-05-10', '2024-05-18', 95),
    (2, 5,  '2024-05-10', '2024-05-18', 98),
    (3, 8,  '2024-04-05', '2024-04-12', 80),
    (3, 11, '2024-04-05', '2024-04-12', 91),
    (4, 12, '2024-06-03', NULL,         NULL),
    (5, 15, '2024-07-01', '2024-07-10', 87),
    (5, 16, '2024-07-01', '2024-07-10', 82);

-- benefits (4건)
INSERT INTO hr.benefits (name, type, description, monthly_amount) VALUES
    ('Transportation Allowance', 'TRANSPORT', '대중교통 이용 지원',     100000),
    ('Meal Allowance',           'MEAL',      '점심 식대 지원',         150000),
    ('Health Insurance Top-up',  'HEALTH',    '건강보험 추가 지원',      80000),
    ('Internet Allowance',       'WELFARE',   '재택근무 인터넷 비용',    50000);

-- employee_benefits (12건)
INSERT INTO hr.employee_benefits (emp_id, benefit_id, start_date, end_date) VALUES
    (1,  1, '2020-03-01', NULL),
    (1,  2, '2020-03-01', NULL),
    (2,  1, '2021-07-15', NULL),
    (2,  2, '2021-07-15', NULL),
    (5,  1, '2017-05-20', NULL),
    (5,  2, '2017-05-20', NULL),
    (5,  3, '2022-01-01', NULL),
    (8,  1, '2019-06-01', NULL),
    (8,  2, '2019-06-01', NULL),
    (15, 1, '2020-05-15', NULL),
    (15, 4, '2023-01-01', NULL),
    (18, 3, '2018-09-01', NULL);

-- assets (10건)
INSERT INTO hr.assets (name, category, serial_no, purchase_date, value) VALUES
    ('MacBook Pro 16" 2023',  'LAPTOP',  'MBP-2023-001', '2023-02-01', 3500000),
    ('MacBook Pro 16" 2023',  'LAPTOP',  'MBP-2023-002', '2023-02-01', 3500000),
    ('MacBook Pro 14" 2022',  'LAPTOP',  'MBP-2022-003', '2022-08-15', 2800000),
    ('Dell Monitor 27"',      'MONITOR', 'DL-MON-001',   '2022-01-10', 600000),
    ('Dell Monitor 27"',      'MONITOR', 'DL-MON-002',   '2022-01-10', 600000),
    ('iPhone 15 Pro',         'MOBILE',  'IPH-2023-001', '2023-10-01', 1500000),
    ('iPhone 15 Pro',         'MOBILE',  'IPH-2023-002', '2023-10-01', 1500000),
    ('Wireless Keyboard Set', 'PERIPH',  'KBD-001',      '2023-01-15', 150000),
    ('Wireless Keyboard Set', 'PERIPH',  'KBD-002',      '2023-01-15', 150000),
    ('Standing Desk',         'FURNITURE','DSK-001',     '2021-05-01', 800000);

-- asset_assignments (8건)
INSERT INTO hr.asset_assignments (asset_id, emp_id, assigned_at, returned_at) VALUES
    (1,  1,  '2023-02-10', NULL),
    (2,  5,  '2023-02-10', NULL),
    (3,  2,  '2022-09-01', NULL),
    (4,  1,  '2022-02-01', NULL),
    (5,  5,  '2022-02-01', NULL),
    (6,  9,  '2023-10-15', NULL),
    (7,  11, '2023-10-15', NULL),
    (10, 18, '2021-06-01', NULL);

-- announcements (5건)
INSERT INTO hr.announcements (title, content, author_id, published_at, is_pinned) VALUES
    ('2024 하반기 성과평가 일정 안내', '하반기 성과평가는 12월 1일부터 시작됩니다.', 12, '2024-09-01', TRUE),
    ('사내 카페테리아 리모델링 공지',   '10월 한 달간 카페테리아 이용이 제한됩니다.', 12, '2024-09-15', FALSE),
    ('개인정보 보호 교육 필수 이수',    '전 직원 10월 31일까지 온라인 교육 필수.',   12, '2024-09-20', TRUE),
    ('2024 연말 시무식 안내',          '12월 27일 오전 10시 대회의실에서 진행.',    12, '2024-09-25', FALSE),
    ('신규 복지 혜택 — 인터넷 수당',   '10월부터 재택근무자 인터넷 수당이 지급.',   12, '2024-10-01', FALSE);

-- documents (8건)
INSERT INTO hr.documents (title, category, file_path, owner_id) VALUES
    ('2024 조직도',                'ORG',      '/docs/org/2024-org-chart.pdf',       12),
    ('Engineering 온보딩 가이드',  'GUIDE',    '/docs/eng/onboarding-guide.pdf',     5),
    ('취업규칙 2024',              'POLICY',   '/docs/hr/work-rules-2024.pdf',       12),
    ('Q2 마케팅 성과 보고서',      'REPORT',   '/docs/mkt/2024-q2-report.pdf',       9),
    ('서버 아키텍처 다이어그램',   'TECH',     '/docs/eng/server-architecture.pdf',  5),
    ('2024 예산 계획서',           'FINANCE',  '/docs/fin/2024-budget-plan.pdf',     15),
    ('재해복구계획 (DR Plan)',      'INFRA',    '/docs/ops/dr-plan-2024.pdf',         18),
    ('급여 체계 안내',             'HR',       '/docs/hr/salary-structure.pdf',      12);

-- expense_reports (6건)
INSERT INTO hr.expense_reports (emp_id, title, status, submitted_at, approved_by) VALUES
    (1,  '2024-08 출장비 정산',      'APPROVED',  '2024-09-03', 5),
    (5,  '2024-08 컨퍼런스 참가비',  'APPROVED',  '2024-09-02', 5),
    (8,  '2024-09 광고 집행비',      'SUBMITTED', '2024-10-01', NULL),
    (15, '2024-09 외부 감사 비용',   'SUBMITTED', '2024-10-02', NULL),
    (2,  '2024-09 교육 재료비',      'DRAFT',     NULL,         NULL),
    (18, '2024-09 물류 장비 수리',   'APPROVED',  '2024-09-30', 18);

-- expense_items (15건)
INSERT INTO hr.expense_items (report_id, category, amount, description, receipt_no) VALUES
    (1, 'TRANSPORT',   85000,  'KTX 서울-부산 왕복',     'RCP-2024-0901'),
    (1, 'ACCOMMODATION', 180000,'부산 호텔 1박',          'RCP-2024-0902'),
    (1, 'MEAL',         45000, '출장 중 식비',            'RCP-2024-0903'),
    (2, 'REGISTRATION', 500000,'AWS Summit 등록비',       'RCP-2024-0801'),
    (2, 'TRANSPORT',    52000, '공항 리무진',             'RCP-2024-0802'),
    (2, 'ACCOMMODATION',220000,'컨퍼런스 호텔 1박',       'RCP-2024-0803'),
    (3, 'ADVERTISING', 2500000,'Google Ads 집행',         'RCP-2024-1001'),
    (3, 'ADVERTISING', 1800000,'Facebook Ads 집행',       'RCP-2024-1002'),
    (4, 'CONSULTING',   900000,'외부 감사법인 자문료',    'RCP-2024-1003'),
    (4, 'MEAL',         78000, '감사 미팅 식비',          'RCP-2024-1004'),
    (5, 'SUPPLIES',     35000, '교육 노트 및 인쇄물',     NULL),
    (5, 'MEAL',         28000, '팀 점심',                 NULL),
    (6, 'REPAIR',       450000,'지게차 부품 교체',        'RCP-2024-0931'),
    (6, 'SUPPLIES',     120000,'창고 소모품',             'RCP-2024-0932'),
    (6, 'TRANSPORT',    65000, '부품 배송비',             'RCP-2024-0933');

-- audit_logs (20건)
INSERT INTO hr.audit_logs (table_name, operation, row_id, changed_by, detail) VALUES
    ('employees',    'INSERT', 20, 'admin',  '{"action": "new hire"}'),
    ('employees',    'UPDATE',  3, 'admin',  '{"is_active": false}'),
    ('employees',    'UPDATE', 10, 'admin',  '{"is_active": false}'),
    ('employees',    'UPDATE', 14, 'admin',  '{"is_active": false}'),
    ('departments',  'INSERT',  5, 'admin',  '{"name": "Operations"}'),
    ('projects',     'INSERT',  6, 'admin',  '{"name": "HR System Migration"}'),
    ('projects',     'UPDATE',  4, 'admin',  '{"status": "CLOSED"}'),
    ('salary_history','INSERT', 1, 'admin',  '{"emp_id": 1, "raise_pct": 5.45}'),
    ('salary_history','INSERT', 2, 'admin',  '{"emp_id": 2, "raise_pct": 8.33}'),
    ('salary_history','INSERT', 5, 'admin',  '{"emp_id": 5, "raise_pct": 5.13}'),
    ('assets',       'INSERT', 10, 'admin',  '{"name": "Standing Desk"}'),
    ('leave_requests','UPDATE',10, 'manager','{"status": "REJECTED"}'),
    ('expense_reports','UPDATE',1,'finance', '{"status": "APPROVED"}'),
    ('expense_reports','UPDATE',2,'finance', '{"status": "APPROVED"}'),
    ('expense_reports','UPDATE',6,'finance', '{"status": "APPROVED"}'),
    ('training_enrollments','INSERT',8,'system','{"course_id": 4, "emp_id": 12}'),
    ('employee_benefits','INSERT',11,'admin', '{"benefit": "Internet Allowance"}'),
    ('employee_benefits','INSERT',12,'admin', '{"benefit": "Health Insurance Top-up"}'),
    ('asset_assignments','INSERT',8,'admin', '{"asset_id": 10, "emp_id": 18}'),
    ('announcements','INSERT',5,'admin',     '{"title": "신규 복지 혜택"}');

-- 시퀀스 현재값 동기화
SELECT setval('hr.departments_seq',  (SELECT MAX(dept_id)    FROM hr.departments));
SELECT setval('hr.employees_seq',    (SELECT MAX(emp_id)     FROM hr.employees));
SELECT setval('hr.projects_seq',     (SELECT MAX(project_id) FROM hr.projects));
SELECT setval('hr.announcements_seq',(SELECT MAX(ann_id)     FROM hr.announcements));
