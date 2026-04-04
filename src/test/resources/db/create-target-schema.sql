-- ============================================================
-- HR 스키마 — 타겟 컨테이너 스키마 생성 (데이터 없음, FK 없음)
-- PK는 BIGSERIAL 사용 (COPY BINARY 외부값 삽입 호환)
-- IF NOT EXISTS로 멱등 처리
-- ============================================================

CREATE SCHEMA IF NOT EXISTS hr;

CREATE TABLE IF NOT EXISTS hr.departments (
    dept_id    BIGSERIAL    NOT NULL,
    name       VARCHAR(100) NOT NULL,
    location   VARCHAR(100),
    budget     NUMERIC(15,2) CHECK (budget >= 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_departments PRIMARY KEY (dept_id),
    CONSTRAINT uq_dept_name   UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hr.job_titles (
    job_id     BIGSERIAL    NOT NULL,
    title      VARCHAR(100) NOT NULL,
    grade      SMALLINT     NOT NULL CHECK (grade BETWEEN 1 AND 10),
    min_salary NUMERIC(12,2) NOT NULL CHECK (min_salary > 0),
    max_salary NUMERIC(12,2) NOT NULL CHECK (max_salary >= min_salary),
    CONSTRAINT pk_job_titles PRIMARY KEY (job_id),
    CONSTRAINT uq_job_title  UNIQUE (title)
);

CREATE TABLE IF NOT EXISTS hr.employees (
    emp_id     BIGSERIAL    NOT NULL,
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

CREATE TABLE IF NOT EXISTS hr.salary_history (
    history_id BIGSERIAL    NOT NULL,
    emp_id     BIGINT       NOT NULL,
    old_salary NUMERIC(12,2) NOT NULL CHECK (old_salary > 0),
    new_salary NUMERIC(12,2) NOT NULL CHECK (new_salary > 0),
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by VARCHAR(100) NOT NULL,
    CONSTRAINT pk_salary_history PRIMARY KEY (history_id)
);

CREATE TABLE IF NOT EXISTS hr.projects (
    project_id  BIGSERIAL    NOT NULL,
    name        VARCHAR(200) NOT NULL,
    dept_id     BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED','ON_HOLD')),
    start_date  DATE         NOT NULL,
    end_date    DATE,
    budget      NUMERIC(15,2) CHECK (budget >= 0),
    CONSTRAINT pk_projects   PRIMARY KEY (project_id),
    CONSTRAINT uq_proj_name  UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hr.project_assignments (
    assign_id   BIGSERIAL    NOT NULL,
    project_id  BIGINT       NOT NULL,
    emp_id      BIGINT       NOT NULL,
    role        VARCHAR(100),
    assigned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_project_assignments PRIMARY KEY (assign_id),
    CONSTRAINT uq_proj_emp UNIQUE (project_id, emp_id)
);

CREATE TABLE IF NOT EXISTS hr.leave_requests (
    leave_id    BIGSERIAL    NOT NULL,
    emp_id      BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('ANNUAL','SICK','MATERNITY','UNPAID')),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    approved_by BIGINT,
    CONSTRAINT pk_leave_requests PRIMARY KEY (leave_id)
);

CREATE TABLE IF NOT EXISTS hr.attendance (
    attend_id  BIGSERIAL   NOT NULL,
    emp_id     BIGINT      NOT NULL,
    work_date  DATE        NOT NULL,
    check_in   TIME,
    check_out  TIME,
    status     VARCHAR(20) NOT NULL DEFAULT 'PRESENT' CHECK (status IN ('PRESENT','ABSENT','LATE','HALF')),
    CONSTRAINT pk_attendance        PRIMARY KEY (attend_id),
    CONSTRAINT uq_attend_emp_date   UNIQUE (emp_id, work_date)
);

CREATE TABLE IF NOT EXISTS hr.performance_reviews (
    review_id   BIGSERIAL    NOT NULL,
    emp_id      BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    period      VARCHAR(20)  NOT NULL,
    score       SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     TEXT,
    reviewed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_performance_reviews PRIMARY KEY (review_id)
);

CREATE TABLE IF NOT EXISTS hr.training_courses (
    course_id      BIGSERIAL    NOT NULL,
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50),
    duration_hours SMALLINT     NOT NULL CHECK (duration_hours > 0),
    instructor     VARCHAR(100),
    CONSTRAINT pk_training_courses PRIMARY KEY (course_id)
);

CREATE TABLE IF NOT EXISTS hr.training_enrollments (
    enroll_id    BIGSERIAL    NOT NULL,
    course_id    BIGINT       NOT NULL,
    emp_id       BIGINT       NOT NULL,
    enrolled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    score        SMALLINT     CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT pk_training_enrollments PRIMARY KEY (enroll_id),
    CONSTRAINT uq_enroll_course_emp    UNIQUE (course_id, emp_id)
);

CREATE TABLE IF NOT EXISTS hr.benefits (
    benefit_id     BIGSERIAL    NOT NULL,
    name           VARCHAR(100) NOT NULL,
    type           VARCHAR(50)  NOT NULL,
    description    TEXT,
    monthly_amount NUMERIC(10,2) NOT NULL CHECK (monthly_amount >= 0),
    CONSTRAINT pk_benefits  PRIMARY KEY (benefit_id),
    CONSTRAINT uq_ben_name  UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hr.employee_benefits (
    eb_id      BIGSERIAL NOT NULL,
    emp_id     BIGINT    NOT NULL,
    benefit_id BIGINT    NOT NULL,
    start_date DATE      NOT NULL,
    end_date   DATE,
    CONSTRAINT pk_employee_benefits PRIMARY KEY (eb_id),
    CONSTRAINT uq_emp_benefit UNIQUE (emp_id, benefit_id, start_date)
);

CREATE TABLE IF NOT EXISTS hr.assets (
    asset_id      BIGSERIAL    NOT NULL,
    name          VARCHAR(200) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    serial_no     VARCHAR(100),
    purchase_date DATE         NOT NULL,
    value         NUMERIC(12,2) NOT NULL CHECK (value >= 0),
    CONSTRAINT pk_assets        PRIMARY KEY (asset_id),
    CONSTRAINT uq_asset_serial  UNIQUE (serial_no)
);

CREATE TABLE IF NOT EXISTS hr.asset_assignments (
    aa_id       BIGSERIAL    NOT NULL,
    asset_id    BIGINT       NOT NULL,
    emp_id      BIGINT       NOT NULL,
    assigned_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    returned_at TIMESTAMPTZ,
    CONSTRAINT pk_asset_assignments PRIMARY KEY (aa_id)
);

CREATE TABLE IF NOT EXISTS hr.announcements (
    ann_id       BIGSERIAL    NOT NULL,
    title        VARCHAR(300) NOT NULL,
    content      TEXT         NOT NULL,
    author_id    BIGINT       NOT NULL,
    published_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_pinned    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_announcements PRIMARY KEY (ann_id)
);

CREATE TABLE IF NOT EXISTS hr.documents (
    doc_id     BIGSERIAL    NOT NULL,
    title      VARCHAR(300) NOT NULL,
    category   VARCHAR(50),
    file_path  VARCHAR(500) NOT NULL,
    owner_id   BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_documents PRIMARY KEY (doc_id)
);

CREATE TABLE IF NOT EXISTS hr.expense_reports (
    report_id    BIGSERIAL    NOT NULL,
    emp_id       BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    submitted_at TIMESTAMPTZ,
    approved_by  BIGINT,
    CONSTRAINT pk_expense_reports PRIMARY KEY (report_id)
);

CREATE TABLE IF NOT EXISTS hr.expense_items (
    item_id     BIGSERIAL    NOT NULL,
    report_id   BIGINT       NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    amount      NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(300),
    receipt_no  VARCHAR(100),
    CONSTRAINT pk_expense_items PRIMARY KEY (item_id)
);

CREATE TABLE IF NOT EXISTS hr.audit_logs (
    log_id     BIGSERIAL    NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    operation  VARCHAR(10)  NOT NULL CHECK (operation IN ('INSERT','UPDATE','DELETE')),
    row_id     BIGINT       NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    detail     JSONB,
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id)
);
