package vn.edu.huit.smartparking.backend.security.service;

import java.io.Serializable;

public record AuthRequestDetails(String remoteAddress, String requestId, Channel channel) implements Serializable {
    public enum Channel {
        WEB,
        REST
    }
}
