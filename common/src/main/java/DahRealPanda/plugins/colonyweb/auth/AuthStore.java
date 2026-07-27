package DahRealPanda.plugins.colonyweb.auth;

import DahRealPanda.plugins.colonyweb.ColonyWeb;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Disk persistence for dashboard accounts ({@code <data-dir>/auth.json}), so grants and
 * logged-in browsers survive a server restart.
 *
 * <p>Writes go to a temp file and are then moved into place, so a crash mid-write cannot
 * truncate the account file.</p>
 */
public final class AuthStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public AuthStore(Path dataDir) {
        this.file = dataDir.resolve("auth.json");
    }

    /** Load all accounts, keyed by lowercase UUID string. Returns empty on any failure. */
    public Map<String, WebUser> load() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, WebUser> users = GSON.fromJson(json,
                    new TypeToken<LinkedHashMap<String, WebUser>>() { }.getType());
            return users != null ? users : new LinkedHashMap<>();
        } catch (Exception e) {
            LOGGER.error("{} could not read {} — starting with no accounts", ColonyWeb.LOG, file, e);
            return new LinkedHashMap<>();
        }
    }

    /** Persist all accounts atomically. */
    public void save(Map<String, WebUser> users) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(users), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("{} could not write {}", ColonyWeb.LOG, file, e);
        }
    }
}
