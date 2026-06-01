package kim.biryeong.semiontd.persistence;

import java.nio.file.Path;
import java.util.Objects;

public final class FilePersistenceBackend implements SemionPersistenceBackend {
    private final Path configDir;

    public FilePersistenceBackend(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
    }

    public Path configDir() {
        return configDir;
    }

    @Override
    public SemionPersistenceBackendType type() {
        return SemionPersistenceBackendType.FILE;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void close() {
    }
}
