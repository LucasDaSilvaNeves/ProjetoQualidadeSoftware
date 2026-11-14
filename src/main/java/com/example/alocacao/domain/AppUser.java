package com.example.alocacao.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String passwordHash;
    private String fullName;
    private String email;
    private Role role;
    private boolean enabled = true;
}
