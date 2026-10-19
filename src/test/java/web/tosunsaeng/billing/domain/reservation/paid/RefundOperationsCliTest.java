package web.tosunsaeng.billing.domain.reservation.paid;

import java.io.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefundOperationsCliTest {
    static final String ID = "12345678-1234-4234-8234-123456789abc";
    @Test void acceptsReadActions() {
        for (String action : new String[]{"purchase-show", "outbox-show", "case-show"})
            assertThat(RefundOperationsCli.parse(new String[]{"--action=" + action, "--target=" + ID})).containsEntry("target", ID);
        assertThat(RefundOperationsCli.parse(new String[]{"--action=case-list"})).containsEntry("action", "case-list");
        assertThat(RefundOperationsCli.parse(new String[]{"--action=case-list", "--after=" + ID})).containsEntry("after", ID);
    }
    @Test void caseReadsRejectMutationAndInvalidPagination() {
        for (String[] args : new String[][]{
                {"--action=case-list", "--after="}, {"--action=case-list", "--after=" + ID + "\n"},
                {"--action=case-list", "--target=" + ID}, {"--action=case-list", "--apply=true"},
                {"--action=case-list", "--limit=1000"}, {"--action=case-show", "--target=" + ID, "--amount=100"}})
            assertThatThrownBy(() -> RefundOperationsCli.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsWritesDuplicateUnknownAndNoncanonicalInput() {
        for (String[] args : new String[][]{
                {"--action=resume", "--target=" + ID}, {"--action=purchase-show", "--apply=true"},
                {"--action=purchase-show", "--action=outbox-show"},
                {"--action=outbox-show", "--target=" + ID.toUpperCase()},
                {"--action=outbox-show", "--target=" + ID + "\n"},
                {"--action=outbox-show", "--target=" + ID, "--apply=true"}})
            assertThatThrownBy(() -> RefundOperationsCli.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidCommandDoesNotConnectOrLeakInput() {
        var output = new ByteArrayOutputStream(); var errors = new ByteArrayOutputStream();
        assertThat(RefundOperationsCli.run(new String[]{"--action=resume", "--target=secret"},
                Map.of("BILLING_REFUND_OPERATOR_MONGO_URI", "secret"), new PrintStream(output), new PrintStream(errors))).isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).doesNotContain("secret", "Exception");
    }
    @Test void applyRequiresIsolationAndCanonicalCountersBeforeConnecting() {
        var args = new java.util.ArrayList<>(java.util.List.of("--action=outbox-resume", "--target=" + ID,
                "--operation-id=" + ID, "--version=1", "--generation=1", "--epoch=" + ID, "--reason=CREDENTIALS_FIXED"));
        assertThat(RefundOperationsCli.parse(args.toArray(String[]::new))).doesNotContainKey("apply");
        args.add("--apply=true");
        assertThatThrownBy(() -> RefundOperationsCli.parse(args.toArray(String[]::new))).isInstanceOf(IllegalArgumentException.class);
        args.add("--writers-isolated=true");
        assertThat(RefundOperationsCli.parse(args.toArray(String[]::new))).containsEntry("apply", "true");
        args.set(3, "--version=01");
        assertThatThrownBy(() -> RefundOperationsCli.parse(args.toArray(String[]::new))).isInstanceOf(IllegalArgumentException.class);
    }
}
