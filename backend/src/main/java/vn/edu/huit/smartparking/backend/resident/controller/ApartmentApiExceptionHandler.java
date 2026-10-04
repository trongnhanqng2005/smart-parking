package vn.edu.huit.smartparking.backend.resident.controller;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentIdentityConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentConcurrentModificationException;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStateConflictException;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;

@RestControllerAdvice(assignableTypes = ApartmentManagementController.class)
public class ApartmentApiExceptionHandler {
    @ExceptionHandler(ApartmentIdentityConflictException.class)
    public ResponseEntity<AuthError> identityConflict(ApartmentIdentityConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("APARTMENT_IDENTITY_CONFLICT", "Apartment business identity already exists"));
    }

    @ExceptionHandler(ApartmentNotFoundException.class)
    public ResponseEntity<AuthError> notFound(ApartmentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthError("NOT_FOUND", "Apartment not found"));
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<AuthError> membershipNotFound(MembershipNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthError("NOT_FOUND", "Membership not found"));
    }

    @ExceptionHandler({ApartmentConcurrentModificationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<AuthError> concurrentModification(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("CONCURRENT_MODIFICATION", "Apartment was modified concurrently"));
    }

    @ExceptionHandler(ApartmentStatusConflictException.class)
    public ResponseEntity<AuthError> statusConflict(ApartmentStatusConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("STATUS_CONFLICT", "Apartment status does not allow this command"));
    }

    @ExceptionHandler(MembershipStateConflictException.class)
    public ResponseEntity<AuthError> membershipStateConflict(MembershipStateConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("RELATION_STATE_CONFLICT", "Membership state does not allow this command"));
    }

    @ExceptionHandler({
            ApartmentInvalidRequestException.class,
            MembershipInvalidRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AuthError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new AuthError("INVALID_REQUEST", "Request validation failed"));
    }
}
