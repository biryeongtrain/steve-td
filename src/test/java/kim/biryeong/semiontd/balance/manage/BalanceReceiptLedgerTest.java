package kim.biryeong.semiontd.balance.manage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import kim.biryeong.semiontd.balance.manage.BalanceRevisionStore.Receipt;
import org.junit.jupiter.api.Test;

class BalanceReceiptLedgerTest {
    @Test
    void everyReplacementStateMatchesOrderedJournalAndScheduledSnapshot() {
        var ledger = new BalanceReceiptLedger();
        var journal = new LinkedHashMap<String, Receipt>();
        var random = new Random(2603);
        assertFalse(ledger.hasScheduled());
        for (int step = 0; step < 3000; step++) {
            String key = "request-" + random.nextInt(120);
            Receipt receipt = receipt(key, DeploymentState.values()[random.nextInt(DeploymentState.values().length)]);
            journal.put(key, receipt);
            ledger.put(key, receipt);
            var expected = journal.values().stream()
                    .filter(value -> value.deployment().state() == DeploymentState.SCHEDULED).toList();
            assertEquals(expected, ledger.scheduledSnapshot());
            assertEquals(!expected.isEmpty(), ledger.hasScheduled());
            assertEquals(List.copyOf(journal.values()), List.copyOf(ledger.values()));
            assertSame(receipt, ledger.get(key));
        }
    }

    @Test
    void completionCancellationRecoveryAndNewReservationKeepDetachedSnapshots() {
        var ledger = new BalanceReceiptLedger();
        for (int i = 0; i < 20000; i++) {
            ledger.put("history-" + i, receipt("history-" + i, DeploymentState.APPLIED));
        }
        assertFalse(ledger.hasScheduled());
        assertEquals(List.of(), ledger.scheduledSnapshot());
        ledger.put("a", receipt("a", DeploymentState.SCHEDULED));
        ledger.put("b", receipt("b", DeploymentState.SCHEDULED));
        var pending = ledger.scheduledSnapshot();
        var history = ledger.copy();
        ledger.put("a", receipt("a", DeploymentState.APPLYING));
        ledger.put("b", receipt("b", DeploymentState.CANCELLED));
        assertFalse(ledger.hasScheduled());
        ledger.put("a", receipt("a", DeploymentState.REQUIRES_REVIEW));
        ledger.put("c", receipt("c", DeploymentState.SCHEDULED));
        assertEquals(List.of("c"), ledger.scheduledSnapshot().stream().map(Receipt::idempotencyKey).toList());
        assertEquals(List.of("a", "b"), pending.stream().map(Receipt::idempotencyKey).toList());
        assertEquals(DeploymentState.SCHEDULED, history.get("a").deployment().state());
        assertThrows(UnsupportedOperationException.class, () -> ledger.values().clear());
    }

    private static Receipt receipt(String key, DeploymentState state) {
        var patch = new BalancePatch("base", ApplyMode.NEXT_MATCH, "test", null, List.of());
        var deployment = new BalanceDeployment(key, state, "base", null, ApplyMode.NEXT_MATCH,
                "test", "operator", "test", 1, null, "NEXT_MATCH", "PENDING", null, List.of());
        return new Receipt(key, "fingerprint", patch, "candidate", "hash", "scope", null, deployment);
    }
}
