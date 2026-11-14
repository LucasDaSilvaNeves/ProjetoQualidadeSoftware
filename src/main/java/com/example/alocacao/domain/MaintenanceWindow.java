package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document("maintenance_windows")
@CompoundIndex(name = "idx_maintenance_resource_start", def = "{'resourceId': 1, 'startTime': 1}")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceWindow {

    @Id
    private String id;
    private String resourceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
}
