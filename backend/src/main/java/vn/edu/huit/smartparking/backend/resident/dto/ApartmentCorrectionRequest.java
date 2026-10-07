package vn.edu.huit.smartparking.backend.resident.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApartmentCorrectionRequest {
    private String building;
    private String apartmentCode;
    private Integer floorNo;
    private String reason;
    private boolean buildingProvided;
    private boolean apartmentCodeProvided;
    private boolean floorNoProvided;

    @JsonProperty("building")
    public void setBuilding(String building) {
        this.buildingProvided = true;
        this.building = building;
    }

    @JsonProperty("apartment_code")
    public void setApartmentCode(String apartmentCode) {
        this.apartmentCodeProvided = true;
        this.apartmentCode = apartmentCode;
    }

    @JsonProperty("floor_no")
    public void setFloorNo(Integer floorNo) {
        this.floorNoProvided = true;
        this.floorNo = floorNo;
    }

    @JsonProperty("reason")
    public void setReason(String reason) {
        this.reason = reason;
    }

    public String building() {
        return building;
    }

    public String apartmentCode() {
        return apartmentCode;
    }

    public Integer floorNo() {
        return floorNo;
    }

    public String reason() {
        return reason;
    }

    public boolean hasBuilding() {
        return buildingProvided;
    }

    public boolean hasApartmentCode() {
        return apartmentCodeProvided;
    }

    public boolean hasFloorNo() {
        return floorNoProvided;
    }
}
