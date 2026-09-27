package vn.edu.huit.smartparking.backend.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
