package jp.feato.gunvalhalla;

import io.papermc.paper.ServerBuildInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jp.feato.gunvalhalla.command.FirearmsDebugCommand;
import jp.feato.gunvalhalla.command.FirearmsCommand;
import jp.feato.gunvalhalla.config.RuntimeConfiguration;
import jp.feato.gunvalhalla.compatibility.BridgeCompatibility;
import jp.feato.gunvalhalla.compatibility.DatapackHandshake;
import jp.feato.gunvalhalla.integration.valhalla.ValhallaIntegration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

public final class FEATOGunValhallaBridge extends JavaPlugin implements Listener {
    private RuntimeConfiguration runtimeConfiguration;
    private BridgeCompatibility compatibility;
    private DatapackHandshake handshake;
    private ValhallaIntegration valhalla;
    private boolean registered;
    private boolean failed;
    private String status = "Waiting for initialization";

    @Override public void onEnable() {
        saveDefaultConfig();
        try (var input = getResource("bridge.properties")) {
            runtimeConfiguration = new RuntimeConfiguration(getDataFolder().toPath().resolve("config.yml"));
            valhalla = new ValhallaIntegration();
            var commands = new FirearmsCommand(runtimeConfiguration, new FirearmsDebugCommand(this, valhalla), getLogger());
            var command = Objects.requireNonNull(getCommand("firearms"));
            command.setExecutor(commands);
            command.setTabCompleter(commands);
            runtimeConfiguration.reload();
            compatibility = BridgeCompatibility.load(input);
            if (!getPluginMeta().getVersion().equals(compatibility.version())) throw new IllegalStateException("Plugin metadata version mismatch");
            ServerBuildInfo info = ServerBuildInfo.buildInfo();
            if (!info.brandId().equals(ServerBuildInfo.BRAND_PAPER_ID) || !info.minecraftVersionId().equals(compatibility.minecraftVersion()) ||
                    info.buildNumber().orElse(-1) != compatibility.paperBuild())
                throw new IllegalStateException("Requires exact Paper " + compatibility.minecraftVersion() + " build " + compatibility.paperBuild());
            Plugin dependency = getServer().getPluginManager().getPlugin("ValhallaMMO");
            if (dependency == null || !dependency.isEnabled() || !dependency.getPluginMeta().getVersion().equals(compatibility.valhallaVersion()))
                throw new IllegalStateException("Requires enabled ValhallaMMO " + compatibility.valhallaVersion());
            handshake = new DatapackHandshake(compatibility.marker());
            getServer().getPluginManager().registerEvents(this, this);
            getServer().getScheduler().runTaskTimer(this, this::checkCompatibility, 20, 20);
            getLogger().info("Bridge plugin=" + compatibility.version() + " protocol=" + compatibility.protocol() +
                    " targets: Gun Core=" + compatibility.gunCoreVersion() + " Modern Guns=" + compatibility.modernGunsVersion() +
                    " Valhalla=" + compatibility.valhallaVersion() + "; waiting for live datapack marker");
        } catch (Exception | LinkageError failure) { fail(failure); }
    }

    public void checkCompatibility() {
        if (failed || handshake == null) return;
        try {
            Objective objective = Objects.requireNonNull(getServer().getScoreboardManager()).getMainScoreboard().getObjective("fgv_bridge");
            Map<String, Integer> actual = null;
            Integer heartbeat = null;
            if (objective != null) {
                actual = new HashMap<>();
                for (String key : compatibility.marker().keySet()) {
                    Score score = objective.getScore(key);
                    if (score.isScoreSet()) actual.put(key, score.getScore());
                }
                Score score = objective.getScore("#heartbeat");
                if (score.isScoreSet()) heartbeat = score.getScore();
            }
            var state = handshake.sample(actual, heartbeat);
            status = handshake.reason();
            if (state == DatapackHandshake.State.FAILED) throw new IllegalStateException(status);
            if (state == DatapackHandshake.State.VERIFIED && !registered) {
                valhalla.register(this::isReady);
                registered = true;
                status = "FIREARMS registered; live verification pending";
                getLogger().info(status);
            }
            if (registered) valhalla.verifyIdentity();
        } catch (Exception | LinkageError failure) { fail(failure); }
    }

    private void fail(Throwable failure) {
        failed = true;
        status = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
        getLogger().log(java.util.logging.Level.SEVERE, "Bridge PoC stopped: " + status + ". No automatic retry; full restart required.", failure);
        getServer().getScheduler().cancelTasks(this);
        // Do not unregister through private APIs or erase player data. A partial registration
        // remains visible in Valhalla until full restart, with this skill non-levelable.
    }

    /** Immediate identity validation does not advance the timed heartbeat watchdog. */
    public void validateCurrentCompatibility() {
        if (!isReady()) throw new IllegalStateException(status);
        try {
            Objective objective = Objects.requireNonNull(getServer().getScoreboardManager())
                    .getMainScoreboard().getObjective("fgv_bridge");
            if (objective == null) throw new IllegalStateException("Datapack marker disappeared");
            for (var entry : compatibility.marker().entrySet()) {
                Score score = objective.getScore(entry.getKey());
                if (!score.isScoreSet() || score.getScore() != entry.getValue())
                    throw new IllegalStateException("Datapack marker changed: " + entry.getKey());
            }
            valhalla.verifyIdentity();
        } catch (Exception | LinkageError failure) {
            fail(failure);
            throw new IllegalStateException(status, failure);
        }
    }

    @EventHandler public void onLogin(PlayerLoginEvent event) {
        if (!registered && !failed) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "FIREARMS PoC initializing; retry shortly");
    }
    @Override public void onDisable() { failed = true; }
    public boolean isReady() { return isEnabled() && registered && !failed && handshake != null && handshake.state() == DatapackHandshake.State.VERIFIED; }
    public String status() { return status; }
    public boolean debugEnabled() { return runtimeConfiguration != null && runtimeConfiguration.debugEnabled(); }
}
