package vn.edu.huit.smartparking.backend.vehicle.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.huit.smartparking.backend.audit.service.AuditService;
import vn.edu.huit.smartparking.backend.resident.entity.ApartmentMembership;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipRole;
import vn.edu.huit.smartparking.backend.resident.enums.MembershipStatus;
import vn.edu.huit.smartparking.backend.resident.enums.RelationLifecycleAction;
import vn.edu.huit.smartparking.backend.resident.enums.ResidentStatus;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentMembershipRepository;
import vn.edu.huit.smartparking.backend.resident.repository.ApartmentRepository;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusRelationStateConflictException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusResourceNotFoundException;
import vn.edu.huit.smartparking.backend.resident.service.ResidentStatusResources;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.MembershipAuthorityLoss;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.MembershipVoidSource;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.StatusMembershipAction;
import vn.edu.huit.smartparking.backend.resident.service.VehicleRightInvalidationPort.VehicleRightAction;
import vn.edu.huit.smartparking.backend.security.entity.User;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleResidentRelation;
import vn.edu.huit.smartparking.backend.vehicle.entity.VehicleRightPendingTransition;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationGuarantorType;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationStatus;
import vn.edu.huit.smartparking.backend.vehicle.enums.VehicleRelationType;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleResidentRelationRepository;
import vn.edu.huit.smartparking.backend.vehicle.repository.VehicleRightPendingTransitionRepository;

@Service
public class VehicleRightInvalidationService implements VehicleRightInvalidationPort {
    private static final String RELATION_ENTITY = "VEHICLE_RESIDENT_RELATION";

    private final VehicleResidentRelationRepository relationRepository;
    private final ApartmentRepository apartmentRepository;
    private final ApartmentMembershipRepository membershipRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleRightPendingTransitionRepository pendingTransitionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate discoveryTransactionTemplate;

    public VehicleRightInvalidationService(
            VehicleResidentRelationRepository relationRepository,
            ApartmentRepository apartmentRepository,
            ApartmentMembershipRepository membershipRepository,
            VehicleRepository vehicleRepository,
            VehicleRightPendingTransitionRepository pendingTransitionRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.relationRepository = relationRepository;
        this.apartmentRepository = apartmentRepository;
        this.membershipRepository = membershipRepository;
        this.vehicleRepository = vehicleRepository;
        this.pendingTransitionRepository = pendingTransitionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.discoveryTransactionTemplate = new TransactionTemplate(transactionManager);
        this.discoveryTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.discoveryTransactionTemplate.setReadOnly(true);
    }

    public List<Long> lockOwnerLossApartmentContexts(Long vehicleId, Long ownerResidentId, LocalDateTime effectiveAt) {
        List<Long> apartmentIds = discoveryTransactionTemplate.execute(status ->
                relationRepository.findHouseholdGuarantorApartmentsForOwnerLoss(
                        vehicleId, ownerResidentId, effectiveAt, VehicleRelationType.AUTHORIZED_USER,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationStatus.ACTIVE,
                        MembershipStatus.ACTIVE)).stream().sorted().toList();
        for (Long apartmentId : apartmentIds) {
            apartmentRepository.findByIdForUpdate(apartmentId).orElseThrow(VehicleNotFoundException::new);
        }
        return apartmentIds;
    }

    public List<Long> lockOwnerVoidApartmentContexts(Long vehicleId) {
        List<Long> apartmentIds = discoveryTransactionTemplate.execute(status ->
                relationRepository.findAllByVehicle_IdOrderById(vehicleId).stream()
                        .filter(relation -> relation.getStatus() != VehicleRelationStatus.VOID
                                && relation.getRelationType() == VehicleRelationType.AUTHORIZED_USER
                                && relation.getGuarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                                && relation.getGuarantorApartment() != null)
                        .map(relation -> relation.getGuarantorApartment().getId())
                        .distinct().sorted().toList());
        for (Long apartmentId : apartmentIds) {
            apartmentRepository.findByIdForUpdate(apartmentId).orElseThrow(VehicleNotFoundException::new);
        }
        return apartmentIds;
    }

    @Override
    public void invalidateForMembershipLosses(List<MembershipAuthorityLoss> losses, User actor) {
        applyMembershipLosses(losses, actor, false);
    }

    @Override
    public void scheduleForMembershipLosses(List<MembershipAuthorityLoss> losses, User actor) {
        applyMembershipLosses(losses, actor, true);
    }

