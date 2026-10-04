package vn.edu.huit.smartparking.backend.resident.controller;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.huit.smartparking.backend.resident.service.ApartmentNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.HouseholdHeadConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipConcurrentModificationException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipInvalidRequestException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipOverlapException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.MembershipStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentNotFoundException;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;

@RestControllerAdvice(assignableTypes = ApartmentMembershipManagementController.class)
public class ApartmentMembershipApiExceptionHandler {
    @ExceptionHandler(MembershipOverlapException.class)
    public ResponseEntity<AuthError> membershipOverlap(MembershipOverlapException exception) {
        return conflict("MEMBERSHIP_OVERLAP", "Membership interval overlaps an existing membership");
    }

    @ExceptionHandler(HouseholdHeadConflictException.class)
    public ResponseEntity<AuthError> householdHeadConflict(HouseholdHeadConflictException exception) {
        return conflict("HOUSEHOLD_HEAD_CONFLICT", "Apartment already has an overlapping household head");
    }

    @ExceptionHandler(MembershipStatusConflictException.class)
    public ResponseEntity<AuthError> statusConflict(MembershipStatusConflictException exception) {
        return conflict("STATUS_CONFLICT", "Apartment or Resident status does not allow this membership");
    }

    @ExceptionHandler(MembershipStateConflictException.class)
    public ResponseEntity<AuthError> relationStateConflict(MembershipStateConflictException exception) {
        return conflict("RELATION_STATE_CONFLICT", "Membership state does not allow this command");
    }

    @ExceptionHandler({MembershipConcurrentModificationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<AuthError> concurrentModification(RuntimeException exception) {
        return conflict("CONCURRENT_MODIFICATION", "Membership changed concurrently");
    }

    @ExceptionHandler({MembershipNotFoundException.class, ApartmentNotFoundException.class,
            ResidentNotFoundException.class})
    public ResponseEntity<AuthError> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new AuthError("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler({MembershipInvalidRequestException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<AuthError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new AuthError("INVALID_REQUEST", "Request validation failed"));
    }

    private ResponseEntity<AuthError> conflict(String code, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthError(code, message));
    }
}
