package es.uclm.esibuy.security.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import es.uclm.esibuy.security.model.RememberMeToken;

public interface RememberMeTokenRepository extends MongoRepository<RememberMeToken, String> {

    Optional<RememberMeToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
