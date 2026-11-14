package com.example.alocacao.web;

import com.example.alocacao.repository.NotificationRepository;
import com.example.alocacao.service.ReportService;
import com.example.alocacao.web.dto.Dtos.NotificationView;
import com.example.alocacao.web.dto.Dtos.UtilizationReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Relatórios e notificações")
public class ReportController {

    private final ReportService reports;
    private final NotificationRepository notifications;

    public ReportController(ReportService reports, NotificationRepository notifications) {
        this.reports = reports;
        this.notifications = notifications;
    }

    @GetMapping("/reports/utilization")
    @Operation(summary = "Utilização por recurso, carga horária alocada e conflitos evitados (período inclusivo)")
    public UtilizationReport utilization(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.utilization(from, to);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Últimas 100 notificações (simuladas ou enviadas ao webhook)")
    public List<NotificationView> notifications() {
        return notifications.findTop100ByOrderByIdDesc().stream().map(NotificationView::from).toList();
    }
}
