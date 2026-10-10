package jp.feato.gunvalhalla.command;

import java.util.List;
import jp.feato.gunvalhalla.FEATOGunValhallaBridge;
import jp.feato.gunvalhalla.integration.valhalla.ValhallaIntegration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class FirearmsDebugCommand implements TabExecutor {
    private static final String PERMISSION = "feato.gunvalhalla.debug";
    private final FEATOGunValhallaBridge plugin;
    private final ValhallaIntegration valhalla;
    public FirearmsDebugCommand(FEATOGunValhallaBridge plugin, ValhallaIntegration valhalla) {
        this.plugin = plugin; this.valhalla = valhalla;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { sender.sendMessage("Permission denied"); return true; }
        if (!plugin.debugEnabled()) { sender.sendMessage("PoC debug is disabled in config.yml"); return true; }
        if (!plugin.isReady()) { sender.sendMessage("FIREARMS is unavailable: " + plugin.status()); return true; }
        boolean profile = args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("profile");
        boolean exp = args.length == 4 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("exp");
        boolean shot = args.length == 3 && List.of("shots", "adapt", "restore").contains(args[1].toLowerCase(java.util.Locale.ROOT));
        if (!profile && !exp && !shot) return false;
        Player player = plugin.getServer().getPlayerExact(args[2]);
        if (player == null) { sender.sendMessage("Target must be an online player with an exact name"); return true; }
        try {
            // Re-check current marker before a mutation, as well as the periodic watchdog.
            plugin.validateCurrentCompatibility();
            if (!plugin.isReady()) throw new IllegalStateException(plugin.status());
            if (shot) {
                if (args[1].equalsIgnoreCase("shots")) plugin.shots().report(sender, player);
                else plugin.shots().adapt(sender, player, args[1].equalsIgnoreCase("restore"));
                return true;
            }
            String before = valhalla.profile(player);
            if (exp) {
                double amount = DebugInput.experience(args[3]);
                valhalla.addExperience(player, amount);
                sender.sendMessage("before: " + before);
                plugin.getLogger().info("PoC EXP issuer=" + sender.getName() + " target=" + player.getUniqueId() + " requested=" + amount);
            }
            sender.sendMessage(valhalla.profile(player));
            return true;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            sender.sendMessage("PoC operation refused: " + failure.getMessage());
            return true;
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || !plugin.debugEnabled()) return List.of();
        if (args.length == 1) return List.of("debug");
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return List.of("profile", "exp", "shots", "adapt", "restore");
        return List.of();
    }
}
