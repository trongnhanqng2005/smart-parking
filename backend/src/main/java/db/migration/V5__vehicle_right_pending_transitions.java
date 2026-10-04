package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V5__vehicle_right_pending_transitions extends BaseJavaMigration {
    private static final String CHECKSUM_REVISION = String.join("|",
            "AHRR01-V5",
            "authorized-user-guarantor-type-required-v1",
            "pending-transition-one-per-vehicle-right-v1");

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
        for (String statement : finalDdl()) {
            execute(connection, statement);
        }
    }

    private void preflight(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id, relation_type, guarantor_type, "
                        + "guarantor_resident_id, guarantor_apartment_id, status "
                        + "FROM vehicle_resident_relations ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                if (!hasValidGuarantorShape(rows.getString("relation_type"), rows.getString("guarantor_type"),
                        nullableLong(rows, "guarantor_resident_id"), nullableLong(rows, "guarantor_apartment_id"),
                        rows.getString("status"))) {
                    throw preflightFailure("vehicle relation has an invalid guarantor shape; id=" + id);
                }
            }
        }
    }

    static boolean hasValidGuarantorShape(
            String relationType,
            String guarantorType,
            Long guarantorResidentId,
            Long guarantorApartmentId,
            String status) {
        if ("OWNER".equals(relationType)) {
            return guarantorType == null && guarantorResidentId == null && guarantorApartmentId == null
                    && !"PRE_EFFECTIVE_CANCELLED".equals(status);
        }
        if (!"AUTHORIZED_USER".equals(relationType) || guarantorType == null || guarantorResidentId == null) {
            return false;
        }
        return switch (guarantorType) {
            case "OWNER" -> guarantorApartmentId == null;
            case "HOUSEHOLD_HEAD" -> guarantorApartmentId != null;
            default -> false;
        };
    }

    private Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private List<String> finalDdl() {
        return List.of(
                "ALTER TABLE vehicle_resident_relations "
                        + "DROP CHECK ck_vehicle_relation_guarantor, "
                        + "ADD CONSTRAINT ck_vehicle_relation_guarantor CHECK ("
                        + "(relation_type = 'OWNER' AND guarantor_type IS NULL AND guarantor_resident_id IS NULL "
                        + "AND guarantor_apartment_id IS NULL AND status <> 'PRE_EFFECTIVE_CANCELLED') OR "
                        + "(relation_type = 'AUTHORIZED_USER' AND guarantor_type IS NOT NULL "
                        + "AND guarantor_resident_id IS NOT NULL AND "
                        + "((guarantor_type = 'OWNER' AND guarantor_apartment_id IS NULL) OR "
                        + "(guarantor_type = 'HOUSEHOLD_HEAD' AND guarantor_apartment_id IS NOT NULL))))",
                "CREATE TABLE vehicle_right_pending_transitions ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT, "
                        + "vehicle_right_id BIGINT NOT NULL, "
                        + "effective_at DATETIME NOT NULL, "
                        + "reason VARCHAR(500) NOT NULL, "
                        + "source_actor_user_id BIGINT NOT NULL, "
                        + "PRIMARY KEY (id), "
                        + "CONSTRAINT uk_vehicle_right_pending_transition_relation UNIQUE (vehicle_right_id), "
                        + "KEY ix_vehicle_right_pending_transitions_due (effective_at, id), "
                        + "CONSTRAINT ck_vehicle_right_pending_transition_reason "
                        + "CHECK (CHAR_LENGTH(TRIM(reason)) > 0), "
                        + "CONSTRAINT fk_vr_pending_transition_relation FOREIGN KEY (vehicle_right_id) "
                        + "REFERENCES vehicle_resident_relations (id) ON DELETE RESTRICT, "
                        + "CONSTRAINT fk_vr_pending_transition_actor FOREIGN KEY (source_actor_user_id) "
                        + "REFERENCES users (id) ON DELETE RESTRICT) ENGINE=InnoDB");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static IllegalStateException preflightFailure(String message) {
        return new IllegalStateException("AHRR-01 review remediation V5 preflight failed: " + message);
    }
}
