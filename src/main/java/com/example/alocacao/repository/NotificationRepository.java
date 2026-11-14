package com.example.alocacao.repository;

import com.example.alocacao.domain.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findTop100ByOrderByIdDesc();
}
