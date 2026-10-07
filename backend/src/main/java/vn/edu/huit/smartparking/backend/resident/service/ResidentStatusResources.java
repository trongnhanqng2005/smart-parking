package vn.edu.huit.smartparking.backend.resident.service;

import java.util.List;
import java.util.TreeSet;

public record ResidentStatusResources(List<Long> residentIds, List<Long> apartmentIds, List<Long> vehicleIds) {
    public ResidentStatusResources {
        residentIds = sorted(residentIds);
        apartmentIds = sorted(apartmentIds);
        vehicleIds = sorted(vehicleIds);
    }

    public ResidentStatusResources merge(ResidentStatusResources other) {
        TreeSet<Long> residents = new TreeSet<>(residentIds);
        residents.addAll(other.residentIds());
        TreeSet<Long> apartments = new TreeSet<>(apartmentIds);
        apartments.addAll(other.apartmentIds());
        TreeSet<Long> vehicles = new TreeSet<>(vehicleIds);
        vehicles.addAll(other.vehicleIds());
        return new ResidentStatusResources(List.copyOf(residents), List.copyOf(apartments), List.copyOf(vehicles));
    }

    public boolean contains(ResidentStatusResources other) {
        return residentIds.containsAll(other.residentIds())
                && apartmentIds.containsAll(other.apartmentIds())
                && vehicleIds.containsAll(other.vehicleIds());
    }

    private static List<Long> sorted(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(new TreeSet<>(ids));
    }
}
