package vn.edu.huit.smartparking.backend.audit.service;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;
import vn.edu.huit.smartparking.backend.audit.repository.AuditLogRepository;
import vn.edu.huit.smartparking.backend.security.entity.User;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String action, String entityType, String entityId, User actor, String oldData, String newData) {
        record(action, entityType, entityId, actor, oldData, newData, requestId(), clientIp());
    }

    public void record(
            String action,
            String entityType,
            String entityId,
            User actor,
            String oldData,
            String newData,
            String requestId,
            String clientIp) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actor);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldData(oldData);
        auditLog.setNewData(newData);
        auditLog.setRequestId(requestId);
        auditLog.setClientIp(clientIp);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }

    private String requestId() {
        ServletRequestAttributes attributes = requestAttributes();
        if (attributes == null) {
            return null;
        }
        String requestId = attributes.getRequest().getHeader("X-Request-ID");
        return requestId != null && requestId.length() <= 100 ? requestId : null;
    }

    private String clientIp() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? null : attributes.getRequest().getRemoteAddr();
    }

    private ServletRequestAttributes requestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes : null;
    }
}
