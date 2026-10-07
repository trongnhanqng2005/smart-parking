package vn.edu.huit.smartparking.backend.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
            String entityType, String entityId, Pageable pageable);
}
