package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("resources")
@CompoundIndex(name = "uk_resource_type_name", def = "{'type': 1, 'name': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
public class Resource {

    @Id
    private String id;

    private ResourceType type;
    private String name;
    private String location = "";
    private Integer capacity;
    private String competencies = "";

    /** Recursos restritos exigem aprovação de um responsável. */
    private boolean restricted;

    private boolean active = true;
}
