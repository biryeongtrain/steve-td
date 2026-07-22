package kim.biryeong.semiontd.config;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class PackagedConfigDefaults {
    private static final Gson GSON = new Gson();

    private PackagedConfigDefaults() {
    }

    public static <T> T load(String fileName, Class<T> type, T fallback) {
        String resourcePath = "/semiontd/" + fileName;
        try (InputStream input = PackagedConfigDefaults.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                return fallback;
            }
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                T packaged = GSON.fromJson(reader, type);
                return packaged == null ? fallback : packaged;
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load packaged config defaults: " + fileName, exception);
        }
    }
}
