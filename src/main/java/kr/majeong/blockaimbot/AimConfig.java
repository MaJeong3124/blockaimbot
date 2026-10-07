package kr.majeong.blockaimbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;

public final class AimConfig {
    private static final Logger LOG = LoggerFactory.getLogger("blockaimbot");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockaimbot.json");
    public double aimSpeed = 180;
    public double maxDistance = 6;
    public double randomization = 0;
    public String targetBlock = "";

    public void sanitize() {
        aimSpeed = clamp(aimSpeed, 5, 720, 180);
        maxDistance = clamp(maxDistance, 1, 16, 6);
        randomization = clamp(randomization, 0, 50, 0);
        if (targetBlock == null) targetBlock = "";
    }

    private static double clamp(double n, double lo, double hi, double fallback) {
        return Double.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : fallback;
    }

    public static AimConfig load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                AimConfig c = GSON.fromJson(reader, AimConfig.class);
                if (c == null) throw new IOException("Empty configuration");
                c.sanitize();
                return c;
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot read configuration; using defaults", e);
                // Preserve the invalid file for diagnosis before the next save.
                try { Files.copy(FILE, FILE.resolveSibling("blockaimbot.json.invalid"), StandardCopyOption.REPLACE_EXISTING); }
                catch (IOException backupError) { LOG.warn("Cannot back up invalid configuration", backupError); }
            }
        }
        return new AimConfig();
    }

    public boolean save() {
        sanitize();
        Path temp = FILE.resolveSibling("blockaimbot.json.tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temp, GSON.toJson(this));
            try { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException e) {
            LOG.error("Cannot save configuration", e);
            return false;
        }
    }
}
