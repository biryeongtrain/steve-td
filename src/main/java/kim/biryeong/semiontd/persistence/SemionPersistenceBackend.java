package kim.biryeong.semiontd.persistence;

public interface SemionPersistenceBackend extends AutoCloseable {
    SemionPersistenceBackendType type();

    void initialize();

    @Override
    void close();
}
