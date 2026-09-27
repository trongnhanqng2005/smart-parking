package vn.edu.huit.smartparking.backend.security.service;

import java.io.Serializable;

public record AuthRequestDetails(String remoteAddress, String requestId) implements Serializable {}
