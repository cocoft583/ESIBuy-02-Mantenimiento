package es.uclm.esibuy.security.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import com.mongodb.client.result.UpdateResult;

import es.uclm.esibuy.users.model.User;

class LegacyBackupCodesCleanupTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final LegacyBackupCodesCleanup cleanup = new LegacyBackupCodesCleanup(mongoTemplate);

    @Test
    void removesPlainTextBackupCodesFromEveryUser() throws Exception {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(User.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));

        cleanup.run(new DefaultApplicationArguments());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(User.class));

        assertEquals(
                new Document("backupCodes", new Document("$exists", true)),
                query.getValue().getQueryObject());
        assertEquals(
                new Document("$unset", new Document("backupCodes", 1)),
                update.getValue().getUpdateObject());
    }
}
