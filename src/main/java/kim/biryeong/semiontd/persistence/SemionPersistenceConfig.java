package kim.biryeong.semiontd.persistence;

import java.util.Objects;

public record SemionPersistenceConfig(
        SemionPersistenceBackendType backend,
        String sqlitePath,
        String mongodbUri,
        String mongodbDatabase,
        boolean externalDbRequired
) {
    public SemionPersistenceConfig {
        backend = backend == null ? SemionPersistenceBackendType.FILE : backend;
        sqlitePath = Objects.requireNonNullElse(sqlitePath, "semiontd.db");
        mongodbUri = Objects.requireNonNullElse(mongodbUri, "");
        mongodbDatabase = Objects.requireNonNullElse(mongodbDatabase, "semiontd");
    }

    public static SemionPersistenceConfig defaultConfig() {
        return new SemionPersistenceConfig(
                SemionPersistenceBackendType.FILE,
                "semiontd.db",
                "",
                "semiontd",
                false
        );
    }
}
