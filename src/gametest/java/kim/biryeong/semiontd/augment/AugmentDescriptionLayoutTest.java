package kim.biryeong.semiontd.augment;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AugmentDescriptionLayoutTest {
    @GameTest
    public void completeCatalogFitsTheUnscaledNinetyPixelSevenLineDescriptionArea(GameTestHelper context) throws Exception {
        var config = AugmentConfig.defaults();
        var rows = new ArrayList<Object>();
        var failures = new ArrayList<String>();
        for (var card : AugmentCatalog.definitions()) {
            String summary = AugmentService.offerSummary(card, config);
            var lines = TextUncenterer.splitLines(Component.literal(summary), 90, "ko_kr");
            var title = TextUncenterer.splitLines(Component.literal(card.displayName()), 90, "ko_kr");
            var row = new LinkedHashMap<String, Object>();
            row.put("id", card.id());
            row.put("title", card.displayName());
            row.put("template", card.description());
            row.put("parameters", config.parametersFor(card.id()));
            row.put("description", AugmentDescriptions.describe(card, config));
            row.put("summary", summary);
            row.put("lineCount", lines.size());
            row.put("lines", lines.stream().map(Component::getString).toList());
            row.put("titleLineCount", title.size());
            row.put("lineWidths", lines.stream().map(TextUncenterer::preciseWidth).toList());
            rows.add(row);
            if (lines.size() > 7 || lines.stream().anyMatch(line -> TextUncenterer.preciseWidth(line) > 90)
                    || title.size() > 2 || summary.contains("…")) failures.add(card.id() + ": " + lines.size());
        }
        Path output = Path.of(System.getProperty("semiontd.descriptionAudit", "augment-description-layout.json"));
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(rows));
        context.assertTrue(rows.size() == 181, "All catalog cards must be checked");
        context.assertTrue(failures.isEmpty(), String.join("\n", failures));
        context.succeed();
    }
}
