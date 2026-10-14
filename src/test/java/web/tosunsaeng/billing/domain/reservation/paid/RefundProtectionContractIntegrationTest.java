package web.tosunsaeng.billing.domain.reservation.paid;

import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.domain.reservation.application.*;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import static org.assertj.core.api.Assertions.*;

/** Characterizes the existing runtime; does NOT define the unresolved refund protection policy. */
class RefundProtectionContractIntegrationTest extends PaidReservationTestSupport {
    @Test void sameGroupReplacementRetainsTwoConfirmedNonTerminalSessions() {
        purchase();
        var firstCommand = command();
        var first = runtime().reserve(firstCommand);
        runtime().confirm(confirm(firstCommand, first));
        String group = first.responseSnapshot().getString("attemptGroupId");
        var restart = new RestartContext(group, "session", RestartContext.StartReason.RESTART_INCOMPLETE);
        var request = new ReserveRequest(USER, "second-session", "mock", null, null, null, restart);
        var secondCommand = new ReserveCommand(UUID.randomUUID().toString(), USER, "second-session", "mock", null, null, null,
                new ReservePayloadHasher().hash(request), restart);
        var second = runtime().reserve(secondCommand);
        assertThat(second.responseSnapshot().getString("reservationKind")).isEqualTo("REPLACEMENT");
        assertThat(second.responseSnapshot().getString("attemptGroupId")).isEqualTo(group);
        runtime().confirm(confirm(secondCommand, second));

        var sessions = db.getCollection("attempt_sessions").find(new Document("attemptGroupId", group))
                .sort(new Document("_id", 1)).into(new ArrayList<>());
        assertThat(sessions).hasSize(2).allSatisfy(row -> {
            assertThat(row.getString("state")).isEqualTo("ACTIVE");
            assertThat(row.getDate("confirmedAt")).isNotNull();
            assertThat(row.get("terminalAt")).isNull();
            assertThat(row.get("sourceSnapshot", Document.class).getString("purchaseId"))
                    .isEqualTo(first.source().getString("purchaseId"));
        });
        var previous = db.getCollection("attempt_sessions").find(new Document("_id", "session")).first();
        assertThat(previous.getBoolean("completionAllowed")).isTrue();
        assertThat(db.getCollection("attempt_groups").find(new Document("_id", group)).first().getString("activeSessionId"))
                .isEqualTo("second-session");
        // A single confirmedSessionId can name the latest Session, but cannot express the old completion permission.
        // Whether LC's durable accepted-work evidence independently preserves it requires joint clarification.
    }
}
