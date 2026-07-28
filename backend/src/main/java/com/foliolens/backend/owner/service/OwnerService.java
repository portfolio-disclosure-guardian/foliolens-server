package com.foliolens.backend.owner.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.foliolens.backend.common.exception.CustomException;
import com.foliolens.backend.common.exception.ErrorCode;
import com.foliolens.backend.owner.entity.Owner;
import com.foliolens.backend.owner.repository.OwnerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OwnerService {
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;

    public Owner create(String password, String email) {
        Owner owner = Owner.builder()
                .password(passwordEncoder.encode(password))
                .email(email)
                .build();
        ownerRepository.save(owner);
        return owner;
    }

    public Owner getOwnerByEmail(String email) {
        return ownerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT, "사용자 이메일 입력값이 올바르지 않습니다."));
    }
}
