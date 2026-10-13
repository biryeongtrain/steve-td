package kim.biryeong.semiontd.balance.manage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.DeploymentState;
import kim.biryeong.semiontd.balance.manage.BalanceRevisionStore.Receipt;

final class BalanceReceiptLedger {
    private final Map<String, Receipt> receipts = new LinkedHashMap<>();
    private int scheduledCount;

    void put(String key, Receipt receipt) {
        Receipt previous = receipts.put(key, receipt);
        if (scheduled(previous)) {
            scheduledCount--;
        }
        if (scheduled(receipt)) {
            scheduledCount++;
        }
    }

    Receipt get(String key) {
        return receipts.get(key);
    }

    Collection<Receipt> values() {
        return Collections.unmodifiableCollection(receipts.values());
    }

    Map<String, Receipt> copy() {
        return new LinkedHashMap<>(receipts);
    }

    boolean hasScheduled() {
        return scheduledCount > 0;
    }

    List<Receipt> scheduledSnapshot() {
        return scheduledCount == 0 ? List.of() : receipts.values().stream()
                .filter(BalanceReceiptLedger::scheduled).toList();
    }

    private static boolean scheduled(Receipt receipt) {
        return receipt != null && receipt.deployment().state() == DeploymentState.SCHEDULED;
    }
}