    private void applyMembershipLosses(
            List<MembershipAuthorityLoss> losses, User actor, boolean deferEffectiveGrants) {
        if (losses == null || losses.isEmpty()) {
            return;
        }
        List<MembershipAuthorityLoss> orderedLosses = losses.stream()
                .sorted(java.util.Comparator.comparing(MembershipAuthorityLoss::effectiveAt)).toList();
        TreeSet<Long> vehicleIds = discoveryTransactionTemplate.execute(status -> {
            TreeSet<Long> result = new TreeSet<>();
            for (MembershipAuthorityLoss loss : orderedLosses) {
                result.addAll(relationRepository.findVehiclesAffectedByMembershipLoss(
                        loss.apartmentId(), loss.residentId(), loss.effectiveAt(), VehicleRelationType.AUTHORIZED_USER,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationType.OWNER,
                        VehicleRelationStatus.ACTIVE));
            }
            return result;
        });
        for (Long vehicleId : vehicleIds) {
            vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
            List<VehicleResidentRelation> relations = relationRepository.findAllActiveForVehicleForUpdate(
                    vehicleId, VehicleRelationStatus.ACTIVE);
            for (MembershipAuthorityLoss loss : orderedLosses) {
                invalidateMembershipLoss(loss, actor, relations, deferEffectiveGrants);
            }
        }
    }

    @Override
    public void voidDependentVehicleRightsForMembership(MembershipVoidSource membership, User actor) {
        List<Long> discoveredVehicleIds = discoveryTransactionTemplate.execute(status ->
                relationRepository.findVehiclesAffectedByMembershipVoid(
                        membership.apartmentId(), membership.residentId(),
                        membership.memberRole() == MembershipRole.HOUSEHOLD_HEAD,
                        membership.validFrom(), membership.validTo(),
                        VehicleRelationType.AUTHORIZED_USER,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationType.OWNER,
                        VehicleRelationStatus.VOID, VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED));
        TreeSet<Long> vehicleIds = new TreeSet<>(discoveredVehicleIds);
        for (Long vehicleId : vehicleIds) {
            vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
            List<VehicleResidentRelation> relations = relationRepository.findAllForVehicleForUpdate(vehicleId);
            for (VehicleResidentRelation relation : relations) {
                if (isMembershipVoidDependent(relation, membership, relations)) {
                    voidAuthorizedUser(relation, membership.commandTime(), membership.reason(), actor);
                }
            }
        }
    }

    public void voidDependentAuthorizedUsersForOwner(
            VehicleResidentRelation owner,
            List<VehicleResidentRelation> lockedRelations,
            List<Long> apartmentIds,
            LocalDateTime commandTime,
            String reason,
            User actor) {
        Map<Long, List<ApartmentMembership>> membershipsByApartment = new java.util.LinkedHashMap<>();
        for (Long apartmentId : apartmentIds) {
            membershipsByApartment.put(apartmentId, membershipRepository
                    .findAllForApartmentAndResidentForUpdate(apartmentId, owner.getResident().getId()));
        }
        for (VehicleResidentRelation relation : lockedRelations) {
            if (isOwnerVoidDependent(relation, owner, membershipsByApartment)) {
                voidAuthorizedUser(relation, commandTime, reason, actor);
            }
        }
    }

    private boolean isMembershipVoidDependent(
            VehicleResidentRelation relation,
            MembershipVoidSource membership,
            List<VehicleResidentRelation> relations) {
        if (relation.getStatus() == VehicleRelationStatus.VOID
                || relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                || relation.getGuarantorType() != VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                || relation.getGuarantorApartment() == null
                || !membership.apartmentId().equals(relation.getGuarantorApartment().getId())) {
            return false;
        }
        boolean membershipCoversGrantStart = intervalContains(
                membership.validFrom(), membership.validTo(), relation.getValidFrom());
        boolean membershipWasEffectiveAtCancellation = relation.getStatus()
                == VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED
                && intervalContainsIncludingEnd(membership.validFrom(), membership.validTo(),
                        relation.getLifecycleChangedAt());
        boolean sourceWasHouseholdHead = membership.memberRole() == MembershipRole.HOUSEHOLD_HEAD
                && membership.residentId().equals(relation.getGuarantorResident().getId())
                && (membershipCoversGrantStart || membershipWasEffectiveAtCancellation);
        boolean sourceWasVehicleOwner = relations.stream().anyMatch(owner ->
                owner.getRelationType() == VehicleRelationType.OWNER
                        && owner.getStatus() != VehicleRelationStatus.VOID
                        && membership.residentId().equals(owner.getResident().getId())
                        && (membershipCoversGrantStart
                                && intervalContains(owner.getValidFrom(), owner.getValidTo(), relation.getValidFrom())
                                || membershipWasEffectiveAtCancellation
                                        && intervalContainsIncludingEnd(owner.getValidFrom(), owner.getValidTo(),
                                                relation.getLifecycleChangedAt())));
        return sourceWasHouseholdHead || sourceWasVehicleOwner;
    }

