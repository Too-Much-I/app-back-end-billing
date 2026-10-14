package web.tosunsaeng.billing.domain.payment.provider;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Minimum RC lookup reference only; independent of transaction HMAC and webhook keys. */
public final class PurchaseReferenceCipher {
    public record Context(String environment, String project, String app, String transactionKey) {
        public Context {
            if (!Set.of("SANDBOX", "PRODUCTION").contains(environment) || transactionKey == null || !transactionKey.matches("[a-f0-9]{64}"))
                throw RevenueCatFailure.contract();
            RevenueCatPurchaseDecoder.text(project); RevenueCatPurchaseDecoder.text(app);
        }
        @Override public String toString() { return "ReferenceContext[REDACTED]"; }
    }
    public record Envelope(int revision, String keyId, String nonce, String ciphertext) {
        @Override public String toString() { return "ReferenceEnvelope[REDACTED]"; }
    }
    private final String keyId;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public PurchaseReferenceCipher(String keyId, byte[] key) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,64}") || key == null || key.length != 32) throw RevenueCatFailure.contract();
        this.keyId = keyId; this.key = new SecretKeySpec(key.clone(), "AES");
    }
    public Envelope encrypt(Context context, String reference) {
        RevenueCatPurchaseDecoder.text(reference); byte[] nonce = new byte[12]; random.nextBytes(nonce);
        return new Envelope(1, keyId, Base64.getEncoder().encodeToString(nonce), Base64.getEncoder().encodeToString(
                crypt(Cipher.ENCRYPT_MODE, context, nonce, reference.getBytes(StandardCharsets.UTF_8))));
    }
    public String decrypt(Context context, Envelope envelope) {
        try {
            if (envelope.revision() != 1 || !keyId.equals(envelope.keyId()) || envelope.ciphertext().length() > 1400 || envelope.nonce().length() != 16)
                throw RevenueCatFailure.contract();
            byte[] nonce = Base64.getDecoder().decode(envelope.nonce());
            byte[] ciphertext = Base64.getDecoder().decode(envelope.ciphertext());
            if (nonce.length != 12 || ciphertext.length < 17 || ciphertext.length > 1036) throw RevenueCatFailure.contract();
            String value = new String(crypt(Cipher.DECRYPT_MODE, context, nonce, ciphertext), StandardCharsets.UTF_8);
            RevenueCatPurchaseDecoder.text(value); return value;
        } catch (RuntimeException invalid) { throw RevenueCatFailure.contract(); }
    }
    private byte[] crypt(int mode, Context context, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            for (String part : List.of("rc-reference-r1", keyId, context.environment(), context.project(), context.app(), context.transactionKey())) {
                byte[] b = part.getBytes(StandardCharsets.UTF_8); cipher.updateAAD(ByteBuffer.allocate(4).putInt(b.length).array()); cipher.updateAAD(b);
            }
            return cipher.doFinal(input);
        } catch (Exception invalid) { throw RevenueCatFailure.contract(); }
    }
    @Override public String toString() { return "PurchaseReferenceCipher[REDACTED]"; }
}
