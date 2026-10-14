package web.tosunsaeng.billing.domain.payment.worker;

import java.io.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PaymentReviewOperatorCliTest {
    static final String ID = "11111111-1111-4111-8111-111111111111";
    static String[] account(String... extras) {
        var args = new ArrayList<>(List.of("--kind=account", "--action=resume", "--target=" + ID, "--operation-id=" + ID,
                "--version=1", "--generation=0", "--epoch=" + ID, "--reason=PROVIDER_RECOVERED", "--failure=RETRY_EXHAUSTED", "--selected=none"));
        args.addAll(List.of(extras)); return args.toArray(String[]::new);
    }
    @Test void defaultIsInspectAndResumeRequiresExplicitApply() {
        assertThat(PaymentReviewOperatorCli.parse(new String[]{"--kind=account", "--target=" + ID})).doesNotContainKey("apply");
        assertThat(PaymentReviewOperatorCli.parse(account())).doesNotContainKey("apply");
        assertThat(PaymentReviewOperatorCli.parse(account("--apply", "--writers-isolated"))).containsEntry("apply", "true");
    }
    @ParameterizedTest @ValueSource(strings={"--unknown=x", "--operator=admin", "--apply=yes", "--kind=auth", "--version=2", "--secret=hidden"})
    void rejectsUnknownDuplicateAndBooleanCoercion(String extra) {
        assertThatThrownBy(() -> PaymentReviewOperatorCli.parse(account(extra))).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings={"-1", "+1", "01", "1.0", "1e1", "1\n", "9223372036854775808"})
    void rejectsNoncanonicalNumbers(String value) {
        assertThatThrownBy(() -> PaymentReviewOperatorCli.number(value)).isInstanceOf(RuntimeException.class);
    }
    @Test void acceptsLongBoundaryParserButRefusesUnincrementableCommandVersion() {
        assertThat(PaymentReviewOperatorCli.number("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
        var args = account(); args[4] = "--version=9223372036854775807";
        assertThatThrownBy(() -> PaymentReviewOperatorCli.parse(args)).isInstanceOf(RuntimeException.class);
    }
    @Test void duplicateSelectionAndCrossActionArgumentsAreRejected() {
        var args = account(); args[9] = "--selected=" + ID + ":1," + ID + ":2";
        assertThatThrownBy(() -> PaymentReviewOperatorCli.parse(args)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PaymentReviewOperatorCli.parse(new String[]{"--kind=auth", "--action=inspect", "--target=project", "--apply"})).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PaymentReviewOperatorCli.parse(new String[]{"--kind=auth", "--action=resume", "--target=project",
                "--operation-id=" + ID, "--version=1", "--reason=PROVIDER_RECOVERED"})).isInstanceOf(RuntimeException.class);
    }
    @Test void authenticatedPrincipalIsRequiredAndNeverSelfAsserted() {
        var status = new Document("authInfo", new Document("authenticatedUsers", List.of()));
        assertThatThrownBy(() -> PaymentReviewOperatorCli.authenticatedActor(status)).isInstanceOf(RuntimeException.class);
        status.get("authInfo", Document.class).put("authenticatedUsers", List.of(new Document("user", "operator-one").append("db", "admin")));
        String digest = PaymentReviewOperatorCli.authenticatedActor(status);
        assertThat(digest).matches("[0-9a-f]{64}").doesNotContain("operator-one");
        status.get("authInfo", Document.class).put("authenticatedUsers", List.of(new Document("user", "operator-two").append("db", "admin")));
        assertThat(PaymentReviewOperatorCli.authenticatedActor(status)).isNotEqualTo(digest);
    }
    @Test void missingIsolationRefusesBeforeOpeningDatabaseAndNeverEchoesSecret() {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int status = PaymentReviewOperatorCli.run(account("--apply"), Map.of("BILLING_REVIEW_OPERATOR_MONGO_URI", "secret-do-not-print"), new PrintStream(out), new PrintStream(err));
        assertThat(status).isEqualTo(1); assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).contains("check result").doesNotContain("secret-do-not-print", ID);
    }
}
