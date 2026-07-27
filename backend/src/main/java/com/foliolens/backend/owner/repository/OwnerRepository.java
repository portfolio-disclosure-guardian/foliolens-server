package com.foliolens.backend.owner.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.foliolens.backend.owner.entity.Owner;


public interface OwnerRepository extends JpaRepository<Owner, UUID>{
    Optional<Owner> findByEmail(String email);
}