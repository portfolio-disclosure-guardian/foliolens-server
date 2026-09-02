package com.foliolens.backend.question.entity;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.foliolens.backend.domain.BaseCreatedEntity;
import com.foliolens.backend.global.exception.ErrorCode;
import com.foliolens.backend.question.RequestChannel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Builder;

@Entity
@Getter
@Table(name = "question_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionRun extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; //질문,처리상태,답변,처리상태를 모두 총괄하는 하나의 트랜잭션이 갖는 ID

    @Column(name = "external_question_id", nullable = false)
    private String externalQuestionId; //평가 시스템이 보낸 Question ID

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "question_text", nullable = false)
    private String questionText; //사용자가 작성한 프롬프트

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private RequestChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuestionRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_plan_json", columnDefinition = "jsonb")
    private JsonNode queryPlanJson; //queryPlan을 Postegre에 JSONB 형태로 저장

    @Column(name = "answer_text")
    private String answerText; //해당 필드값은 계산,검증,금지 표현 필터링 등의 전처리를 거친 후의 HCX 답변 String

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private ErrorCode errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    public QuestionRun(
            String externalQuestionId,
            String requestId,
            String questionText,
            RequestChannel channel) {
        this.externalQuestionId = externalQuestionId;
        this.requestId = requestId;
        this.questionText = questionText;
        this.channel = channel;
        this.status = QuestionRunStatus.PENDING;
    }

    public void start() {
        requireStatus(QuestionRunStatus.PENDING);
        this.status = QuestionRunStatus.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void complete(String answerText) {
        requireStatus(QuestionRunStatus.PROCESSING);
        this.status = QuestionRunStatus.COMPLETED;
        this.answerText = answerText;
        this.errorCode = null;
        this.completedAt = Instant.now();
    }

    public void fail(ErrorCode errorCode) {
        if (this.status == QuestionRunStatus.COMPLETED || this.status == QuestionRunStatus.FAILED) {
            throw new IllegalStateException("종료된 질문 실행의 상태를 변경할 수 없습니다.");
        }
        this.status = QuestionRunStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = Instant.now();
    }

    public Long getProcessingTimeMillis() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }

    private void requireStatus(QuestionRunStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "질문 실행 상태가 올바르지 않습니다. expected=" + expected + ", actual=" + this.status);
        }
    }
}
