package com.example.alocacao.repository;

import com.example.alocacao.domain.Resource;
import com.example.alocacao.domain.ResourceType;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResourceRepository extends MongoRepository<Resource, String> {
    boolean existsByTypeAndName(ResourceType type, String name);
}