    private boolean isOwnerVoidDependent(
            VehicleResidentRelation relation,
            VehicleResidentRelation owner,
            Map<Long, List<ApartmentMembership>> membershipsByApartment) {
        if (relation.getStatus() == VehicleRelationStatus.VOID
                || relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER) {
            return false;
        }
        boolean ownerCoveredGrantStart = intervalContains(
                owner.getValidFrom(), owner.getValidTo(), relation.getValidFrom());
        boolean cancelledAtOwnerLoss = owner.getValidTo() != null
                && relation.getStatus() == VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED
                && owner.getValidTo().equals(relation.getLifecycleChangedAt())
                && (owner.getLifecycleReason() == null
                        || owner.getLifecycleReason().equals(relation.getLifecycleReason()));
        if (!ownerCoveredGrantStart && !cancelledAtOwnerLoss) {
            return false;
        }
        boolean directOwnerGuarantor = relation.getGuarantorType() == VehicleRelationGuarantorType.OWNER
                && relation.getGuarantorResident() != null
                && owner.getResident().getId().equals(relation.getGuarantorResident().getId());
        Long apartmentId = relation.getGuarantorApartment() == null
                ? null : relation.getGuarantorApartment().getId();
        List<ApartmentMembership> memberships = apartmentId == null
                ? List.of() : membershipsByApartment.getOrDefault(apartmentId, List.of());
        boolean householdOwnerMember = relation.getGuarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                && (ownerCoveredGrantStart && memberships.stream()
                        .filter(membership -> membership.getStatus() != MembershipStatus.VOID)
                        .anyMatch(membership -> intervalContains(
                                membership.getValidFrom(), membership.getValidTo(), relation.getValidFrom()))
                        || cancelledAtOwnerLoss && memberships.stream()
                                .filter(membership -> membership.getStatus() != MembershipStatus.VOID)
                                .anyMatch(membership -> intervalContains(
                                membership.getValidFrom(), membership.getValidTo(), owner.getValidTo())));
        return directOwnerGuarantor || householdOwnerMember;
    }

    private boolean intervalContains(LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }

    private boolean intervalContainsIncludingEnd(
            LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime at) {
        return !validFrom.isAfter(at) && (validTo == null || !validTo.isBefore(at));
    }

    private void voidAuthorizedUser(
            VehicleResidentRelation relation, LocalDateTime commandTime, String reason, User actor) {
        String oldData = auditData(relation, relation.getLifecycleReason());
        relation.setStatus(VehicleRelationStatus.VOID);
        relation.setLifecycleChangedAt(commandTime);
        relation.setLifecycleReason(reason);
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        auditService.record("VEHICLE_RIGHT_VOIDED", RELATION_ENTITY, saved.getId().toString(), actor,
                oldData, auditData(saved, reason));
    }

