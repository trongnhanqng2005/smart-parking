package vn.edu.huit.smartparking.backend.resident.service;

public class ResidentStatusLockSetChangedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ResidentStatusResources discoveredResources;

    public ResidentStatusLockSetChangedException(ResidentStatusResources discoveredResources) {
        this.discoveredResources = discoveredResources;
    }

    public ResidentStatusResources discoveredResources() {
        return discoveredResources;
    }
}
