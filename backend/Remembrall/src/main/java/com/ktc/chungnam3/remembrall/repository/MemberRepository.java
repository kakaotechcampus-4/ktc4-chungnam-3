package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findByAuthProviderAndProviderUserId(
            AuthProvider authProvider,
            String providerUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT member FROM Member member WHERE member.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") UUID memberId);


    @Modifying
    @Query(value = """
        INSERT INTO member (
            id,
            auth_provider,
            provider_user_id,
            created_at
        )
        VALUES (
            :id,
            :authProvider,
            :providerUserId,
            :createdAt
        )
        ON CONFLICT (auth_provider, provider_user_id)
        DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("authProvider") String authProvider,
            @Param("providerUserId") String providerUserId,
            @Param("createdAt") Instant createdAt
    );

}
