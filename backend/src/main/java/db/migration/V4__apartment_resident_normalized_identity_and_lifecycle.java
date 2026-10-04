package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import vn.edu.huit.smartparking.backend.resident.ResidentIdentityKeyNormalizer;

public class V4__apartment_resident_normalized_identity_and_lifecycle extends BaseJavaMigration {
    private static final int APARTMENT_PAGE_SIZE = 500;
    private static final int RESIDENT_PAGE_SIZE = 500;
    private static final String CHECKSUM_REVISION = String.join("|",
            "AHR01-V4",
            "building-nfc-trim-collapse-upper-root-nfc-utf8-v1",
            "apartment-code-nfc-trim-upper-root-nfc-utf8-v1",
            "identity-number-nfc-trim-remove-whitespace-latin-upper-nfc-utf8-v1",
            "membership-lifecycle-v1",
            "vehicle-guarantor-lifecycle-v1");

    @Override
    public Integer getChecksum() {
        CRC32 checksum = new CRC32();
        checksum.update(CHECKSUM_REVISION.getBytes(StandardCharsets.UTF_8));
        for (String statement : finalDdl()) {
            checksum.update(statement.getBytes(StandardCharsets.UTF_8));
        }
        return (int) checksum.getValue();
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        preflight(connection);

        execute(connection, "ALTER TABLE apartments "
                + "ADD COLUMN building_key VARBINARY(2048) NULL, "
                + "ADD COLUMN apartment_code_key VARBINARY(768) NULL");
        execute(connection, "ALTER TABLE residents "
                + "ADD COLUMN identity_number_key VARBINARY(512) NULL");

        backfillApartmentKeys(connection);
        backfillResidentKeys(connection);

        for (String statement : finalDdl()) {
            execute(connection, statement);
        }
    }

