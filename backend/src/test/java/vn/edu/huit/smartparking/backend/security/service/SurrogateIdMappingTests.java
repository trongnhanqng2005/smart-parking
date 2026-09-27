package vn.edu.huit.smartparking.backend.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.huit.smartparking.backend.ai.entity.AiInferenceResult;
import vn.edu.huit.smartparking.backend.alert.entity.Alert;
import vn.edu.huit.smartparking.backend.audit.entity.AuditLog;
import vn.edu.huit.smartparking.backend.billing.entity.Charge;
import vn.edu.huit.smartparking.backend.billing.entity.ChargeItem;
import vn.edu.huit.smartparking.backend.billing.entity.Payment;
import vn.edu.huit.smartparking.backend.card.entity.Card;
import vn.edu.huit.smartparking.backend.card.entity.CardAssignment;
import vn.edu.huit.smartparking.backend.gate.entity.BarrierAction;
import vn.edu.huit.smartparking.backend.gate.entity.DecisionPolicyVersion;
import vn.edu.huit.smartparking.backend.gate.entity.GateEvent;
import vn.edu.huit.smartparking.backend.gate.entity.GateLane;
import vn.edu.huit.smartparking.backend.gate.entity.GateStation;
import vn.edu.huit.smartparking.backend.gate.entity.ManualReview;
import vn.edu.huit.smartparking.backend.gate.entity.WorkShift;
import vn.edu.huit.smartparking.backend.media.entity.FaceTemplate;
import vn.edu.huit.smartparking.backend.media.entity.GateEventMedia;
import vn.edu.huit.smartparking.backend.media.entity.MediaAsset;
import vn.edu.huit.smartparking.backend.media.entity.ResidentMedia;
import vn.edu.huit.smartparking.backend.parking.entity.ParkingSession;
import vn.edu.huit.smartparking.backend.pricing.entity.MonthlyRate;
import vn.edu.huit.smartparking.backend.pricing.entity.PricingVersion;
import vn.edu.huit.smartparking.backend.pricing.entity.VisitorCarRateRule;
import vn.edu.huit.smartparking.backend.pricing.entity.VisitorPeriodRate;
import vn.edu.huit.smartparking.backend.resident.entity.Apartment;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.entity.IdentityVerification;
import vn.edu.huit.smartparking.backend.resident.entity.Resident;
import vn.edu.huit.smartparking.backend.security.entity.Permission;
import vn.edu.huit.smartparking.backend.security.entity.Role;
import vn.edu.huit.smartparking.backend.security.entity.RolePermission;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.security.entity.UserRole;
import vn.edu.huit.smartparking.backend.subscription.entity.ParkingSubscription;
import vn.edu.huit.smartparking.backend.vehicle.entity.Vehicle;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleCategory;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleFamily;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;

class SurrogateIdMappingTests {
    @Test
    void everySingleColumnEntityIdUsesIdentityAndJoinIdsStayComposite() throws Exception {
        List<Class<?>> entities = List.of(
                AuditLog.class, Alert.class, VehicleResidentRelation.class, VehicleFamily.class, ParkingSubscription.class,
                AiInferenceResult.class, VehicleCategory.class, Payment.class, Charge.class, Card.class,
                CardAssignment.class, ChargeItem.class, Vehicle.class, PricingVersion.class, Permission.class,
                Role.class, ParkingSession.class, VisitorPeriodRate.class, VisitorCarRateRule.class, MonthlyRate.class,
                GateEventMedia.class, User.class, Apartment.class, FaceTemplate.class, ApartmentMembership.class,
                BarrierAction.class, MediaAsset.class, IdentityVerification.class, GateEvent.class,
                DecisionPolicyVersion.class, ResidentMedia.class, Resident.class, GateLane.class, WorkShift.class,
                GateStation.class, ManualReview.class);

        assertEquals(36, entities.size());
        for (Class<?> entity : entities) {
            Field id = entity.getDeclaredField("id");
            GeneratedValue generatedValue = id.getAnnotation(GeneratedValue.class);
            assertNotNull(generatedValue, entity.getSimpleName() + " must use generated identity");
            assertEquals(GenerationType.IDENTITY, generatedValue.strategy(), entity.getSimpleName());
        }
        assertNotNull(UserRole.class.getDeclaredField("id").getAnnotation(EmbeddedId.class));
        assertNotNull(RolePermission.class.getDeclaredField("id").getAnnotation(EmbeddedId.class));
    }
}
