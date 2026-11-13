package com.rallit.domain.user.repository;

import com.rallit.domain.user.entity.SocialAccount;
import com.rallit.domain.user.entity.User;
import com.rallit.domain.user.enums.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
	@Query("""
			select account.user from SocialAccount account
			where account.provider = :provider and account.providerUserId = :providerUserId
			""")
	Optional<User> findUserByProviderAndProviderUserId(
			@Param("provider") SocialProvider provider, @Param("providerUserId") String providerUserId);

	Optional<SocialAccount> findFirstByUserIdOrderByIdAsc(Long userId);

	@Modifying
	@Transactional
	@Query("""
			update SocialAccount account set account.displayName = :displayName
			where account.provider = :provider and account.providerUserId = :providerUserId
			and (account.displayName is null or account.displayName <> :displayName)
			""")
	void updateDisplayName(@Param("provider") SocialProvider provider,
			@Param("providerUserId") String providerUserId, @Param("displayName") String displayName);
}
