package com.example.alocacao.web.dto;

import com.example.alocacao.domain.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Objetos de entrada e saída da API (o documento do banco nunca é exposto diretamente). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- entrada ----------

    public record CreateReservationRequest(
            @NotNull(message = "Informe ao menos um recurso")
            @Size(min = 1, max = 10, message = "Informe de 1 a 10 recursos")
            List<@NotBlank(message = "Id de recurso inválido") String> resourceIds,
            @NotNull(message = "O início é obrigatório") LocalDateTime startTime,
            @NotNull(message = "O término é obrigatório") LocalDateTime endTime,
            @NotBlank(message = "A finalidade é obrigatória")
            @Size(max = 255, message = "A finalidade deve ter no máximo 255 caracteres") String purpose) {
    }

    public record UpdateReservationRequest(
            @NotNull(message = "O início é obrigatório") LocalDateTime startTime,
            @NotNull(message = "O término é obrigatório") LocalDateTime endTime,
            @NotBlank(message = "A finalidade é obrigatória")
            @Size(max = 255, message = "A finalidade deve ter no máximo 255 caracteres") String purpose) {
    }

    public record DecisionRequest(
            @Size(max = 255, message = "A observação deve ter no máximo 255 caracteres") String note) {
    }

    public record ResourceRequest(
            @NotNull(message = "O tipo é obrigatório") ResourceType type,
            @NotBlank(message = "O nome é obrigatório")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres") String name,
            @Size(max = 120, message = "A localização deve ter no máximo 120 caracteres") String location,
            @Positive(message = "A capacidade deve ser maior que zero") Integer capacity,
            @Size(max = 300, message = "As competências devem ter no máximo 300 caracteres") String competencies,
            boolean restricted) {
    }

    public record MaintenanceRequest(
            @NotNull(message = "O início é obrigatório") LocalDateTime startTime,
            @NotNull(message = "O término é obrigatório") LocalDateTime endTime,
            @NotBlank(message = "O motivo é obrigatório")
            @Size(max = 255, message = "O motivo deve ter no máximo 255 caracteres") String reason) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "O usuário é obrigatório")
            @Size(min = 3, max = 60, message = "O usuário deve ter de 3 a 60 caracteres")
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Use apenas letras, números, ponto, hífen e sublinhado")
            String username,
            @NotBlank(message = "A senha é obrigatória")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres") String password,
            @NotBlank(message = "O nome é obrigatório")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres") String fullName,
            @NotBlank(message = "O e-mail é obrigatório")
            @Email(message = "E-mail inválido")
            @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres") String email,
            @NotNull(message = "O perfil é obrigatório") Role role) {
    }

    // ---------- saída ----------

    public record ResourceView(String id, ResourceType type, String name, String location, Integer capacity,
                               String competencies, boolean restricted, boolean active) {
        public static ResourceView from(Resource r) {
            return new ResourceView(r.getId(), r.getType(), r.getName(), r.getLocation(), r.getCapacity(),
                    r.getCompetencies(), r.isRestricted(), r.isActive());
        }
    }

    public record MaintenanceView(String id, String resourceId, LocalDateTime startTime, LocalDateTime endTime,
                                  String reason) {
        public static MaintenanceView from(MaintenanceWindow m) {
            return new MaintenanceView(m.getId(), m.getResourceId(), m.getStartTime(), m.getEndTime(),
                    m.getReason());
        }
    }

    public record ItemView(String id, String name, ResourceType type, boolean restricted) {
        public static ItemView from(ReservationItem i) {
            return new ItemView(i.getResourceId(), i.getResourceName(), i.getResourceType(), i.isRestricted());
        }
    }

    public record ReservationView(String id, String requesterId, String requesterName, LocalDateTime startTime,
                                  LocalDateTime endTime, String purpose, ReservationStatus status,
                                  boolean requiresApproval, List<ItemView> resources,
                                  LocalDateTime createdAt, LocalDateTime updatedAt,
                                  LocalDateTime checkedOutAt, String checkedOutBy,
                                  LocalDateTime returnedAt, String returnedBy, String decisionNote) {
        public static ReservationView from(Reservation r) {
            List<ItemView> resources = r.getItems().stream()
                    .map(ItemView::from)
                    .sorted(Comparator.comparing(ItemView::name))
                    .toList();
            boolean restricted = resources.stream().anyMatch(ItemView::restricted);
            return new ReservationView(r.getId(), r.getRequesterId(), r.getRequesterName(),
                    r.getStartTime(), r.getEndTime(), r.getPurpose(), r.getStatus(), restricted, resources,
                    r.getCreatedAt(), r.getUpdatedAt(), r.getCheckedOutAt(), r.getCheckedOutBy(),
                    r.getReturnedAt(), r.getReturnedBy(), r.getDecisionNote());
        }
    }

    public record AuditView(String id, String entityType, String entityId, String action, String fromStatus,
                            String toStatus, String actor, String details, LocalDateTime createdAt) {
        public static AuditView from(AuditEntry a) {
            return new AuditView(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(), a.getFromStatus(),
                    a.getToStatus(), a.getActor(), a.getDetails(), a.getCreatedAt());
        }
    }

    public record NotificationView(String id, String recipient, String subject, String message, String channel,
                                   boolean delivered, LocalDateTime createdAt) {
        public static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getRecipient(), n.getSubject(), n.getMessage(),
                    n.getChannel(), n.isDelivered(), n.getCreatedAt());
        }
    }

    public record UserView(String id, String username, String fullName, String email, Role role, boolean enabled) {
        public static UserView from(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.isEnabled());
        }
    }

    public record ResourceUsage(String resourceId, String name, ResourceType type, long reservations,
                                double allocatedHours) {
    }

    public record UtilizationReport(LocalDate from, LocalDate to, long conflictsAvoided,
                                    double totalAllocatedHours, List<ResourceUsage> resources) {
    }
}
