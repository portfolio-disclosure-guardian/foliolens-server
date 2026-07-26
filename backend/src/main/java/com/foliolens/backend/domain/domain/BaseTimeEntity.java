package com.foliolens.backend.domain.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
// 다른 엔티티들에서 공통으로 쓸 변수들임(생성시간, 수정시간) 
// 걍 extends하면 자동으로 해당 엔티티 객체에 생성/적용됨.
public abstract class BaseTimeEntity { 
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @CreatedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    //TODO: extracted_at, completed_at, revoked_at
}
