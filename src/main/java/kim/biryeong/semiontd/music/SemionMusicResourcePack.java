package kim.biryeong.semiontd.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.nio.file.Files;
import kim.biryeong.semiontd.SemionTd;
import org.slf4j.Logger;

public final class SemionMusicResourcePack {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SOUNDS_JSON_PATH = "assets/" + SemionTd.MOD_ID + "/sounds.json";

    private SemionMusicResourcePack() {
    }

    public static void register(SemionMusicLibrary library, Logger logger) {
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> addToResourcePack(library, builder, logger));
    }

    public static void addToResourcePack(SemionMusicLibrary library, ResourcePackBuilder builder, Logger logger) {
        if (library.isEmpty()) {
            return;
        }

        JsonObject sounds = readExistingSounds(builder);
        for (SemionMusicTrack track : library.tracks()) {
            try {
                builder.addData(soundAssetPath(track), Files.readAllBytes(track.source()));
            } catch (IOException exception) {
                logger.warn("Failed to add Semion TD music track {} to generated resource pack.", track.source(), exception);
                continue;
            }
            sounds.add(track.eventId().getPath(), soundEntry(track));
        }
        builder.addStringData(SOUNDS_JSON_PATH, GSON.toJson(sounds));
    }

    private static JsonObject readExistingSounds(ResourcePackBuilder builder) {
        String existing = builder.getStringDataOrSource(SOUNDS_JSON_PATH);
        if (existing == null || existing.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(existing).getAsJsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static JsonObject soundEntry(SemionMusicTrack track) {
        JsonObject entry = new JsonObject();
        entry.addProperty("replace", false);
        JsonArray sounds = new JsonArray();
        JsonObject sound = new JsonObject();
        sound.addProperty("name", track.soundFileId().toString());
        sound.addProperty("stream", true);
        sound.addProperty("preload", false);
        sound.addProperty("volume", 1.0F);
        sound.addProperty("pitch", 1.0F);
        sounds.add(sound);
        entry.add("sounds", sounds);
        return entry;
    }

    private static String soundAssetPath(SemionMusicTrack track) {
        return "assets/" + SemionTd.MOD_ID + "/sounds/music/" + track.id() + ".ogg";
    }
}
