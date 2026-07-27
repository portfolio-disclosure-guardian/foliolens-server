package com.foliolens.backend.domain.owners.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;

import com.foliolens.backend.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "owners")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Owner extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "owners_status")
    private OwnerStatus status;

    @CreatedDate
    @Column(name = "deleted_at",updatable = false)
    private OffsetDateTime deletedAt;

    @Builder
    public Owner(String password, String email){
        this.password = password;
        this.email = email;
        this.status = OwnerStatus.ACTIVE;
    }
}