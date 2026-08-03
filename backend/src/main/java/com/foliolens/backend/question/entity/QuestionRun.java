package com.foliolens.backend.question.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.foliolens.backend.answer.AnswerResult;
import com.foliolens.backend.domain.BaseCreatedEntity;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.RequestChannel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import lombok.AccessLevel;

@Entity
@Getter
@Table(name = "question_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionRun extends BaseCreatedEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id; //질문,처리상태,답변,처리상태를 모두 총괄하는 하나의 트랜잭션이 갖는 ID

    @Column(name="external_question_id")
    String externalQuestionId; //평가 시스템이 보낸 Question ID

    @Column(name="question_text")
    String questionText; //사용자가 작성한 프롬프트

    @Column(name="channel")
    RequestChannel channel;

    @Column(name="status")
    QuestionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="query_plan_json")
    JsonNode queryPlanJson; //queryPlan을 Postegre에 JSONB 형태로 저장

    @OneToOne
    @MapsId
    @JoinColumn(name="answer")
    AnswerResult answer;

    @Column(name="error_code")
    ErrorCode errorCode;

    @Column(name="completed_at",updatable = false)
    Instant completedAt;

    @PrePersist
    @PreUpdate
    public void updateCompletionTimestamp() {
        if ("COMPLETED".equals(this.status) && this.completedAt == null) {
            this.completedAt = Instant.now();
        } else if (!"COMPLETED".equals(this.status)) {
            this.completedAt = null; // Clear if uncompleted
        }
    }
}
