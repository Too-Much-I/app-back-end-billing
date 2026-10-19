package web.tosunsaeng.billing.contract;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Isolated feasibility experiments, not Identity or Billing production repositories. */
@Testcontainers(disabledWithoutDocker = false)
class PaymentLifecycleMongoFeasibilityTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"))
            .withCommand("--replSet", "docker-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1");
    static final TransactionOptions TX = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY.withJournal(true)).build();
    private MongoClient client;
    private MongoDatabase db;

    @BeforeEach
    void prepare() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        db = client.getDatabase("c0_" + UUID.randomUUID().toString().replace("-", ""))
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        for (String name : List.of("counter", "journal", "users", "control", "evidence")) db.createCollection(name);
        db.getCollection("counter").insertOne(new Document("_id", "stream").append("sequence", 0L));
        db.getCollection("control").insertOne(new Document("_id", "consumer")
                .append("generation", "old").append("version", 0L));
    }

    @AfterEach
    void close() {
        if (client != null) {
            client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                    .append("mode", "off"));
            db.drop(); // Only this test's randomly named database in its disposable container.
            client.close();
        }
    }

    private long capture(ClientSession session, String user) {
        Document counter = db.getCollection("counter").findOneAndUpdate(session, eq("_id", "stream"),
                inc("sequence", 1L), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        long sequence = counter.getLong("sequence");
        db.getCollection("journal").insertOne(session, new Document("_id", sequence).append("user", user));
        db.getCollection("users").insertOne(session, new Document("_id", user).append("state", "WITHDRAWN"));
        return sequence;
    }

    @Test
    void counterJournalAndUserRollbackTogether() {
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            assertThat(capture(session, "synthetic-a")).isEqualTo(1);
            session.abortTransaction();
        }
        assertThat(db.getCollection("counter").find().first().getLong("sequence")).isZero();
        assertThat(db.getCollection("journal").countDocuments()).isZero();
        assertThat(db.getCollection("users").countDocuments()).isZero();
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            assertThat(capture(session, "synthetic-b")).isEqualTo(1);
            session.commitTransaction();
        }
        assertThat(db.getCollection("journal").countDocuments()).isEqualTo(1);
    }

    @Test
    void identicalAtClusterTimeKeepsUserPagesAndHStableAcrossLaterCommit() {
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            capture(session, "a");
            capture(session, "b");
            session.commitTransaction();
        }
        Document first = db.runCommand(new Document("find", "users").append("filter", eqDoc("_id", "a"))
                .append("readConcern", new Document("level", "snapshot")));
        Document cursor = first.get("cursor", Document.class);
        BsonTimestamp time = cursor.get("atClusterTime", BsonTimestamp.class);
        if (time == null) time = first.get("atClusterTime", BsonTimestamp.class);
        assertThat(time).as("Server selected snapshot timestamp").isNotNull();
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            capture(session, "c");
            session.commitTransaction();
        }
        Document concern = new Document("level", "snapshot").append("atClusterTime", time);
        Document laterPage = db.runCommand(new Document("find", "users")
                .append("filter", new Document("_id", new Document("$gt", "a")))
                .append("sort", new Document("_id", 1)).append("readConcern", concern));
        assertThat(batch(laterPage).stream().map(d -> d.getString("_id")).toList()).containsExactly("b");
        Document h = db.runCommand(new Document("find", "counter").append("readConcern", concern));
        assertThat(batch(h).getFirst().getLong("sequence")).isEqualTo(2);
        assertThat(db.getCollection("counter").find().first().getLong("sequence")).isEqualTo(3);
        assertThat(db.getCollection("journal").find(new Document("_id", new Document("$gt", 2L)))
                .into(new ArrayList<>())).hasSize(1);
    }

    @Test
    void oldSnapshotWriterCannotAdvanceEvidenceAfterGenerationChanges() {
        try (ClientSession old = client.startSession()) {
            old.startTransaction(TX);
            assertThat(db.getCollection("control").find(old).first().getString("generation")).isEqualTo("old");
            db.getCollection("control").updateOne(eq("_id", "consumer"),
                    new Document("$set", new Document("generation", "new")).append("$inc", new Document("version", 1L)));
            assertThatThrownBy(() -> db.getCollection("control").updateOne(old,
                    new Document("_id", "consumer").append("generation", "old"), inc("version", 1L)))
                    .isInstanceOf(MongoException.class);
            old.abortTransaction();
        }
        assertThat(db.getCollection("control").find().first().getString("generation")).isEqualTo("new");
        assertThat(db.getCollection("evidence").countDocuments()).isZero();
    }

    @Test
    void counterStaleSnapshotCannotCommitOutOfOrder() {
        try (ClientSession earlier = client.startSession(); ClientSession later = client.startSession()) {
            earlier.startTransaction(TX);
            db.getCollection("counter").find(earlier).first();
            later.startTransaction(TX);
            capture(later, "winner");
            later.commitTransaction();
            assertThatThrownBy(() -> capture(earlier, "loser")).isInstanceOf(MongoException.class);
            earlier.abortTransaction();
        }
        assertThat(db.getCollection("journal").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("counter").find().first().getLong("sequence")).isEqualTo(1);
    }

    @Test
    void injectedUnknownCommitRetriesSameTransactionWithoutAllocatingAnotherSequence() {
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            capture(session, "synthetic");
            failCommand("commitTransaction", new Document("errorCode", 91)
                    .append("errorLabels", List.of("UnknownTransactionCommitResult")));
            // The driver may itself retry the injected retryable commit once.
            try {
                session.commitTransaction();
            } catch (MongoException unknown) {
                assertThat(unknown.hasErrorLabel("UnknownTransactionCommitResult")).isTrue();
                session.commitTransaction();
            }
        }
        assertThat(db.getCollection("journal").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("counter").find().first().getLong("sequence")).isEqualTo(1);
    }

    @Test
    void injectedSnapshotTooOldIsAnErrorNotAnEmptySuccessfulPage() {
        failCommand("find", new Document("errorCode", 286));
        assertThatThrownBy(() -> db.runCommand(new Document("find", "users")
                .append("readConcern", new Document("level", "snapshot"))))
                .isInstanceOfSatisfying(MongoCommandException.class, e -> assertThat(e.getErrorCode()).isEqualTo(286));
    }

    @Test
    void bsonDateCannotBeUsedAsNanosecondCanonicalEvidence() {
        Instant original = Instant.parse("2026-10-07T00:00:00.123456789Z");
        db.getCollection("evidence").insertOne(new Document("_id", "precision")
                .append("date", Date.from(original)).append("canonical", original.toString()));
        Document stored = db.getCollection("evidence").find().first();
        assertThat(stored.getDate("date").toInstant()).isEqualTo(Instant.parse("2026-10-07T00:00:00.123Z"));
        assertThat(Instant.parse(stored.getString("canonical"))).isEqualTo(original);
    }

    private void failCommand(String command, Document options) {
        client.getDatabase("admin").runCommand(new Document("configureFailPoint", "failCommand")
                .append("mode", new Document("times", 1))
                .append("data", new Document("failCommands", List.of(command)).append("errorCode", options.get("errorCode"))
                        .append("errorLabels", options.getOrDefault("errorLabels", List.of()))));
    }

    @Test
    void foundationIndexManifestCanBeCreatedExactlyWithoutChangingCoreSchema() throws Exception {
        verifyIndexManifest("billing-indexes");
        var refs = db.getCollection("purchase_account_refs");
        refs.insertOne(new Document("_id", "active-a").append("environment", "SANDBOX")
                .append("accountId", "synthetic").append("active", true));
        refs.insertOne(new Document("_id", "inactive").append("environment", "SANDBOX")
                .append("accountId", "synthetic").append("active", false));
        assertThatThrownBy(() -> refs.insertOne(new Document("_id", "active-b").append("environment", "SANDBOX")
                .append("accountId", "synthetic").append("active", true))).isInstanceOf(MongoException.class);
    }

    @Test
    void identityIndexProposalAndInt64OrderingWorkInDisposableMongo() throws Exception {
        verifyIndexManifest("identity-indexes");
        var journal = db.getCollection("withdrawal_journal");
        for (long seq : new long[] {10L, Long.MAX_VALUE, 9L}) {
            journal.insertOne(new Document("streamId", "fake-stream").append("sequence", seq)
                    .append("eventId", "fake-event-" + seq));
        }
        var rows = journal.find().sort(new Document("sequence", 1)).into(new ArrayList<>());
        assertThat(rows.stream().map(row -> row.getLong("sequence")).toList())
                .containsExactly(9L, 10L, Long.MAX_VALUE);
        assertThatThrownBy(() -> journal.insertOne(new Document("streamId", "fake-stream")
                .append("sequence", 9L).append("eventId", "other"))).isInstanceOf(MongoException.class);
        var streams = db.getCollection("withdrawal_stream");
        streams.insertOne(new Document("environment", "TEST").append("streamId", "a").append("status", "ACTIVE"));
        streams.insertOne(new Document("environment", "TEST").append("streamId", "b").append("status", "CLOSED_RESTORE"));
        assertThatThrownBy(() -> streams.insertOne(new Document("environment", "TEST")
                .append("streamId", "c").append("status", "ACTIVE"))).isInstanceOf(MongoException.class);
    }

    private void verifyIndexManifest(String name) throws Exception {
        var manifest = PaymentLifecycleContractFixtureTest.fixture(name);
        for (var index : manifest.get("indexes")) {
            String collection = index.get("collection").asText();
            Document keys = Document.parse(index.get("keys").toString());
            IndexOptions options = new IndexOptions().name(index.get("name").asText())
                    .unique(index.get("unique").asBoolean());
            if (index.has("partialFilterExpression"))
                options.partialFilterExpression(Document.parse(index.get("partialFilterExpression").toString()));
            if (index.has("expireAfterSeconds"))
                options.expireAfter(index.get("expireAfterSeconds").asLong(), TimeUnit.SECONDS);
            db.getCollection(collection).createIndex(keys, options);
            db.getCollection(collection).createIndex(keys, options); // Rerun with the same contract.
            Document actual = db.getCollection(collection).listIndexes().into(new ArrayList<>()).stream()
                    .filter(row -> row.getString("name").equals(index.get("name").asText())).findFirst().orElseThrow();
            assertThat(new ArrayList<>(actual.get("key", Document.class).keySet()))
                    .containsExactlyElementsOf(keys.keySet());
            assertThat(actual.get("key", Document.class)).isEqualTo(keys);
            assertThat(actual.getBoolean("unique", false)).isEqualTo(index.get("unique").asBoolean());
            assertThat(actual.get("partialFilterExpression"))
                    .isEqualTo(index.has("partialFilterExpression")
                            ? Document.parse(index.get("partialFilterExpression").toString()) : null);
            if (index.has("expireAfterSeconds"))
                assertThat(((Number) actual.get("expireAfterSeconds")).longValue()).isEqualTo(0L);
            else assertThat(actual).doesNotContainKey("expireAfterSeconds");
        }
    }

    @Test
    void accountRetryUpdatesSeparateActivityWithoutRotatingReference() {
        db.getCollection("users").insertOne(new Document("_id", "account").append("activeRefId", "stable")
                .append("version", 1L));
        db.getCollection("evidence").insertOne(new Document("_id", "cursor").append("lastActivityAt", 1L));
        for (long tick : List.of(2L, 3L)) {
            try (ClientSession session = client.startSession()) {
                session.startTransaction(TX);
                db.getCollection("control").updateOne(session,
                        new Document("_id", "consumer").append("generation", "old"), inc("version", 1L));
                assertThat(db.getCollection("users").find(session).first().getString("activeRefId")).isEqualTo("stable");
                db.getCollection("evidence").updateOne(session, eq("_id", "cursor"),
                        new Document("$max", new Document("lastActivityAt", tick)));
                session.commitTransaction();
            }
        }
        assertThat(db.getCollection("users").find().first().getLong("version")).isEqualTo(1L);
        assertThat(db.getCollection("evidence").find().first().getLong("lastActivityAt")).isEqualTo(3L);
        assertThat(db.getCollection("control").find().first().getLong("version")).isEqualTo(2L);
    }

    @Test
    void majorityJournalSurvivesSingleMemberStepdownAndReelection() throws Exception {
        try (ClientSession session = client.startSession()) {
            session.startTransaction(TX);
            capture(session, "before-stepdown");
            session.commitTransaction();
        }
        try {
            client.getDatabase("admin").runCommand(new Document("replSetStepDown", 1).append("force", true));
        } catch (MongoException expectedDisconnect) {
            // Primary may close its connection while stepping down; verify state after reelection below.
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        boolean primary = false;
        while (System.nanoTime() < deadline) {
            try {
                primary = client.getDatabase("admin").runCommand(new Document("hello", 1))
                        .getBoolean("isWritablePrimary", false);
                if (primary) break;
            } catch (MongoException electionInProgress) { }
            Thread.sleep(100);
        }
        assertThat(primary).isTrue();
        assertThat(db.getCollection("journal").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("counter").find().first().getLong("sequence")).isEqualTo(1);
    }

    private static Document eqDoc(String key, Object value) { return new Document(key, value); }

    private static List<Document> batch(Document response) {
        return response.get("cursor", Document.class).getList("firstBatch", Document.class);
    }
}
