package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.BalanceDeployment;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.BalancePatch;

/** One atomic index is both the active pointer and the durable request journal. */
public class BalanceRevisionStore {
    private final Path directory;

    public BalanceRevisionStore(Path directory) {this.directory = directory;}

    public record Receipt(String idempotencyKey, String fingerprint, BalancePatch patch, String candidateRevision,
                          String validationHash, String scope, String targetGameId, BalanceDeployment deployment) {}

    public record Index(int schemaVersion, String activeRevision, List<Receipt> receipts, String writeBlocked) {
        public Index {receipts = List.copyOf(receipts);}
    }

    public Index loadOrCreate(BalanceBundle initial) throws IOException {
        Files.createDirectories(directory.resolve("revisions"));
        Path index = directory.resolve("index.json");
        if (!Files.exists(index)) {
            // Existing revision files without their pointer require operator recovery, never default import.
            try (var revisions = Files.list(directory.resolve("revisions"))) {
                if (revisions.findAny().isPresent()) {throw new IOException("Managed balance pointer is missing.");}
            }
            saveRevision(initial);
            Index created = new Index(1, initial.revision(), List.of(), null);
            saveIndex(created);
            return created;
        }
        try {
            Index loaded = BalanceBundle.GSON.fromJson(Files.readString(index), Index.class);
            if (loaded == null || loaded.schemaVersion() != 1 || loaded.activeRevision() == null) {
                throw new IOException("Unsupported balance store format.");
            }
            readRevision(loaded.activeRevision());
            java.util.HashSet<String> keys = new java.util.HashSet<>();
            java.util.HashSet<String> requests = new java.util.HashSet<>();
            String lastApplied = null;
            for (Receipt receipt : loaded.receipts()) {
                if (receipt == null || receipt.deployment() == null || receipt.patch() == null
                        || !keys.add(receipt.idempotencyKey()) || !requests.add(receipt.deployment().requestId())) {
                    throw new IOException("Invalid balance request journal.");
                }
                BalanceDeployment deployment = receipt.deployment();
                if (deployment.state() == null || deployment.applyMode() == null || deployment.actor() == null
                        || deployment.source() == null || receipt.scope() == null || receipt.fingerprint() == null
                        || !receipt.fingerprint().matches("[a-f0-9]{64}") || receipt.validationHash() == null
                        || !receipt.validationHash().matches("[a-f0-9]{64}")
                        || !java.util.Objects.equals(receipt.idempotencyKey(), deployment.requestId())
                        || !java.util.Objects.equals(receipt.patch().baseRevision(), deployment.previousRevision())) {
                    throw new IOException("Invalid balance request metadata.");
                }
                readRevision(receipt.candidateRevision());
                readRevision(deployment.previousRevision());
                if (deployment.state() == kim.biryeong.semiontd.balance.manage.BalanceDtos.DeploymentState.APPLIED) {
                    if (!receipt.candidateRevision().equals(deployment.effectiveRevision()) || deployment.appliedAt() == null) {
                        throw new IOException("Applied request has no matching effective revision.");
                    }
                    lastApplied = deployment.effectiveRevision();
                }
            }
            if (lastApplied != null && !lastApplied.equals(loaded.activeRevision())) {
                throw new IOException("Active pointer and applied request journal disagree.");
            }
            return loaded;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid balance store.", exception);
        }
    }

    public BalanceBundle readRevision(String revision) throws IOException {
        if (revision == null || !revision.matches("[a-f0-9]{64}")) {throw new IOException("Invalid revision identifier.");}
        try {
            BalanceBundle bundle = BalanceBundle.fromJson(JsonParser.parseString(Files.readString(
                    directory.resolve("revisions").resolve(revision + ".json"))).getAsJsonObject());
            if (!bundle.revision().equals(revision)) {throw new IOException("Balance revision checksum mismatch.");}
            return bundle;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid balance revision.", exception);
        }
    }

    public void saveRevision(BalanceBundle bundle) throws IOException {
        Path target = directory.resolve("revisions").resolve(bundle.revision() + ".json");
        if (Files.exists(target)) {readRevision(bundle.revision()); return;}
        writeAtomic(target, BalanceBundle.canonical(bundle.toJson()));
    }

    public void saveIndex(Index index) throws IOException {
        writeAtomic(directory.resolve("index.json"), BalanceBundle.GSON.toJson(index));
    }

    private static void writeAtomic(Path target, String contents) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".balance-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(contents);
                while (buffer.hasRemaining()) {channel.write(buffer);}
                channel.force(true);
            }
            // A non-atomic fallback would make the active pointer unsafe after a crash.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) {directory.force(true);}
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