    @Override
    public ResidentStatusResources discoverResidentStatusResources(
            Long residentId,
            ResidentStatus status,
            LocalDateTime statusAt,
            List<VehicleRightAction> vehicleActions,
            List<StatusMembershipAction> membershipActions) {
        return discoveryTransactionTemplate.execute(transaction -> {
            TreeSet<Long> residentIds = new TreeSet<>();
            TreeSet<Long> apartmentIds = new TreeSet<>();
            TreeSet<Long> vehicleIds = new TreeSet<>();
            Set<Long> eligibleActionIds = new HashSet<>();
            residentIds.add(residentId);

            membershipRepository.findActiveForResident(residentId, MembershipStatus.ACTIVE)
                    .forEach(membership -> apartmentIds.add(membership.getApartment().getId()));

            List<VehicleResidentRelation> residentRelations = relationRepository
                    .findAllByResident_IdOrderById(residentId).stream()
                    .filter(relation -> relation.getStatus() == VehicleRelationStatus.ACTIVE)
                    .toList();
            residentRelations.forEach(relation -> addRelationResources(
                    relation, residentIds, apartmentIds, vehicleIds));
            residentRelations.forEach(relation -> eligibleActionIds.add(relation.getId()));

            if (status != ResidentStatus.ACTIVE) {
                relationRepository.findDependentAuthorizedUsersForResidentStatusLoss(
                                residentId, statusAt, VehicleRelationType.AUTHORIZED_USER, VehicleRelationType.OWNER,
                                VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationStatus.ACTIVE,
                                MembershipStatus.ACTIVE)
                        .forEach(relation -> {
                            eligibleActionIds.add(relation.getId());
                            addRelationResources(relation, residentIds, apartmentIds, vehicleIds);
                        });
            }

            for (StatusMembershipAction action : membershipActions) {
                var membership = membershipRepository.findById(action.membershipId())
                        .filter(candidate -> residentId.equals(candidate.getResident().getId()))
                        .orElseThrow(ResidentStatusResourceNotFoundException::new);
                Long apartmentId = membership.getApartment().getId();
                apartmentIds.add(apartmentId);
                List<Long> affectedVehicleIds = relationRepository.findVehiclesAffectedByMembershipLoss(
                        apartmentId, residentId, action.effectiveAt(), VehicleRelationType.AUTHORIZED_USER,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationType.OWNER,
                        VehicleRelationStatus.ACTIVE);
                vehicleIds.addAll(affectedVehicleIds);
                for (Long vehicleId : affectedVehicleIds) {
                    List<VehicleResidentRelation> relations = relationRepository
                            .findAllByVehicle_IdOrderById(vehicleId).stream()
                            .filter(relation -> relation.getStatus() == VehicleRelationStatus.ACTIVE)
                            .toList();
                    for (VehicleResidentRelation relation : relations) {
                        if (isMembershipLossDependent(relation,
                                new MembershipAuthorityLoss(apartmentId, residentId, action.effectiveAt(), action.reason()),
                                relations)) {
                            eligibleActionIds.add(relation.getId());
                        }
                    }
                }
            }

            for (Long vehicleId : List.copyOf(vehicleIds)) {
                relationRepository.findAllByVehicle_IdOrderById(vehicleId).stream()
                        .filter(relation -> relation.getStatus() == VehicleRelationStatus.ACTIVE)
                        .forEach(relation -> addRelationResources(
                                relation, residentIds, apartmentIds, vehicleIds));
            }

            for (VehicleRightAction action : vehicleActions) {
                VehicleResidentRelation relation = relationRepository.findById(action.relationId())
                        .orElseThrow(ResidentStatusResourceNotFoundException::new);
                if (!eligibleActionIds.contains(relation.getId())) {
                    throw new ResidentStatusResourceNotFoundException();
                }
                addRelationResources(relation, residentIds, apartmentIds, vehicleIds);
                if (relation.getRelationType() == VehicleRelationType.OWNER) {
                    List<VehicleResidentRelation> vehicleRelations = relationRepository
                            .findAllByVehicle_IdOrderById(relation.getVehicle().getId()).stream()
                            .filter(candidate -> candidate.getStatus() == VehicleRelationStatus.ACTIVE)
                            .toList();
                    vehicleRelations.forEach(candidate -> addRelationResources(
                            candidate, residentIds, apartmentIds, vehicleIds));
                    List<Long> ownerApartmentIds = relationRepository.findHouseholdGuarantorApartmentsForOwnerLoss(
                            relation.getVehicle().getId(), residentId, action.effectiveAt(),
                            VehicleRelationType.AUTHORIZED_USER, VehicleRelationGuarantorType.HOUSEHOLD_HEAD,
                            VehicleRelationStatus.ACTIVE, MembershipStatus.ACTIVE);
                    apartmentIds.addAll(ownerApartmentIds);
                    for (VehicleResidentRelation candidate : vehicleRelations) {
                        boolean affected = candidate.getRelationType() == VehicleRelationType.AUTHORIZED_USER
                                && (candidate.getValidTo() == null
                                        || candidate.getValidTo().isAfter(action.effectiveAt()))
                                && (candidate.getGuarantorType() == VehicleRelationGuarantorType.OWNER
                                        && candidate.getGuarantorResident() != null
                                        && residentId.equals(candidate.getGuarantorResident().getId())
                                        || candidate.getGuarantorType()
                                                        == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                                                && candidate.getGuarantorApartment() != null
                                                && ownerApartmentIds.contains(
                                                        candidate.getGuarantorApartment().getId()));
                        if (affected) {
                            eligibleActionIds.add(candidate.getId());
                        }
                    }
                }
            }
            return new ResidentStatusResources(
                    List.copyOf(residentIds), List.copyOf(apartmentIds), List.copyOf(vehicleIds));
        });
    }

