package vn.edu.huit.smartparking.backend.resident.controller;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.huit.smartparking.backend.resident.service.ResidentConcurrentModificationException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusRelationStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusResourceNotFoundException;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;

@RestControllerAdvice(assignableTypes = ResidentManagementController.class)
public class ResidentApiExceptionHandler {
    @ExceptionHandler(ResidentIdentityConflictException.class)
    public ResponseEntity<AuthError> identityConflict(ResidentIdentityConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("RESIDENT_IDENTITY_CONFLICT", "Resident identity number already exists"));
    }

    @ExceptionHandler(ResidentConcurrentModificationException.class)
    public ResponseEntity<AuthError> concurrentModification(ResidentConcurrentModificationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("CONCURRENT_MODIFICATION", "Resident changed concurrently"));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<AuthError> concurrentModification(ConcurrencyFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("CONCURRENT_MODIFICATION", "Resident changed concurrently"));
    }

    @ExceptionHandler({ResidentNotFoundException.class, ResidentStatusResourceNotFoundException.class})
    public ResponseEntity<AuthError> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthError("NOT_FOUND", "Resident resource not found"));
    }

    @ExceptionHandler(ResidentStatusConflictException.class)
    public ResponseEntity<AuthError> statusConflict(ResidentStatusConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("STATUS_CONFLICT", "Resident status does not allow this command"));
    }

    @ExceptionHandler(ResidentStatusRelationStateConflictException.class)
    public ResponseEntity<AuthError> relationStateConflict(ResidentStatusRelationStateConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("RELATION_STATE_CONFLICT", "Relation state does not allow this command"));
    }

    @ExceptionHandler({
            ResidentInvalidRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AuthError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new AuthError("INVALID_REQUEST", "Request validation failed"));
    }
}