    private void preflight(Connection connection) throws SQLException {
        int innodbPageSize;
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT @@innodb_page_size")) {
            result.next();
            innodbPageSize = result.getInt(1);
        }
        if (innodbPageSize < 16_384) {
            throw preflightFailure("target InnoDB page size does not support the approved full-width unique key indexes");
        }
        requireForeignKey(connection, "apartment_memberships", "apartment_id", "apartments",
                "apartment_memberships_ibfk_1");
        requireForeignKey(connection, "apartment_memberships", "resident_id", "residents",
                "apartment_memberships_ibfk_2");
        requireForeignKey(connection, "vehicle_resident_relations", "vehicle_id", "vehicles",
                "vehicle_resident_relations_ibfk_1");
        requireForeignKey(connection, "vehicle_resident_relations", "resident_id", "residents",
                "vehicle_resident_relations_ibfk_2");
        preflightApartments(connection);
        preflightResidents(connection);
        preflightMemberships(connection);
        preflightVehicleRelations(connection);
    }

    private void preflightApartments(Connection connection) throws SQLException {
        Map<ApartmentIdentity, Long> identities = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id, building, apartment_code FROM apartments ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String building = rows.getString("building");
                String apartmentCode = rows.getString("apartment_code");
                byte[] buildingKey = canonicalKey(
                        () -> ResidentIdentityKeyNormalizer.buildingKey(building), "apartments", id);
                byte[] apartmentCodeKey = canonicalKey(
                        () -> ResidentIdentityKeyNormalizer.apartmentCodeKey(apartmentCode), "apartments", id);
                requireKeySize(buildingKey, ResidentIdentityKeyNormalizer.BUILDING_KEY_MAX_BYTES,
                        "apartments", id, "building_key");
                requireKeySize(apartmentCodeKey, ResidentIdentityKeyNormalizer.APARTMENT_CODE_KEY_MAX_BYTES,
                        "apartments", id, "apartment_code_key");

                ApartmentIdentity identity = new ApartmentIdentity(new BinaryKey(buildingKey), new BinaryKey(apartmentCodeKey));
                Long conflictingId = identities.putIfAbsent(identity, id);
                if (conflictingId != null) {
                    throw preflightFailure("normalized Apartment identity collision; ids="
                            + conflictingId + "," + id);
                }
            }
        }
    }

    private void preflightResidents(Connection connection) throws SQLException {
        Map<BinaryKey, Long> identities = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT id, full_name, identity_number FROM residents ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String fullName = rows.getString("full_name");
                if (fullName == null || fullName.isBlank()) {
                    throw preflightFailure("Resident full_name is missing or blank; id=" + id);
                }
                String identityNumber = rows.getString("identity_number");
                byte[] identityKey = canonicalKey(
                        () -> ResidentIdentityKeyNormalizer.identityNumberKey(identityNumber), "residents", id);
                requireKeySize(identityKey, ResidentIdentityKeyNormalizer.IDENTITY_NUMBER_KEY_MAX_BYTES,
                        "residents", id, "identity_number_key");

                Long conflictingId = identities.putIfAbsent(new BinaryKey(identityKey), id);
                if (conflictingId != null) {
                    throw preflightFailure("normalized Resident identity collision; ids="
                            + conflictingId + "," + id);
                }
            }
        }
    }

    private void preflightMemberships(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, apartment_id, resident_id, member_role, "
                        + "valid_from, valid_to, status FROM apartment_memberships ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                requireNotNull(rows, "apartment_id", "apartment_memberships", id);
                requireNotNull(rows, "resident_id", "apartment_memberships", id);
                requireNotNull(rows, "member_role", "apartment_memberships", id);
                String status = rows.getString("status");
                if (!"ACTIVE".equals(status)) {
                    throw preflightFailure("terminal or unknown ApartmentMembership status lacks lifecycle evidence; id="
                            + id);
                }
                requireValidInterval(rows, "apartment_memberships", id);
            }
        }
    }

    private void preflightVehicleRelations(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, vehicle_id, resident_id, relation_type, "
                        + "valid_from, valid_to, status FROM vehicle_resident_relations ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                requireNotNull(rows, "vehicle_id", "vehicle_resident_relations", id);
                requireNotNull(rows, "resident_id", "vehicle_resident_relations", id);
                String relationType = rows.getString("relation_type");
                if (!"OWNER".equals(relationType)) {
                    throw preflightFailure("existing AUTHORIZED_USER or unknown vehicle relation has no evidenced "
                            + "historical guarantor/context; id=" + id);
                }
                String status = rows.getString("status");
                if (!"ACTIVE".equals(status)) {
                    throw preflightFailure("terminal or unknown VehicleResidentRelation status lacks lifecycle "
                            + "evidence; id=" + id);
                }
                requireValidInterval(rows, "vehicle_resident_relations", id);
            }
        }
    }

    private void backfillApartmentKeys(Connection connection) throws SQLException {
        long afterId = Long.MIN_VALUE;
        while (true) {
            List<ApartmentKeyRow> batch = new ArrayList<>(APARTMENT_PAGE_SIZE);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, building, apartment_code FROM apartments WHERE id > ? ORDER BY id LIMIT ?")) {
                select.setLong(1, afterId);
                select.setInt(2, APARTMENT_PAGE_SIZE);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        long id = rows.getLong("id");
                        batch.add(new ApartmentKeyRow(id,
                                ResidentIdentityKeyNormalizer.buildingKey(rows.getString("building")),
                                ResidentIdentityKeyNormalizer.apartmentCodeKey(rows.getString("apartment_code"))));
                    }
                }
            }
            if (batch.isEmpty()) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE apartments SET building_key = ?, apartment_code_key = ? WHERE id = ?")) {
                for (ApartmentKeyRow row : batch) {
                    update.setBytes(1, row.buildingKey());
                    update.setBytes(2, row.apartmentCodeKey());
                    update.setLong(3, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
            afterId = batch.getLast().id();
        }
    }

    private void backfillResidentKeys(Connection connection) throws SQLException {
        long afterId = Long.MIN_VALUE;
        while (true) {
            List<ResidentKeyRow> batch = new ArrayList<>(RESIDENT_PAGE_SIZE);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, identity_number FROM residents WHERE id > ? ORDER BY id LIMIT ?")) {
                select.setLong(1, afterId);
                select.setInt(2, RESIDENT_PAGE_SIZE);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        batch.add(new ResidentKeyRow(rows.getLong("id"),
                                ResidentIdentityKeyNormalizer.identityNumberKey(rows.getString("identity_number"))));
                    }
                }
            }
            if (batch.isEmpty()) {
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE residents SET identity_number_key = ? WHERE id = ?")) {
                for (ResidentKeyRow row : batch) {
                    update.setBytes(1, row.identityNumberKey());
                    update.setLong(2, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
            afterId = batch.getLast().id();
        }
    }

    private List<String> finalDdl() {
        return List.of(
                "ALTER TABLE apartments "
                        + "MODIFY COLUMN building VARCHAR(100) NOT NULL, "
                        + "MODIFY COLUMN apartment_code VARCHAR(50) NOT NULL, "
                        + "MODIFY COLUMN building_key VARBINARY(2048) NOT NULL, "
                        + "MODIFY COLUMN apartment_code_key VARBINARY(768) NOT NULL, "
                        + "ADD CONSTRAINT uk_apartments_normalized_identity "
                        + "UNIQUE (building_key, apartment_code_key), "
                        + "ADD CONSTRAINT ck_apartments_building_key_nonempty CHECK (OCTET_LENGTH(building_key) > 0), "
                        + "ADD CONSTRAINT ck_apartments_code_key_nonempty CHECK (OCTET_LENGTH(apartment_code_key) > 0)",
                "ALTER TABLE residents "
                        + "MODIFY COLUMN full_name VARCHAR(150) NOT NULL, "
                        + "MODIFY COLUMN identity_number VARCHAR(30) NOT NULL, "
                        + "MODIFY COLUMN identity_number_key VARBINARY(512) NOT NULL, "
                        + "ADD CONSTRAINT uk_residents_identity_number_key UNIQUE (identity_number_key), "
                        + "ADD CONSTRAINT ck_residents_identity_key_nonempty CHECK (OCTET_LENGTH(identity_number_key) > 0)",
                "ALTER TABLE apartment_memberships "
                        + "DROP FOREIGN KEY apartment_memberships_ibfk_1, "
                        + "DROP FOREIGN KEY apartment_memberships_ibfk_2, "
                        + "ADD COLUMN lifecycle_changed_at DATETIME(6) NULL, "
                        + "ADD COLUMN lifecycle_reason VARCHAR(500) NULL, "
                        + "MODIFY COLUMN apartment_id BIGINT NOT NULL, "
                        + "MODIFY COLUMN resident_id BIGINT NOT NULL, "
                        + "MODIFY COLUMN member_role ENUM('HOUSEHOLD_HEAD', 'MEMBER') NOT NULL, "
                        + "MODIFY COLUMN valid_from DATETIME NOT NULL, "
                        + "MODIFY COLUMN status ENUM('ACTIVE', 'INACTIVE', 'REVOKED', 'VOID') NOT NULL, "
                        + "ADD CONSTRAINT ck_apartment_membership_valid_interval "
                        + "CHECK (valid_to IS NULL OR valid_to > valid_from), "
                        + "ADD CONSTRAINT ck_apartment_membership_lifecycle CHECK ("
                        + "(status = 'ACTIVE' AND lifecycle_changed_at IS NULL AND lifecycle_reason IS NULL) OR "
                        + "(status = 'INACTIVE' AND valid_to IS NOT NULL AND lifecycle_changed_at IS NOT NULL "
                        + "AND lifecycle_reason IS NOT NULL AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0) OR "
                        + "(status = 'REVOKED' AND lifecycle_changed_at IS NOT NULL AND lifecycle_reason IS NOT NULL "
                        + "AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0) OR "
                        + "(status = 'VOID' AND lifecycle_changed_at IS NOT NULL AND lifecycle_reason IS NOT NULL "
                        + "AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0)), "
                        + "ADD CONSTRAINT fk_apartment_membership_apartment "
                        + "FOREIGN KEY (apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT, "
                        + "ADD CONSTRAINT fk_apartment_membership_resident "
                        + "FOREIGN KEY (resident_id) REFERENCES residents (id) ON DELETE RESTRICT",
                "ALTER TABLE vehicle_resident_relations "
                        + "DROP FOREIGN KEY vehicle_resident_relations_ibfk_1, "
                        + "DROP FOREIGN KEY vehicle_resident_relations_ibfk_2, "
                        + "ADD COLUMN guarantor_type ENUM('OWNER', 'HOUSEHOLD_HEAD') NULL, "
                        + "ADD COLUMN guarantor_resident_id BIGINT NULL, "
                        + "ADD COLUMN guarantor_apartment_id BIGINT NULL, "
                        + "ADD COLUMN lifecycle_changed_at DATETIME(6) NULL, "
                        + "ADD COLUMN lifecycle_reason VARCHAR(500) NULL, "
                        + "MODIFY COLUMN vehicle_id BIGINT NOT NULL, "
                        + "MODIFY COLUMN resident_id BIGINT NOT NULL, "
                        + "MODIFY COLUMN relation_type ENUM('OWNER', 'AUTHORIZED_USER') NOT NULL, "
                        + "MODIFY COLUMN valid_from DATETIME NOT NULL, "
                        + "MODIFY COLUMN status ENUM('ACTIVE', 'INACTIVE', 'REVOKED', 'VOID', 'PRE_EFFECTIVE_CANCELLED') NOT NULL, "
                        + "ADD CONSTRAINT ck_vehicle_relation_valid_interval "
                        + "CHECK (valid_to IS NULL OR valid_to > valid_from), "
                        + "ADD CONSTRAINT ck_vehicle_relation_guarantor CHECK ("
                        + "(relation_type = 'OWNER' AND guarantor_type IS NULL AND guarantor_resident_id IS NULL "
                        + "AND guarantor_apartment_id IS NULL AND status <> 'PRE_EFFECTIVE_CANCELLED') OR "
                        + "(relation_type = 'AUTHORIZED_USER' AND guarantor_resident_id IS NOT NULL AND "
                        + "((guarantor_type = 'OWNER' AND guarantor_apartment_id IS NULL) OR "
                        + "(guarantor_type = 'HOUSEHOLD_HEAD' AND guarantor_apartment_id IS NOT NULL)))), "
                        + "ADD CONSTRAINT ck_vehicle_relation_lifecycle CHECK ("
                        + "(status = 'ACTIVE' AND lifecycle_changed_at IS NULL AND lifecycle_reason IS NULL) OR "
                        + "(status = 'INACTIVE' AND valid_to IS NOT NULL AND lifecycle_changed_at IS NOT NULL "
                        + "AND lifecycle_reason IS NOT NULL AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0) OR "
                        + "(status = 'REVOKED' AND lifecycle_changed_at IS NOT NULL AND lifecycle_reason IS NOT NULL "
                        + "AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0) OR "
                        + "(status = 'VOID' AND lifecycle_changed_at IS NOT NULL AND lifecycle_reason IS NOT NULL "
                        + "AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0) OR "
                        + "(status = 'PRE_EFFECTIVE_CANCELLED' AND relation_type = 'AUTHORIZED_USER' "
                        + "AND valid_to IS NULL AND lifecycle_changed_at IS NOT NULL "
                        + "AND lifecycle_changed_at <= valid_from AND lifecycle_reason IS NOT NULL "
                        + "AND CHAR_LENGTH(TRIM(lifecycle_reason)) > 0)), "
                        + "ADD CONSTRAINT fk_vehicle_relation_vehicle "
                        + "FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE RESTRICT, "
                        + "ADD CONSTRAINT fk_vehicle_relation_resident "
                        + "FOREIGN KEY (resident_id) REFERENCES residents (id) ON DELETE RESTRICT, "
                        + "ADD CONSTRAINT fk_vehicle_relation_guarantor_resident "
                        + "FOREIGN KEY (guarantor_resident_id) REFERENCES residents (id) ON DELETE RESTRICT, "
                        + "ADD CONSTRAINT fk_vehicle_relation_guarantor_apartment "
                        + "FOREIGN KEY (guarantor_apartment_id) REFERENCES apartments (id) ON DELETE RESTRICT");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static byte[] canonicalKey(KeySupplier supplier, String table, long id) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException exception) {
            throw preflightFailure("missing or blank identity source in " + table + "; id=" + id);
        }
    }

    private static void requireKeySize(byte[] key, int maxBytes, String table, long id, String column) {
        if (key.length > maxBytes) {
            throw preflightFailure("normalized key exceeds " + column + " capacity in " + table + "; id=" + id);
        }
    }

    private static void requireNotNull(ResultSet rows, String column, String table, long id) throws SQLException {
        if (rows.getObject(column) == null) {
            throw preflightFailure("required relation field " + column + " is null in " + table + "; id=" + id);
        }
    }

    private static void requireForeignKey(
            Connection connection, String table, String column, String targetTable, String expectedName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? "
                        + "AND referenced_table_name = ? AND constraint_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            statement.setString(3, targetTable);
            statement.setString(4, expectedName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) != 1) {
                    throw preflightFailure("expected baseline foreign key is missing or renamed: "
                            + table + "." + column + " -> " + targetTable);
                }
            }
        }
    }

    private static void requireValidInterval(ResultSet rows, String table, long id) throws SQLException {
        Timestamp validFrom = rows.getTimestamp("valid_from");
        Timestamp validTo = rows.getTimestamp("valid_to");
        if (validFrom == null || (validTo != null && !validTo.toLocalDateTime().isAfter(validFrom.toLocalDateTime()))) {
            throw preflightFailure("invalid relation validity interval in " + table + "; id=" + id);
        }
    }

    private static IllegalStateException preflightFailure(String message) {
        return new IllegalStateException("AHR-01 preflight failed: " + message);
    }

    private record ApartmentKeyRow(long id, byte[] buildingKey, byte[] apartmentCodeKey) {}

    private record ResidentKeyRow(long id, byte[] identityNumberKey) {}

    private record ApartmentIdentity(BinaryKey buildingKey, BinaryKey apartmentCodeKey) {}

    private record BinaryKey(byte[] value) {
        private BinaryKey {
            value = Arrays.copyOf(value, value.length);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BinaryKey key && Arrays.equals(value, key.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    @FunctionalInterface
    private interface KeySupplier {
        byte[] get();
    }
}
