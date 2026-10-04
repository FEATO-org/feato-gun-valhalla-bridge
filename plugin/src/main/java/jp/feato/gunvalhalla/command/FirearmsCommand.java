package jp.feato.gunvalhalla.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import jp.feato.gunvalhalla.config.RuntimeConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.InvalidConfigurationException;

/** Route config reload separately from the existing PoC debug operations. */
public final class FirearmsCommand implements TabExecutor {
    private static final String RELOAD_PERMISSION = "feato.gunvalhalla.reload";
    private static final String DEBUG_PERMISSION = "feato.gunvalhalla.debug";
    private final RuntimeConfiguration configuration;
    private final TabExecutor debug;
    private final Logger logger;
    public FirearmsCommand(RuntimeConfiguration configuration, TabExecutor debug, Logger logger) {
        this.configuration = configuration; this.debug = debug; this.logger = logger;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            if (!permitted(sender, RELOAD_PERMISSION)) return true;
            if (args.length != 1) return false;
            try {
                var settings = configuration.reload();
                sender.sendMessage("FEATO Gun-Valhalla Bridge configuration reloaded. debug.enabled=" + settings.debugEnabled());
                logger.info("Bridge configuration reloaded by " + sender.getName() + "; debug.enabled=" + settings.debugEnabled());
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException failure) {
                // YAML parser messages may contain file contents; report only safe diagnostics.
                String reason = failure instanceof InvalidConfigurationException ? "Invalid YAML syntax" :
                        failure instanceof IOException ? "Cannot read config.yml" : failure.getMessage();
                sender.sendMessage("Configuration reload failed: " + reason);
                logger.warning("Bridge configuration reload failed for " + sender.getName() + ": " + reason);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            if (!permitted(sender, DEBUG_PERMISSION)) return true;
            if (!configuration.debugEnabled()) { sender.sendMessage("PoC debug is disabled in config.yml"); return true; }
            return debug.onCommand(sender, command, label, args);
        }
        return false;
    }
    private static boolean permitted(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage("Permission denied");
        return false;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        if (args.length == 1) {
            var choices = new ArrayList<String>();
            if (sender.hasPermission(RELOAD_PERMISSION)) choices.add("reload");
            if (sender.hasPermission(DEBUG_PERMISSION) && configuration.debugEnabled()) choices.add("debug");
            return matching(choices, args[0]);
        }
        if (args[0].equalsIgnoreCase("debug") && sender.hasPermission(DEBUG_PERMISSION) && configuration.debugEnabled())
            return matching(debug.onTabComplete(sender, command, alias, args), args[args.length - 1]);
        return List.of();
    }
    private static List<String> matching(List<String> choices, String prefix) {
        if (choices == null) return List.of();
        String lower = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
