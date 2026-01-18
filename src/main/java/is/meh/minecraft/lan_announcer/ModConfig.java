package is.meh.minecraft.lan_announcer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lanannouncer.json");

    public List<String> extraAddresses = new ArrayList<>();

    public ModConfig() {
        // Default values
    }

    @SuppressWarnings("null")
    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = Files.readString(CONFIG_PATH);
                var config = GSON.fromJson(content, ModConfig.class);
                if (config != null) {
                    if (config.extraAddresses == null)
                        config.extraAddresses = new ArrayList<>();
                    return config;
                }
            } catch (IOException e) {
                LANAnnouncer.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            LANAnnouncer.LOGGER.error("Failed to save config", e);
        }
    }
}
