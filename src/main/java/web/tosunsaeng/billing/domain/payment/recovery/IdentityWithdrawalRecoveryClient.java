package web.tosunsaeng.billing.domain.payment.recovery;

import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;

/**
 * Fakeable boundary only: no production implementation or Spring bean yet.
 * Implementations perform authenticated, bounded HTTP outside Mongo transactions.
 * Caller retains the dispatch epoch/lease and must fence before applying any result.
 */
public interface IdentityWithdrawalRecoveryClient {
    SnapshotStatus createSnapshot(String streamId, String operationId);
    SnapshotStatus snapshotStatus(String snapshotId, String streamId);
    SnapshotPage snapshotPage(String snapshotId, String cursor);
    FeedPage feedPage(String streamId, long afterSequence, String scanCursor, Long expectedTarget);
}
