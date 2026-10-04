package vn.edu.huit.smartparking.backend.vehicle.controller;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.edu.huit.smartparking.backend.security.dto.AuthError;
import vn.edu.huit.smartparking.backend.vehicle.service.GuarantorChainConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleConcurrentModificationException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleInvalidRequestException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleNotFoundException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleOwnerConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleOwnerRelationNotFoundException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRelationStateConflictException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleRightOverlapException;
import vn.edu.huit.smartparking.backend.vehicle.service.VehicleStatusConflictException;

@RestControllerAdvice(assignableTypes = VehicleManagementController.class)
public class VehicleApiExceptionHandler {
    @ExceptionHandler({VehicleNotFoundException.class, VehicleOwnerRelationNotFoundException.class})
    public ResponseEntity<AuthError> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthError("NOT_FOUND", "Vehicle resource not found"));
    }

    @ExceptionHandler(VehicleOwnerConflictException.class)
    public ResponseEntity<AuthError> ownerConflict(VehicleOwnerConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("VEHICLE_OWNER_CONFLICT", "Vehicle already has an overlapping OWNER"));
    }

    @ExceptionHandler(VehicleRightOverlapException.class)
    public ResponseEntity<AuthError> relationOverlap(VehicleRightOverlapException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("VEHICLE_RIGHT_OVERLAP", "Resident already has an overlapping Vehicle relation"));
    }

    @ExceptionHandler(GuarantorChainConflictException.class)
    public ResponseEntity<AuthError> guarantorChainConflict(GuarantorChainConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("GUARANTOR_CHAIN_CONFLICT", "Guarantor chain is not effective"));
    }

    @ExceptionHandler(VehicleStatusConflictException.class)
    public ResponseEntity<AuthError> statusConflict(VehicleStatusConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("STATUS_CONFLICT", "Resident or Apartment status does not allow this command"));
    }

    @ExceptionHandler(VehicleRelationStateConflictException.class)
    public ResponseEntity<AuthError> relationStateConflict(VehicleRelationStateConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("RELATION_STATE_CONFLICT", "Vehicle relation state does not allow this command"));
    }

    @ExceptionHandler({ConcurrencyFailureException.class, VehicleConcurrentModificationException.class})
    public ResponseEntity<AuthError> concurrentModification(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthError("CONCURRENT_MODIFICATION", "Vehicle relations changed concurrently"));
    }

    @ExceptionHandler({
            VehicleInvalidRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<AuthError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new AuthError("INVALID_REQUEST", "Request validation failed"));
    }
}
