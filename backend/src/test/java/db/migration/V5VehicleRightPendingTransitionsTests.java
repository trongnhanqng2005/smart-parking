package db.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.migration.Context;

class V5VehicleRightPendingTransitionsTests {
    @Test
    void acceptsOwnerWithoutGuarantorFields() {
        assertTrue(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "OWNER", null, null, null, "ACTIVE"));
    }

    @Test
    void rejectsAuthorizedUserWithMissingGuarantorTypeEvenWhenResidentExists() {
        assertFalse(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "AUTHORIZED_USER", null, 17L, null, "ACTIVE"));
    }

    @Test
    void requiresApartmentContextOnlyForHouseholdHeadGuarantor() {
        assertTrue(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "AUTHORIZED_USER", "OWNER", 17L, null, "ACTIVE"));
        assertTrue(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "AUTHORIZED_USER", "HOUSEHOLD_HEAD", 17L, 23L, "ACTIVE"));
        assertFalse(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "AUTHORIZED_USER", "OWNER", 17L, 23L, "ACTIVE"));
        assertFalse(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "AUTHORIZED_USER", "HOUSEHOLD_HEAD", 17L, null, "ACTIVE"));
    }

    @Test
    void rejectsOwnerRowsWithGuarantorDataOrPreEffectiveCancellation() {
        assertFalse(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "OWNER", null, 17L, null, "ACTIVE"));
        assertFalse(V5__vehicle_right_pending_transitions.hasValidGuarantorShape(
                "OWNER", null, null, null, "PRE_EFFECTIVE_CANCELLED"));
    }

    @Test
    void failsPreflightBeforeDdlForExistingAuthorizedUserWithoutGuarantorType() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rows = mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("id")).thenReturn(913L);
        when(rows.getLong("guarantor_resident_id")).thenReturn(17L);
        when(rows.wasNull()).thenReturn(false, true);
        when(rows.getString("relation_type")).thenReturn("AUTHORIZED_USER");
        when(rows.getString("guarantor_type")).thenReturn(null);
        when(rows.getString("status")).thenReturn("ACTIVE");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new V5__vehicle_right_pending_transitions().migrate(context));

        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("id=913"));
        verify(statement).executeQuery(contains("FROM vehicle_resident_relations ORDER BY id"));
        verify(statement, never()).execute(anyString());
        verify(connection, times(1)).createStatement();
    }
}
