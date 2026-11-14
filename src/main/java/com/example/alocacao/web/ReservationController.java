package com.example.alocacao.web;

import com.example.alocacao.domain.ReservationStatus;
import com.example.alocacao.security.AppUserPrincipal;
import com.example.alocacao.service.ReservationService;
import com.example.alocacao.web.dto.Dtos.CreateReservationRequest;
import com.example.alocacao.web.dto.Dtos.DecisionRequest;
import com.example.alocacao.web.dto.Dtos.ReservationView;
import com.example.alocacao.web.dto.Dtos.UpdateReservationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservas")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista reservas (solicitante vê só as próprias)")
    public List<ReservationView> list(@AuthenticationPrincipal AppUserPrincipal me,
                                      @RequestParam(required = false) ReservationStatus status) {
        return service.list(CurrentUserSupport.of(me), status);
    }

    @GetMapping("/{id}")
    public ReservationView get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        return service.get(CurrentUserSupport.of(me), id);
    }

    @PostMapping
    @Operation(summary = "Cria reserva. 409 em conflito de horário; 422 se algum recurso estiver em manutenção")
    public ResponseEntity<ReservationView> create(@AuthenticationPrincipal AppUserPrincipal me,
                                                  @Valid @RequestBody CreateReservationRequest request) {
        ReservationView view = service.create(CurrentUserSupport.of(me), request);
        return ResponseEntity.created(URI.create("/api/reservations/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera horário e finalidade de reserva solicitada ou aprovada")
    public ReservationView update(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id,
                                  @Valid @RequestBody UpdateReservationRequest request) {
        return service.update(CurrentUserSupport.of(me), id, request);
    }

    @PostMapping("/{id}/cancel")
    public ReservationView cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        return service.cancel(CurrentUserSupport.of(me), id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprova (somente RESPONSAVEL)")
    public ReservationView approve(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id,
                                   @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.approve(CurrentUserSupport.of(me), id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Rejeita (somente RESPONSAVEL)")
    public ReservationView reject(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id,
                                  @Valid @RequestBody(required = false) DecisionRequest body) {
        return service.reject(CurrentUserSupport.of(me), id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Registra a retirada do material / início do uso (somente RESPONSAVEL)")
    public ReservationView checkout(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        return service.checkout(CurrentUserSupport.of(me), id);
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "Registra a devolução e conclui a reserva (somente RESPONSAVEL)")
    public ReservationView giveBack(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        return service.giveBack(CurrentUserSupport.of(me), id);
    }

    @PostMapping("/{id}/no-show")
    @Operation(summary = "Registra não comparecimento, após o horário de início (somente RESPONSAVEL)")
    public ReservationView noShow(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        return service.noShow(CurrentUserSupport.of(me), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Apaga reserva. 409 se já foi iniciada (EM_USO ou CONCLUIDA)")
    public void delete(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable String id) {
        service.delete(CurrentUserSupport.of(me), id);
    }
}
