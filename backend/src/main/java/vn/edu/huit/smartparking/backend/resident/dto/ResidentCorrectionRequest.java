package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public class ResidentCorrectionRequest {
    private String fullName;
    private String identityNumber;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String reason;
    private boolean fullNameProvided;
    private boolean identityNumberProvided;
    private boolean dateOfBirthProvided;
    private boolean phoneProvided;
    private boolean emailProvided;

    @JsonProperty("full_name")
    public void setFullName(String fullName) {
        this.fullNameProvided = true;
        this.fullName = fullName;
    }

    @JsonProperty("identity_number")
    public void setIdentityNumber(String identityNumber) {
        this.identityNumberProvided = true;
        this.identityNumber = identityNumber;
    }

    @JsonProperty("date_of_birth")
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirthProvided = true;
        this.dateOfBirth = dateOfBirth;
    }

    @JsonProperty("phone")
    public void setPhone(String phone) {
        this.phoneProvided = true;
        this.phone = phone;
    }

    @JsonProperty("email")
    public void setEmail(String email) {
        this.emailProvided = true;
        this.email = email;
    }

    @JsonProperty("reason")
    public void setReason(String reason) {
        this.reason = reason;
    }

    public String fullName() {
        return fullName;
    }

    public String identityNumber() {
        return identityNumber;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String reason() {
        return reason;
    }

    public boolean hasFullName() {
        return fullNameProvided;
    }

    public boolean hasIdentityNumber() {
        return identityNumberProvided;
    }

    public boolean hasDateOfBirth() {
        return dateOfBirthProvided;
    }

    public boolean hasPhone() {
        return phoneProvided;
    }

    public boolean hasEmail() {
        return emailProvided;
    }
}
