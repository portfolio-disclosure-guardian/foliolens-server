package com.foliolens.backend.question.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.foliolens.backend.domain.BaseCreatedEntity;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.RequestChannel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Builder;

@Entity
@Getter
@Table(name = "question_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionRun extends BaseCreatedEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id; //질문,처리상태,답변,처리상태를 모두 총괄하는 하나의 트랜잭션이 갖는 ID

    @Column(name="external_question_id",nullable = false)
    String externalQuestionId; //평가 시스템이 보낸 Question ID

    @Column(name="question_text",nullable = false)
    String questionText; //사용자가 작성한 프롬프트

    @Column(name="channel",nullable = false)
    RequestChannel channel;

    @Column(name="status",nullable = false)
    QuestionRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="query_plan_json")
    JsonNode queryPlanJson; //queryPlan을 Postegre에 JSONB 형태로 저장

    @JoinColumn(name="answer_text")
    String answerText; //해당 필드값은 계산,검증,금지 표현 필터링 등의 전처리를 거친 후의 HCX 답변 String

    @Column(name="error_code")
    ErrorCode errorCode;

    @Column(name="completed_at",updatable = false)
    Instant completedAt;

    @PrePersist
    @PreUpdate
    public void updateCompletionTimestamp() {
        if (this.status==QuestionRunStatus.COMPLETED && this.completedAt == null) {
            this.completedAt = Instant.now();
        } else if (this.status!=QuestionRunStatus.COMPLETED) {
            this.completedAt = null; // Clear if uncompleted
        }
    }

    @Builder
    public QuestionRun(String externalQuestionId, String questionText){
        this.externalQuestionId=externalQuestionId;
        this.questionText=questionText;
        this.channel=RequestChannel.EVALUATION;
        this.status=QuestionRunStatus.PENDING;
    }
}
