package com.foliolens.backend.domain.domain.owners.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foliolens.backend.domain.domain.owners.entity.Owner;

public interface OwnerRepository extends JpaRepository<Owner, UUID>{

}
