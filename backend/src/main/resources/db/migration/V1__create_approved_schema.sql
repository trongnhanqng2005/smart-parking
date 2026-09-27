CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100),
    password_hash VARCHAR(255),
    full_name VARCHAR(150),
    email VARCHAR(150),
    phone VARCHAR(30),
    status ENUM('ACTIVE', 'LOCKED', 'DISABLED'),
    last_login_at DATETIME,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE roles (
    id BIGINT PRIMARY KEY,
    code VARCHAR(50),
    name VARCHAR(100),
    description VARCHAR(255)
);

CREATE TABLE permissions (
    id BIGINT PRIMARY KEY,
    code VARCHAR(100),
    name VARCHAR(150),
    resource VARCHAR(100),
    action VARCHAR(50),
    description VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id BIGINT,
    role_id BIGINT,
    assigned_at DATETIME,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id BIGINT,
    permission_id BIGINT,
    assigned_at DATETIME,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE apartments (
    id BIGINT PRIMARY KEY,
    apartment_code VARCHAR(50),
    building VARCHAR(100),
    floor_no INT,
    status ENUM('ACTIVE', 'INACTIVE'),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE residents (
    id BIGINT PRIMARY KEY,
    full_name VARCHAR(150),
    identity_number VARCHAR(30),
    date_of_birth DATE,
    phone VARCHAR(30),
    email VARCHAR(100),
    status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED'),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE apartment_memberships (
    id BIGINT PRIMARY KEY,
    apartment_id BIGINT,
    resident_id BIGINT,
    member_role ENUM('HOUSEHOLD_HEAD', 'MEMBER'),
    valid_from DATETIME,
    valid_to DATETIME,
    status ENUM('ACTIVE', 'INACTIVE', 'REVOKED'),
    created_at DATETIME
);

CREATE TABLE vehicle_families (
    id BIGINT PRIMARY KEY,
    code VARCHAR(50),
    name VARCHAR(100)
);

CREATE TABLE vehicle_categories (
    id BIGINT PRIMARY KEY,
    family_id BIGINT,
    code VARCHAR(50),
    name VARCHAR(120),
    description VARCHAR(255)
);

CREATE TABLE vehicles (
    id BIGINT PRIMARY KEY,
    vehicle_category_id BIGINT,
    plate_number VARCHAR(30),
    plate_normalized VARCHAR(30),
    brand VARCHAR(100),
    model VARCHAR(100),
    color VARCHAR(50),
    seat_count INT,
    engine_capacity_cc INT,
    is_electric BOOLEAN,
    status ENUM('ACTIVE', 'INACTIVE', 'BLOCKED'),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE vehicle_resident_relations (
    id BIGINT PRIMARY KEY,
    vehicle_id BIGINT,
    resident_id BIGINT,
    relation_type ENUM('OWNER', 'AUTHORIZED_USER'),
    valid_from DATETIME,
    valid_to DATETIME,
    status ENUM('ACTIVE', 'INACTIVE', 'REVOKED'),
    created_at DATETIME
);

CREATE TABLE cards (
    id BIGINT PRIMARY KEY,
    card_uid VARCHAR(100),
    card_type ENUM('RESIDENT', 'VISITOR'),
    status ENUM('ACTIVE', 'LOCKED', 'LOST', 'CANCELLED', 'REPLACED'),
    issued_at DATETIME,
    expired_at DATETIME,
    replaced_card_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE card_assignments (
    id BIGINT PRIMARY KEY,
    card_id BIGINT,
    resident_id BIGINT,
    assigned_at DATETIME,
    ended_at DATETIME,
    status ENUM('ACTIVE', 'INACTIVE', 'REVOKED'),
    created_at DATETIME
);

CREATE TABLE pricing_versions (
    id BIGINT PRIMARY KEY,
    version_code VARCHAR(50),
    name VARCHAR(150),
    description VARCHAR(500),
    effective_from DATETIME,
    effective_to DATETIME,
    status ENUM('DRAFT', 'ACTIVE', 'INACTIVE'),
    created_by_user_id BIGINT,
    created_at DATETIME
);

CREATE TABLE monthly_rates (
    id BIGINT PRIMARY KEY,
    pricing_version_id BIGINT,
    vehicle_category_id BIGINT,
    duration_days INT,
    amount DECIMAL(14, 2)
);

CREATE TABLE visitor_period_rates (
    id BIGINT PRIMARY KEY,
    pricing_version_id BIGINT,
    vehicle_family_id BIGINT,
    period_type ENUM('DAY', 'NIGHT', 'OVERNIGHT_CYCLE'),
    start_time TIME,
    end_time TIME,
    cycle_minutes INT,
    amount DECIMAL(14, 2)
);

CREATE TABLE visitor_car_rate_rules (
    id BIGINT PRIMARY KEY,
    pricing_version_id BIGINT,
    vehicle_family_id BIGINT,
    base_minutes INT,
    base_fee DECIMAL(14, 2),
    increment_minutes INT,
    increment_fee DECIMAL(14, 2),
    overnight_min_fee DECIMAL(14, 2)
);

CREATE TABLE decision_policy_versions (
    id BIGINT PRIMARY KEY,
    policy_code VARCHAR(50),
    name VARCHAR(150),
    plate_min_confidence DECIMAL(5, 4),
    vehicle_min_confidence DECIMAL(5, 4),
    face_min_similarity DECIMAL(5, 4),
    liveness_min_score DECIMAL(5, 4),
    image_quality_min_score DECIMAL(5, 4),
    effective_from DATETIME,
    effective_to DATETIME,
    status ENUM('DRAFT', 'ACTIVE', 'INACTIVE'),
    created_by_user_id BIGINT,
    created_at DATETIME
);

CREATE TABLE parking_subscriptions (
    id BIGINT PRIMARY KEY,
    vehicle_id BIGINT,
    monthly_rate_id BIGINT,
    valid_from DATETIME,
    valid_until DATETIME,
    status ENUM('PENDING', 'ACTIVE', 'EXPIRED', 'SUSPENDED', 'CANCELLED'),
    created_by_user_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE gate_stations (
    id BIGINT PRIMARY KEY,
    station_code VARCHAR(50),
    name VARCHAR(100),
    hostname VARCHAR(150),
    status ENUM('ACTIVE', 'INACTIVE', 'MAINTENANCE'),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE gate_lanes (
    id BIGINT PRIMARY KEY,
    station_id BIGINT,
    lane_code VARCHAR(50),
    name VARCHAR(100),
    direction ENUM('ENTRY', 'EXIT', 'BOTH'),
    status ENUM('ACTIVE', 'INACTIVE', 'MAINTENANCE')
);

CREATE TABLE work_shifts (
    id BIGINT PRIMARY KEY,
    shift_code VARCHAR(50),
    lane_id BIGINT,
    staff_user_id BIGINT,
    started_at DATETIME,
    ended_at DATETIME,
    opening_cash DECIMAL(14, 2),
    closing_cash DECIMAL(14, 2),
    status ENUM('OPEN', 'CLOSED'),
    confirmed_by_user_id BIGINT,
    confirmed_at DATETIME,
    note VARCHAR(500)
);

CREATE TABLE parking_sessions (
    id BIGINT PRIMARY KEY,
    customer_type ENUM('RESIDENT', 'VISITOR'),
    registered_vehicle_id BIGINT,
    access_card_id BIGINT,
    entry_plate VARCHAR(30),
    entry_vehicle_category_id BIGINT,
    entered_at DATETIME,
    exited_at DATETIME,
    status ENUM('OPEN', 'CLOSED', 'CLOSED_MANUAL', 'CANCELLED', 'SYNC_CONFLICT'),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE gate_events (
    id BIGINT PRIMARY KEY,
    origin_event_id CHAR(36),
    parking_session_id BIGINT,
    lane_id BIGINT,
    work_shift_id BIGINT,
    event_type ENUM('ENTRY', 'EXIT'),
    occurred_at DATETIME,
    card_id BIGINT,
    driver_resident_id BIGINT,
    resolved_vehicle_id BIGINT,
    resolved_vehicle_category_id BIGINT,
    resolved_plate VARCHAR(30),
    plate_resolution_source ENUM('AI', 'MANUAL', 'REGISTERED_DATA'),
    vehicle_resolution_source ENUM('AI', 'MANUAL', 'REGISTERED_DATA'),
    driver_resolution_source ENUM('AI', 'MANUAL', 'REGISTERED_DATA'),
    decision_policy_version_id BIGINT,
    decision ENUM('AUTO_APPROVE', 'MANUAL_REVIEW', 'REJECT'),
    final_outcome ENUM('PENDING', 'ALLOWED', 'REJECTED'),
    decision_reason_code VARCHAR(100),
    decision_note VARCHAR(500),
    operating_mode ENUM('ONLINE', 'OFFLINE'),
    sync_status ENUM('NOT_REQUIRED', 'PENDING', 'SYNCED', 'CONFLICT'),
    sync_conflict_reason VARCHAR(500),
    operator_user_id BIGINT,
    created_at DATETIME
);

CREATE TABLE manual_reviews (
    id BIGINT PRIMARY KEY,
    gate_event_id BIGINT,
    reviewer_user_id BIGINT,
    decision ENUM('APPROVE', 'REJECT'),
    reason_code VARCHAR(100),
    note VARCHAR(1000),
    reviewed_at DATETIME,
    created_at DATETIME
);

CREATE TABLE barrier_actions (
    id BIGINT PRIMARY KEY,
    gate_event_id BIGINT,
    action ENUM('OPEN', 'CLOSE'),
    trigger_source ENUM('AUTO', 'MANUAL'),
    requested_by_user_id BIGINT,
    requested_at DATETIME,
    result ENUM('REQUESTED', 'SUCCESS', 'FAILED'),
    completed_at DATETIME,
    failure_reason VARCHAR(500)
);

CREATE TABLE media_assets (
    id BIGINT PRIMARY KEY,
    storage_path VARCHAR(500),
    mime_type VARCHAR(100),
    file_size_bytes BIGINT,
    sha256 VARCHAR(64),
    captured_at DATETIME,
    created_at DATETIME
);

CREATE TABLE resident_media (
    id BIGINT PRIMARY KEY,
    resident_id BIGINT,
    media_id BIGINT,
    purpose ENUM('CCCD_FACE', 'REGISTRATION_FACE', 'OTHER'),
    created_at DATETIME
);

CREATE TABLE gate_event_media (
    id BIGINT PRIMARY KEY,
    gate_event_id BIGINT,
    media_id BIGINT,
    purpose ENUM('PLATE_IMAGE', 'DRIVER_FACE', 'VEHICLE_IMAGE', 'OVERVIEW_IMAGE'),
    created_at DATETIME
);

CREATE TABLE face_templates (
    id BIGINT PRIMARY KEY,
    resident_id BIGINT,
    source_media_id BIGINT,
    model_version VARCHAR(100),
    template_storage_ref VARCHAR(500),
    created_at DATETIME,
    revoked_at DATETIME
);

CREATE TABLE identity_verifications (
    id BIGINT PRIMARY KEY,
    resident_id BIGINT,
    cccd_media_id BIGINT,
    registration_media_id BIGINT,
    realtime_media_id BIGINT,
    cccd_registration_score DECIMAL(5, 4),
    registration_realtime_score DECIMAL(5, 4),
    cccd_realtime_score DECIMAL(5, 4),
    threshold_used DECIMAL(5, 4),
    result ENUM('PASS', 'REVIEW', 'FAIL'),
    model_version VARCHAR(100),
    verified_by_user_id BIGINT,
    verified_at DATETIME,
    created_at DATETIME
);

CREATE TABLE ai_inference_results (
    id BIGINT PRIMARY KEY,
    gate_event_id BIGINT,
    attempt_no INT,
    status ENUM('SUCCESS', 'PARTIAL', 'FAILED'),
    predicted_plate VARCHAR(30),
    plate_confidence DECIMAL(5, 4),
    predicted_vehicle_family_id BIGINT,
    vehicle_confidence DECIMAL(5, 4),
    matched_face_template_id BIGINT,
    face_similarity DECIMAL(5, 4),
    liveness_score DECIMAL(5, 4),
    image_quality_score DECIMAL(5, 4),
    anpr_model_version VARCHAR(100),
    ocr_model_version VARCHAR(100),
    vehicle_model_version VARCHAR(100),
    face_model_version VARCHAR(100),
    liveness_model_version VARCHAR(100),
    processing_ms INT,
    created_at DATETIME
);

CREATE TABLE charges (
    id BIGINT PRIMARY KEY,
    invoice_no VARCHAR(50),
    parking_subscription_id BIGINT,
    parking_session_id BIGINT,
    pricing_version_id BIGINT,
    total_amount DECIMAL(14, 2),
    status ENUM('UNPAID', 'PAYMENT_PENDING', 'PAID', 'REFUNDED', 'VOID', 'ADJUSTED'),
    created_at DATETIME,
    settled_at DATETIME
);

CREATE TABLE charge_items (
    id BIGINT PRIMARY KEY,
    charge_id BIGINT,
    component_code VARCHAR(100),
    description VARCHAR(255),
    amount DECIMAL(14, 2),
    calculation_detail JSON,
    display_order INT,
    created_at DATETIME
);

CREATE TABLE payments (
    id BIGINT PRIMARY KEY,
    charge_id BIGINT,
    payment_type ENUM('PAYMENT', 'REFUND', 'ADJUSTMENT'),
    payment_method ENUM('CASH', 'BANK_TRANSFER', 'QR', 'OTHER'),
    amount DECIMAL(14, 2),
    status ENUM('PENDING', 'SUCCESS', 'FAILED', 'VOIDED'),
    external_reference VARCHAR(150),
    receipt_no VARCHAR(50),
    original_payment_id BIGINT,
    work_shift_id BIGINT,
    processed_by_user_id BIGINT,
    paid_at DATETIME,
    note VARCHAR(500),
    created_at DATETIME
);

CREATE TABLE alerts (
    id BIGINT PRIMARY KEY,
    gate_event_id BIGINT,
    parking_session_id BIGINT,
    alert_code VARCHAR(100),
    severity ENUM('INFO', 'WARNING', 'CRITICAL'),
    status ENUM('OPEN', 'ACKNOWLEDGED', 'RESOLVED'),
    message VARCHAR(1000),
    created_at DATETIME,
    acknowledged_by_user_id BIGINT,
    acknowledged_at DATETIME,
    resolved_by_user_id BIGINT,
    resolved_at DATETIME
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY,
    actor_user_id BIGINT,
    action VARCHAR(100),
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    old_data JSON,
    new_data JSON,
    request_id VARCHAR(100),
    client_ip VARCHAR(50),
    created_at DATETIME
);

ALTER TABLE user_roles
    ADD FOREIGN KEY (user_id) REFERENCES users (id),
    ADD FOREIGN KEY (role_id) REFERENCES roles (id);

ALTER TABLE role_permissions
    ADD FOREIGN KEY (role_id) REFERENCES roles (id),
    ADD FOREIGN KEY (permission_id) REFERENCES permissions (id);

ALTER TABLE work_shifts
    ADD FOREIGN KEY (staff_user_id) REFERENCES users (id),
    ADD FOREIGN KEY (confirmed_by_user_id) REFERENCES users (id);

ALTER TABLE audit_logs
    ADD FOREIGN KEY (actor_user_id) REFERENCES users (id);

ALTER TABLE apartment_memberships
    ADD FOREIGN KEY (apartment_id) REFERENCES apartments (id),
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id);

ALTER TABLE vehicle_categories
    ADD FOREIGN KEY (family_id) REFERENCES vehicle_families (id);

ALTER TABLE vehicles
    ADD FOREIGN KEY (vehicle_category_id) REFERENCES vehicle_categories (id);

ALTER TABLE vehicle_resident_relations
    ADD FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id);

ALTER TABLE parking_subscriptions
    ADD FOREIGN KEY (vehicle_id) REFERENCES vehicles (id);

ALTER TABLE parking_sessions
    ADD FOREIGN KEY (registered_vehicle_id) REFERENCES vehicles (id),
    ADD FOREIGN KEY (entry_vehicle_category_id) REFERENCES vehicle_categories (id);

ALTER TABLE gate_events
    ADD FOREIGN KEY (driver_resident_id) REFERENCES residents (id),
    ADD FOREIGN KEY (resolved_vehicle_id) REFERENCES vehicles (id),
    ADD FOREIGN KEY (resolved_vehicle_category_id) REFERENCES vehicle_categories (id);

ALTER TABLE cards
    ADD FOREIGN KEY (replaced_card_id) REFERENCES cards (id);

ALTER TABLE card_assignments
    ADD FOREIGN KEY (card_id) REFERENCES cards (id),
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id);

ALTER TABLE parking_sessions
    ADD FOREIGN KEY (access_card_id) REFERENCES cards (id);

ALTER TABLE gate_events
    ADD FOREIGN KEY (card_id) REFERENCES cards (id);

ALTER TABLE pricing_versions
    ADD FOREIGN KEY (created_by_user_id) REFERENCES users (id);

ALTER TABLE monthly_rates
    ADD FOREIGN KEY (pricing_version_id) REFERENCES pricing_versions (id),
    ADD FOREIGN KEY (vehicle_category_id) REFERENCES vehicle_categories (id);

ALTER TABLE visitor_period_rates
    ADD FOREIGN KEY (pricing_version_id) REFERENCES pricing_versions (id),
    ADD FOREIGN KEY (vehicle_family_id) REFERENCES vehicle_families (id);

ALTER TABLE visitor_car_rate_rules
    ADD FOREIGN KEY (pricing_version_id) REFERENCES pricing_versions (id),
    ADD FOREIGN KEY (vehicle_family_id) REFERENCES vehicle_families (id);

ALTER TABLE parking_subscriptions
    ADD FOREIGN KEY (monthly_rate_id) REFERENCES monthly_rates (id),
    ADD FOREIGN KEY (created_by_user_id) REFERENCES users (id);

ALTER TABLE decision_policy_versions
    ADD FOREIGN KEY (created_by_user_id) REFERENCES users (id);

ALTER TABLE gate_events
    ADD FOREIGN KEY (decision_policy_version_id) REFERENCES decision_policy_versions (id);

ALTER TABLE gate_lanes
    ADD FOREIGN KEY (station_id) REFERENCES gate_stations (id);

ALTER TABLE work_shifts
    ADD FOREIGN KEY (lane_id) REFERENCES gate_lanes (id);

ALTER TABLE gate_events
    ADD FOREIGN KEY (parking_session_id) REFERENCES parking_sessions (id),
    ADD FOREIGN KEY (lane_id) REFERENCES gate_lanes (id),
    ADD FOREIGN KEY (work_shift_id) REFERENCES work_shifts (id),
    ADD FOREIGN KEY (operator_user_id) REFERENCES users (id);

ALTER TABLE manual_reviews
    ADD FOREIGN KEY (gate_event_id) REFERENCES gate_events (id),
    ADD FOREIGN KEY (reviewer_user_id) REFERENCES users (id);

ALTER TABLE barrier_actions
    ADD FOREIGN KEY (gate_event_id) REFERENCES gate_events (id),
    ADD FOREIGN KEY (requested_by_user_id) REFERENCES users (id);

ALTER TABLE alerts
    ADD FOREIGN KEY (gate_event_id) REFERENCES gate_events (id),
    ADD FOREIGN KEY (parking_session_id) REFERENCES parking_sessions (id),
    ADD FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (id),
    ADD FOREIGN KEY (resolved_by_user_id) REFERENCES users (id);

ALTER TABLE resident_media
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id),
    ADD FOREIGN KEY (media_id) REFERENCES media_assets (id);

ALTER TABLE gate_event_media
    ADD FOREIGN KEY (gate_event_id) REFERENCES gate_events (id),
    ADD FOREIGN KEY (media_id) REFERENCES media_assets (id);

ALTER TABLE face_templates
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id),
    ADD FOREIGN KEY (source_media_id) REFERENCES media_assets (id);

ALTER TABLE identity_verifications
    ADD FOREIGN KEY (resident_id) REFERENCES residents (id),
    ADD FOREIGN KEY (cccd_media_id) REFERENCES media_assets (id),
    ADD FOREIGN KEY (registration_media_id) REFERENCES media_assets (id),
    ADD FOREIGN KEY (realtime_media_id) REFERENCES media_assets (id),
    ADD FOREIGN KEY (verified_by_user_id) REFERENCES users (id);

ALTER TABLE ai_inference_results
    ADD FOREIGN KEY (gate_event_id) REFERENCES gate_events (id),
    ADD FOREIGN KEY (predicted_vehicle_family_id) REFERENCES vehicle_families (id),
    ADD FOREIGN KEY (matched_face_template_id) REFERENCES face_templates (id);

ALTER TABLE charges
    ADD FOREIGN KEY (parking_subscription_id) REFERENCES parking_subscriptions (id),
    ADD FOREIGN KEY (parking_session_id) REFERENCES parking_sessions (id),
    ADD FOREIGN KEY (pricing_version_id) REFERENCES pricing_versions (id);

ALTER TABLE charge_items
    ADD FOREIGN KEY (charge_id) REFERENCES charges (id);

ALTER TABLE payments
    ADD FOREIGN KEY (charge_id) REFERENCES charges (id),
    ADD FOREIGN KEY (original_payment_id) REFERENCES payments (id),
    ADD FOREIGN KEY (work_shift_id) REFERENCES work_shifts (id),
    ADD FOREIGN KEY (processed_by_user_id) REFERENCES users (id);
