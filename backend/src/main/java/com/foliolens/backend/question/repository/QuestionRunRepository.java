package com.foliolens.backend.question.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foliolens.backend.question.entity.QuestionRun;

public interface QuestionRunRepository extends JpaRepository<QuestionRun, UUID> {
}