    @Override
    public void applyResidentStatusChange(
            Long residentId,
            ResidentStatus previousStatus,
            ResidentStatus targetStatus,
            LocalDateTime statusAt,
            String reason,
            List<MembershipAuthorityLoss> membershipLosses,
            List<VehicleRightAction> vehicleActions,
            User actor,
            ResidentStatusResources lockedResources) {
        Map<Long, List<VehicleResidentRelation>> relationsByVehicle = new java.util.LinkedHashMap<>();
        for (Long vehicleId : lockedResources.vehicleIds()) {
            vehicleRepository.findByIdForUpdate(vehicleId).orElseThrow(VehicleNotFoundException::new);
            relationsByVehicle.put(vehicleId, relationRepository.findAllActiveForVehicleForUpdate(
                    vehicleId, VehicleRelationStatus.ACTIVE));
        }

        Set<Long> actionIds = vehicleActions.stream().map(VehicleRightAction::relationId)
                .collect(java.util.stream.Collectors.toSet());
        List<VehicleResidentRelation> affectedByStatus = targetStatus == ResidentStatus.ACTIVE
                ? List.of()
                : relationRepository.findDependentAuthorizedUsersForResidentStatusLoss(
                        residentId, statusAt, VehicleRelationType.AUTHORIZED_USER, VehicleRelationType.OWNER,
                        VehicleRelationGuarantorType.HOUSEHOLD_HEAD, VehicleRelationStatus.ACTIVE,
                        MembershipStatus.ACTIVE);
        Set<Long> affectedByStatusIds = affectedByStatus.stream().map(VehicleResidentRelation::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (targetStatus == ResidentStatus.INACTIVE) {
            List<VehicleResidentRelation> directEffectiveRelations = relationsByVehicle.values().stream()
                    .flatMap(List::stream)
                    .filter(relation -> residentId.equals(relation.getResident().getId()))
                    .filter(relation -> effectiveAt(relation.getValidFrom(), relation.getValidTo(), statusAt))
                    .toList();
            if (directEffectiveRelations.stream().anyMatch(relation -> !actionIds.contains(relation.getId()))) {
                throw new ResidentStatusConflictException();
            }
            Set<Long> coveredByMembershipLossIds = new HashSet<>();
            for (MembershipAuthorityLoss loss : membershipLosses) {
                for (List<VehicleResidentRelation> relations : relationsByVehicle.values()) {
                    for (VehicleResidentRelation relation : relations) {
                        if (isMembershipLossDependent(relation, loss, relations)) {
                            coveredByMembershipLossIds.add(relation.getId());
                        }
                    }
                }
            }
            Set<Long> coveredByOwnerLossIds = vehicleActions.stream()
                    .filter(action -> findLockedRelation(relationsByVehicle, action.relationId()) != null)
                    .filter(action -> findLockedRelation(relationsByVehicle, action.relationId()).getRelationType()
                            == VehicleRelationType.OWNER)
                    .flatMap(action -> {
                        VehicleResidentRelation ownerRelation = findLockedRelation(relationsByVehicle, action.relationId());
                        List<VehicleResidentRelation> relations = relationsByVehicle.get(ownerRelation.getVehicle().getId());
                        return relations.stream().filter(relation -> isOwnerLossDependent(
                                relation, residentId, action.effectiveAt()))
                                .map(VehicleResidentRelation::getId);
                    }).collect(java.util.stream.Collectors.toSet());
            Set<Long> explicitlyCoveredDependencies = new HashSet<>(coveredByMembershipLossIds);
            explicitlyCoveredDependencies.addAll(coveredByOwnerLossIds);
            boolean uncoveredDependency = affectedByStatusIds.stream()
                    .filter(id -> findLockedRelation(relationsByVehicle, id) != null)
                    .filter(id -> effectiveAt(findLockedRelation(relationsByVehicle, id).getValidFrom(),
                            findLockedRelation(relationsByVehicle, id).getValidTo(), statusAt))
                    .anyMatch(id -> !actionIds.contains(id)
                            && !explicitlyCoveredDependencies.contains(id));
            if (uncoveredDependency) {
                throw new ResidentStatusConflictException();
            }
        }

        List<ResidentStatusLifecycleEvent> lifecycleEvents = new java.util.ArrayList<>();
        for (VehicleRightAction action : vehicleActions) {
            VehicleResidentRelation relation = findLockedRelation(relationsByVehicle, action.relationId());
            int priority = relation != null && relation.getRelationType() == VehicleRelationType.OWNER ? 2 : 0;
            lifecycleEvents.add(new ResidentStatusLifecycleEvent(
                    action.effectiveAt(), priority, action.relationId(), action, null));
        }
        for (MembershipAuthorityLoss loss : membershipLosses) {
            lifecycleEvents.add(new ResidentStatusLifecycleEvent(
                    loss.effectiveAt(), 1, loss.apartmentId(), null, loss));
        }
        lifecycleEvents.sort(java.util.Comparator.comparing(ResidentStatusLifecycleEvent::effectiveAt)
                .thenComparingInt(ResidentStatusLifecycleEvent::priority)
                .thenComparing(ResidentStatusLifecycleEvent::resourceId));
        for (ResidentStatusLifecycleEvent event : lifecycleEvents) {
            if (event.vehicleAction() != null) {
                VehicleRightAction action = event.vehicleAction();
                VehicleResidentRelation relation = findLockedRelation(relationsByVehicle, action.relationId());
                if (relation == null || relation.getStatus() != VehicleRelationStatus.ACTIVE
                        || !action.effectiveAt().isAfter(relation.getValidFrom())
                        || action.effectiveAt().isAfter(statusAt)
                        || relation.getValidTo() != null && action.effectiveAt().isAfter(relation.getValidTo())) {
                    throw new ResidentStatusRelationStateConflictException();
                }
                if (!residentId.equals(relation.getResident().getId())
                        && !affectedByStatusIds.contains(relation.getId())
                        && !isAffectedByExplicitLoss(relation, residentId, membershipLosses,
                                vehicleActions, relationsByVehicle)) {
                    throw new ResidentStatusResourceNotFoundException();
                }
                if (relation.getRelationType() == VehicleRelationType.OWNER) {
                    List<Long> householdApartments = relationsByVehicle.get(relation.getVehicle().getId()).stream()
                            .filter(candidate -> candidate.getGuarantorType()
                                    == VehicleRelationGuarantorType.HOUSEHOLD_HEAD)
                            .map(candidate -> candidate.getGuarantorApartment() == null
                                    ? null : candidate.getGuarantorApartment().getId())
                            .filter(java.util.Objects::nonNull).distinct().sorted().toList();
                    invalidateOwnerLoss(residentId, action.effectiveAt(), action.reason(), actor,
                            householdApartments, relationsByVehicle.get(relation.getVehicle().getId()));
                }
                endOrRevoke(relation, action, statusAt, actor);
            } else {
                for (List<VehicleResidentRelation> relations : relationsByVehicle.values()) {
                    invalidateMembershipLoss(event.membershipLoss(), actor, relations, false);
                }
            }
        }

        if (targetStatus != ResidentStatus.ACTIVE) {
            for (VehicleResidentRelation relation : affectedByStatus) {
                VehicleResidentRelation locked = findLockedRelation(relationsByVehicle, relation.getId());
                if (locked != null && locked.getStatus() == VehicleRelationStatus.ACTIVE
                        && (locked.getValidTo() == null || locked.getValidTo().isAfter(statusAt))) {
                    invalidate(locked, statusAt, reason, actor);
                }
            }
        }
    }

    private void endOrRevoke(VehicleResidentRelation relation, VehicleRightAction action,
            LocalDateTime changedAt, User actor) {
        String oldData = auditData(relation, relation.getLifecycleReason());
        VehicleRelationStatus nextStatus = action.action() == RelationLifecycleAction.END
                ? VehicleRelationStatus.INACTIVE : VehicleRelationStatus.REVOKED;
        relation.setStatus(nextStatus);
        relation.setValidTo(action.effectiveAt());
        relation.setLifecycleChangedAt(changedAt);
        relation.setLifecycleReason(action.reason());
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        String event = action.action() == RelationLifecycleAction.END
                ? "VEHICLE_RIGHT_ENDED" : "VEHICLE_RIGHT_REVOKED";
        auditService.record(event, RELATION_ENTITY, saved.getId().toString(), actor, oldData,
                auditData(saved, action.reason()));
    }

    private void addRelationResources(
            VehicleResidentRelation relation, Set<Long> residentIds, Set<Long> apartmentIds, Set<Long> vehicleIds) {
        residentIds.add(relation.getResident().getId());
        if (relation.getGuarantorResident() != null) {
            residentIds.add(relation.getGuarantorResident().getId());
        }
        if (relation.getGuarantorApartment() != null) {
            apartmentIds.add(relation.getGuarantorApartment().getId());
        }
        vehicleIds.add(relation.getVehicle().getId());
    }

    private VehicleResidentRelation findLockedRelation(
            java.util.Map<Long, List<VehicleResidentRelation>> relationsByVehicle, Long relationId) {
        return relationsByVehicle.values().stream().flatMap(List::stream)
                .filter(relation -> relationId.equals(relation.getId())).findFirst().orElse(null);
    }

    private boolean effectiveAt(LocalDateTime validFrom, LocalDateTime validTo, LocalDateTime at) {
        return !validFrom.isAfter(at) && (validTo == null || validTo.isAfter(at));
    }

    private boolean isMembershipLossDependent(
            VehicleResidentRelation relation, MembershipAuthorityLoss loss, List<VehicleResidentRelation> relations) {
        if (relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                || relation.getStatus() != VehicleRelationStatus.ACTIVE
                || relation.getValidTo() != null && !relation.getValidTo().isAfter(loss.effectiveAt())
                || relation.getGuarantorType() != VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                || relation.getGuarantorApartment() == null
                || !loss.apartmentId().equals(relation.getGuarantorApartment().getId())) {
            return false;
        }
        boolean sourceOwnsVehicleAtLoss = relations.stream().anyMatch(owner ->
                owner.getRelationType() == VehicleRelationType.OWNER
                        && owner.getStatus() == VehicleRelationStatus.ACTIVE
                        && loss.residentId().equals(owner.getResident().getId())
                        && !owner.getValidFrom().isAfter(loss.effectiveAt())
                        && (owner.getValidTo() == null || !owner.getValidTo().isBefore(loss.effectiveAt())));
        return loss.residentId().equals(relation.getGuarantorResident().getId()) || sourceOwnsVehicleAtLoss;
    }

    private boolean isOwnerLossDependent(
            VehicleResidentRelation relation, Long ownerResidentId, LocalDateTime effectiveAt) {
        if (relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                || relation.getStatus() != VehicleRelationStatus.ACTIVE
                || relation.getValidTo() != null && !relation.getValidTo().isAfter(effectiveAt)) {
            return false;
        }
        boolean directOwnerGuarantor = relation.getGuarantorType() == VehicleRelationGuarantorType.OWNER
                && relation.getGuarantorResident() != null
                && ownerResidentId.equals(relation.getGuarantorResident().getId());
        boolean householdOwnerMember = relation.getGuarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                && relation.getGuarantorApartment() != null
                && !membershipRepository.findEffectiveForUpdate(relation.getGuarantorApartment().getId(),
                        ownerResidentId, effectiveAt, MembershipStatus.ACTIVE).isEmpty();
        return directOwnerGuarantor || householdOwnerMember;
    }

    private boolean isAffectedByExplicitLoss(
            VehicleResidentRelation relation,
            Long residentId,
            List<MembershipAuthorityLoss> membershipLosses,
            List<VehicleRightAction> vehicleActions,
            Map<Long, List<VehicleResidentRelation>> relationsByVehicle) {
        for (MembershipAuthorityLoss loss : membershipLosses) {
            List<VehicleResidentRelation> relations = relationsByVehicle.get(relation.getVehicle().getId());
            if (relations != null && isMembershipLossDependent(relation, loss, relations)) {
                return true;
            }
        }
        for (VehicleRightAction action : vehicleActions) {
            VehicleResidentRelation owner = findLockedRelation(relationsByVehicle, action.relationId());
            if (owner != null && owner.getRelationType() == VehicleRelationType.OWNER
                    && residentId.equals(owner.getResident().getId())
                    && owner.getVehicle().getId().equals(relation.getVehicle().getId())
                    && isOwnerLossDependent(relation, residentId, action.effectiveAt())) {
                return true;
            }
        }
        return false;
    }

    private void invalidateMembershipLoss(
            MembershipAuthorityLoss loss,
            User actor,
            List<VehicleResidentRelation> relations,
            boolean deferEffectiveGrants) {
        boolean sourceResidentOwnedVehicleAtLoss = relations.stream()
                .anyMatch(relation -> relation.getRelationType() == VehicleRelationType.OWNER
                        && relation.getStatus() == VehicleRelationStatus.ACTIVE
                        && loss.residentId().equals(relation.getResident().getId())
                        && !relation.getValidFrom().isAfter(loss.effectiveAt())
                        && (relation.getValidTo() == null || !relation.getValidTo().isBefore(loss.effectiveAt())));
        for (VehicleResidentRelation relation : relations) {
            if (relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                    || relation.getStatus() != VehicleRelationStatus.ACTIVE
                    || relation.getValidTo() != null && !relation.getValidTo().isAfter(loss.effectiveAt())
                    || relation.getGuarantorType() != VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                    || relation.getGuarantorApartment() == null
                    || !loss.apartmentId().equals(relation.getGuarantorApartment().getId())) {
                continue;
            }
            boolean householdHeadLost = loss.residentId().equals(relation.getGuarantorResident().getId());
            if (householdHeadLost || sourceResidentOwnedVehicleAtLoss) {
                if (deferEffectiveGrants && loss.effectiveAt().isAfter(relation.getValidFrom())) {
                    scheduleTransition(relation, loss.effectiveAt(), loss.reason(), actor);
                } else {
                    invalidate(relation, loss.effectiveAt(), loss.reason(), actor);
                }
            }
        }
    }

    public void invalidateOwnerLoss(
            Long ownerResidentId,
            LocalDateTime effectiveAt,
            String reason,
            User actor,
            List<Long> apartmentIds,
            List<VehicleResidentRelation> lockedRelations) {
        applyOwnerLoss(ownerResidentId, effectiveAt, reason, actor, apartmentIds, lockedRelations, false);
    }

    public void scheduleOwnerLoss(
            Long ownerResidentId,
            LocalDateTime effectiveAt,
            String reason,
            User actor,
            List<Long> apartmentIds,
            List<VehicleResidentRelation> lockedRelations) {
        applyOwnerLoss(ownerResidentId, effectiveAt, reason, actor, apartmentIds, lockedRelations, true);
    }

    private void applyOwnerLoss(
            Long ownerResidentId,
            LocalDateTime effectiveAt,
            String reason,
            User actor,
            List<Long> apartmentIds,
            List<VehicleResidentRelation> lockedRelations,
            boolean deferEffectiveGrants) {
        Set<Long> effectiveOwnerMembershipApartments = new HashSet<>();
        for (Long apartmentId : apartmentIds) {
            if (!membershipRepository.findEffectiveForUpdate(
                    apartmentId, ownerResidentId, effectiveAt, MembershipStatus.ACTIVE).isEmpty()) {
                effectiveOwnerMembershipApartments.add(apartmentId);
            }
        }

        for (VehicleResidentRelation relation : lockedRelations) {
            if (relation.getRelationType() != VehicleRelationType.AUTHORIZED_USER
                    || relation.getStatus() != VehicleRelationStatus.ACTIVE
                    || relation.getValidTo() != null && !relation.getValidTo().isAfter(effectiveAt)) {
                continue;
            }
            boolean ownerGuarantor = relation.getGuarantorType() == VehicleRelationGuarantorType.OWNER
                    && relation.getGuarantorResident() != null
                    && ownerResidentId.equals(relation.getGuarantorResident().getId());
            boolean householdOwnerMember = relation.getGuarantorType() == VehicleRelationGuarantorType.HOUSEHOLD_HEAD
                    && relation.getGuarantorApartment() != null
                    && effectiveOwnerMembershipApartments.contains(relation.getGuarantorApartment().getId());
            if (ownerGuarantor || householdOwnerMember) {
                if (deferEffectiveGrants && effectiveAt.isAfter(relation.getValidFrom())) {
                    scheduleTransition(relation, effectiveAt, reason, actor);
                } else {
                    invalidate(relation, effectiveAt, reason, actor);
                }
            }
        }
    }

    private void scheduleTransition(
            VehicleResidentRelation relation, LocalDateTime effectiveAt, String reason, User actor) {
        VehicleRightPendingTransition existingPending = pendingTransitionRepository
                .findByVehicleRight_IdForUpdate(relation.getId()).orElse(null);
        if (existingPending != null) {
            if (!effectiveAt.isBefore(existingPending.getEffectiveAt())) {
                return;
            }
            relation.setValidTo(effectiveAt);
            relationRepository.saveAndFlush(relation);
            existingPending.setEffectiveAt(effectiveAt);
            existingPending.setReason(reason);
            existingPending.setSourceActor(actor);
            pendingTransitionRepository.saveAndFlush(existingPending);
            return;
        }

        relation.setValidTo(effectiveAt);
        VehicleResidentRelation cappedRelation = relationRepository.saveAndFlush(relation);
        VehicleRightPendingTransition pending = new VehicleRightPendingTransition();
        pending.setVehicleRight(cappedRelation);
        pending.setEffectiveAt(effectiveAt);
        pending.setReason(reason);
        pending.setSourceActor(actor);
        pendingTransitionRepository.saveAndFlush(pending);
    }

    private void invalidate(VehicleResidentRelation relation, LocalDateTime effectiveAt, String reason, User actor) {
        String oldData = auditData(relation, relation.getLifecycleReason());
        boolean preEffective = !effectiveAt.isAfter(relation.getValidFrom());
        relation.setStatus(preEffective
                ? VehicleRelationStatus.PRE_EFFECTIVE_CANCELLED
                : VehicleRelationStatus.INACTIVE);
        relation.setValidTo(preEffective ? null : effectiveAt);
        relation.setLifecycleChangedAt(effectiveAt);
        relation.setLifecycleReason(reason);
        VehicleResidentRelation saved = relationRepository.saveAndFlush(relation);
        String action = preEffective
                ? "VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_CANCELLED"
                : "VEHICLE_AUTHORIZED_USER_GUARANTOR_LOSS_ENDED";
        auditService.record(action, RELATION_ENTITY, saved.getId().toString(), actor, oldData,
                auditData(saved, reason));
    }

    public void processDueTransition(
            VehicleResidentRelation relation, LocalDateTime effectiveAt, String reason, User actor) {
        invalidate(relation, effectiveAt, reason, actor);
    }

    private String auditData(VehicleResidentRelation relation, String reason) {
        try {
            return objectMapper.writeValueAsString(new VehicleRightInvalidationAuditData(
            relation.getVehicle().getId(), relation.getResident().getId(), relation.getRelationType(),
                    relation.getGuarantorType(), relation.getGuarantorResident() == null
                            ? null : relation.getGuarantorResident().getId(),
                    relation.getGuarantorApartment() == null ? null : relation.getGuarantorApartment().getId(),
                    relation.getValidFrom(), relation.getValidTo(), relation.getStatus(), reason));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize guarantor-loss audit data", exception);
        }
    }

    private record VehicleRightInvalidationAuditData(
            @JsonProperty("vehicle_id") Long vehicleId,
            @JsonProperty("resident_id") Long residentId,
            @JsonProperty("relation_type") VehicleRelationType relationType,
            @JsonProperty("guarantor_type") VehicleRelationGuarantorType guarantorType,
            @JsonProperty("guarantor_resident_id") Long guarantorResidentId,
            @JsonProperty("guarantor_apartment_id") Long guarantorApartmentId,
            @JsonProperty("valid_from") LocalDateTime validFrom,
            @JsonProperty("valid_to") LocalDateTime validTo,
            @JsonProperty("status") VehicleRelationStatus status,
            @JsonProperty("reason") String reason) {}

    private record ResidentStatusLifecycleEvent(
            LocalDateTime effectiveAt,
            int priority,
            Long resourceId,
            VehicleRightAction vehicleAction,
            MembershipAuthorityLoss membershipLoss) {}
}
