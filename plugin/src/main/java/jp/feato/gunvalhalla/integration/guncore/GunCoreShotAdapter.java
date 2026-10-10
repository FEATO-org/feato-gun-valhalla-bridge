package jp.feato.gunvalhalla.integration.guncore;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import jp.feato.gunvalhalla.FEATOGunValhallaBridge;
import jp.feato.gunvalhalla.shot.ShotTracker;
import jp.feato.gunvalhalla.shot.ShotContext;
import jp.feato.gunvalhalla.shot.ShotContext.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.scoreboard.Objective;
import static jp.feato.gunvalhalla.integration.guncore.GunCoreContract.*;

/** Synchronous raycast scope plus explicitly unattributed native slowcast diagnostics. */
public final class GunCoreShotAdapter implements BasicCommand, Listener {
    private final FEATOGunValhallaBridge plugin;
    private final BooleanSupplier detailed;
    private final Properties weapons = new Properties();
    private final ShotTracker tracker = new ShotTracker(50);
    private final Map<UUID, Observation> observed = new HashMap<>();
    private final int session = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    private Scope scope;
    private boolean armed, stopped;
    private long rejected;
    private record Scope(long id, UUID shooter, int nativeSource, long tick) {}
    private record Observation(long firstTick, Integer range) {}

    public GunCoreShotAdapter(FEATOGunValhallaBridge plugin, BooleanSupplier detailed) throws IOException {
        this.plugin = plugin; this.detailed = detailed;
        try (var input = plugin.getResource("gun-core-weapons.properties")) {
            if (input == null) throw new IOException("Missing fixed weapon contract");
            weapons.load(input);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Startup-only recovery for records in chunks loaded before this listener registered.
        for (var world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) recoverRecord(entity);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    private Integer score(String objective, String entry) {
        Objective value = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objective);
        if (value == null || !value.getScore(entry).isScoreSet()) return null;
        return value.getScore(entry).getScore();
    }
    private void set(String entry, int value) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJECTIVE);
        if (objective != null) objective.getScore(entry).setScore(value);
    }
    private boolean contractPresent() {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        return List.of(OBJECTIVE, SOURCE, "gbg.temp", "gbg.raycast_distance").stream()
                .allMatch(name -> board.getObjective(name) != null) && Integer.valueOf(2).equals(score(OBJECTIVE, "#contract"));
    }
    private void tick() {
        tracker.debug(detailed.getAsBoolean());
        long now = Bukkit.getCurrentTick();
        for (ShotContext expired : tracker.expire(now)) log("COMPLETE id=" + expired.fired().id() + " result=EXPIRED");
        if (scope != null && scope.tick() != now) scope = null;
        set("#in_scope", scope == null ? 0 : 1);
        set("#observe", detailed.getAsBoolean() ? 1 : 0);
        if (stopped) return;
        if (!plugin.isReady()) { set("#ready", 0); armed = false; return; }
        if (!contractPresent()) { if (armed) stop("Runtime Gun Core contract disappeared"); return; }
        if (armed && (!Integer.valueOf(session).equals(score(OBJECTIVE, "#session")) ||
                !Integer.valueOf(1).equals(score(OBJECTIVE, "#ready")))) {
            stop("Session changed or datapack reloaded"); return;
        }
        if (!armed) {
            set("#session", session); set("#ready", 1); armed = true;
            plugin.getLogger().info("Raycast adapter armed; explicit debug adapt required; slowcast is observation only");
        }
        if (!detailed.getAsBoolean()) { observed.clear(); return; }
        // Only observed UUIDs; no world entity scan or player x shot scan.
        for (var entry : List.copyOf(observed.entrySet())) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || now - entry.getValue().firstTick() >= 200) { observed.remove(entry.getKey()); continue; }
            Integer range = score(RANGE, entity.getUniqueId().toString());
            if (!java.util.Objects.equals(range, entry.getValue().range())) {
                observe("STEP", entity);
                observed.put(entry.getKey(), new Observation(entry.getValue().firstTick(), range));
            }
        }
    }
    private void stop(String reason) {
        stopped = true; armed = false; scope = null; set("#ready", 0);
        tracker.close(); observed.clear();
        plugin.getLogger().warning("Shot adapter stopped: " + reason + "; restore adapted items; full restart required");
    }
    @Override public void execute(CommandSourceStack source, String[] args) {
        if (!Bukkit.isPrimaryThread()) { rejected++; return; }
        tracker.debug(detailed.getAsBoolean());
        Entity record = source.getExecutor();
        boolean trusted = record != null && TransportGuard.accepts(record instanceof Marker,
                record.getScoreboardTags().contains(RECORD_TAG), score(OBJECTIVE, record.getUniqueId().toString()), session);
        if (!trusted) { rejected++; return; }
        try {
            if (!armed || !plugin.isReady() || !contractPresent()) throw new IllegalStateException("Adapter not ready");
            long now = Bukkit.getCurrentTick();
            if (args.length == 8 && args[0].equals("fired")) {
                if (scope != null) throw new IllegalStateException("Nested or unfinished FIRED scope");
                UUID shooter = uuid(args, 1);
                Entity entity = Bukkit.getEntity(shooter);
                int type = Integer.parseInt(args[6]), nativeSource = Integer.parseInt(args[7]);
                if (!(entity instanceof Player) || !Integer.valueOf(nativeSource).equals(score(SOURCE, entity.getName())))
                    throw new IllegalArgumentException("Shooter snapshot does not match native source");
                if (!String.valueOf(type).equals(weapons.getProperty(args[5])) || (type != 1 && type != 3))
                    throw new IllegalArgumentException("Unsupported weapon / projectile snapshot");
                ShotContext shot = tracker.fire(shooter, args[5], type, nativeSource, now, point(source.getLocation()));
                scope = new Scope(shot.fired().id(), shooter, nativeSource, now);
                set("#in_scope", 1);
                set("#accepted", 1);
                log("FIRED id=" + scope.id() + " shooter=" + shooter + " weapon=" + args[5] + " projectile=" + type + " tick=" + now);
            } else if (args.length == 7 && args[0].equals("entity")) {
                UUID target = uuid(args, 1);
                int nativeSource = Integer.parseInt(args[6]);
                Headshot headshot = switch (args[5]) { case "1" -> Headshot.TRUE; case "0" -> Headshot.FALSE; default -> Headshot.UNKNOWN; };
                if (scope == null) {
                    log("OBSERVATION ENTITY_HIT target=" + target + " native_source=" + nativeSource +
                            " headshot=" + headshot + " location=" + point(source.getLocation()) + " attribution=UNKNOWN");
                } else {
                    if (scope.tick() != now) throw new IllegalStateException("Stale raycast scope");
                    tracker.hit(scope.id(), nativeSource, new Hit(State.ENTITY_HIT, target, point(source.getLocation()), headshot));
                    log("ENTITY_HIT id=" + scope.id() + " target=" + target + " headshot=" + headshot.name().toLowerCase(java.util.Locale.ROOT));
                }
            } else if (args.length == 1 && args[0].equals("block")) {
                if (scope == null) log("OBSERVATION BLOCK_HIT location=" + point(source.getLocation()) + " attribution=UNKNOWN");
                else {
                    if (scope.tick() != now) throw new IllegalStateException("Stale raycast scope");
                    tracker.hit(scope.id(), scope.nativeSource(), new Hit(State.BLOCK_HIT, null, point(source.getLocation()), Headshot.UNKNOWN));
                    log("BLOCK_HIT id=" + scope.id() + " location=" + point(source.getLocation()));
                }
            } else if (args.length == 5 && args[0].equals("complete")) {
                if (scope == null || scope.tick() != now) throw new IllegalStateException("Unknown COMPLETE scope");
                ShotContext done = tracker.complete(scope.id(), uuid(args, 1), now);
                log("COMPLETE id=" + scope.id() + " result=" + done.state() + " hits=" + done.hits().size());
                scope = null;
                set("#in_scope", 0);
            } else throw new IllegalArgumentException("Invalid internal record");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            rejected++; stop(failure.getMessage());
        } finally {
            Bukkit.getScoreboardManager().getMainScoreboard().resetScores(record.getUniqueId().toString());
            record.remove();
        }
    }
    public void adapt(CommandSender sender, Player player, boolean restore) {
        if (!restore && (!armed || !detailed.getAsBoolean())) {
            sender.sendMessage("Requires ready adapter and debug.enabled + debug.shot-context"); return;
        }
        set("#adapted", 0);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " run function " + (restore ? RESTORE : ADAPT));
        sender.sendMessage(restore ? "Bridge-adapted inventory items restored for " + player.getName() :
                "Adapter result for held item: " + (Integer.valueOf(1).equals(score(OBJECTIVE, "#adapted")) ? "adapted" : "refused (unsupported item / contract)"));
    }
    public void report(CommandSender sender, Player player) {
        sender.sendMessage("active=" + tracker.active(player.getUniqueId()).size() + " recent=" + tracker.recent(player.getUniqueId()).size() +
                " orphan=" + tracker.orphanCount() + " duplicate=" + tracker.duplicateCount() + " rejected=" + rejected + " armed=" + armed);
        for (ShotContext shot : tracker.recent(player.getUniqueId())) sender.sendMessage("id=" + shot.fired().id() + " weapon=" + shot.fired().weapon() +
                " result=" + shot.state() + " hits=" + shot.hits().size());
    }
    private void log(String message) { if (detailed.getAsBoolean()) plugin.getLogger().info("SHOT " + message + " session=" + session); }
    private void observe(String event, Entity entity) {
        log("OBSERVATION " + event + " marker=" + entity.getUniqueId() + " native_source=" + score(SOURCE, entity.getUniqueId().toString()) +
                " type=" + score(TYPE, entity.getUniqueId().toString()) + " range=" + score(RANGE, entity.getUniqueId().toString()) +
                " location=" + point(entity.getLocation()) + " attribution=UNKNOWN");
    }
    @EventHandler public void added(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (!armed || !detailed.getAsBoolean()) return;
        if (entity instanceof Marker && entity.getScoreboardTags().contains(SLOWCAST_TAG)) {
            observe("LAUNCH", entity);
            if (observed.size() < 128) observed.put(entity.getUniqueId(), new Observation(Bukkit.getCurrentTick(), null));
            else log("OBSERVATION LIMIT marker=" + entity.getUniqueId());
        } else if (entity.getScoreboardTags().contains(EXPLOSION_TAG))
            log("OBSERVATION EXPLOSION_CREATED entity=" + entity.getUniqueId() + " location=" + point(entity.getLocation()) + " attribution=UNKNOWN");
    }
    @EventHandler public void removed(EntityRemoveFromWorldEvent event) {
        if (observed.remove(event.getEntity().getUniqueId()) != null) observe("REMOVED", event.getEntity());
    }
    @EventHandler public void exploded(EntityExplodeEvent event) {
        if (armed && event.getEntity().getScoreboardTags().contains(EXPLOSION_TAG))
            log("OBSERVATION EXPLODED entity=" + event.getEntity().getUniqueId() + " location=" + point(event.getLocation()) + " attribution=UNKNOWN");
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {
        // Recover records saved by an interrupted command chain. Never touch native slowcast markers.
        for (Entity entity : event.getEntities()) recoverRecord(entity);
    }
    private void recoverRecord(Entity entity) {
        if (entity instanceof Marker && entity.getScoreboardTags().contains(RECORD_TAG)) {
            Bukkit.getScoreboardManager().getMainScoreboard().resetScores(entity.getUniqueId().toString());
            entity.remove();
        }
    }
    public void close() {
        set("#ready", 0); set("#session", 0); set("#in_scope", 0); set("#observe", 0);
        armed = false; stopped = true; scope = null;
        tracker.close(); observed.clear();
        // One shutdown pass, not a per-shot inventory scan. Offline items can be restored by the datapack function later.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as @a run function " + RESTORE);
    }
}
