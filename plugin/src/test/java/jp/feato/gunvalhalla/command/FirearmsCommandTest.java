package jp.feato.gunvalhalla.command;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jp.feato.gunvalhalla.config.RuntimeConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real router and file loader; debug execution is a boundary spy, not a Valhalla runtime. */
class FirearmsCommandTest {
    @TempDir Path directory;
    private Path file;
    private String defaults;
    private RuntimeConfiguration configuration;
    private FirearmsCommand command;
    private final List<String> messages = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final List<List<String>> debugCalls = new ArrayList<>();
    private final Command bukkitCommand = new Command("firearms") {
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
    };
    @BeforeEach void prepare() throws Exception {
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"))) {
            defaults = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        file = directory.resolve("config.yml"); Files.writeString(file, defaults);
        configuration = new RuntimeConfiguration(file); configuration.reload();
        Logger logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logs.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        TabExecutor debug = new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                debugCalls.add(List.of(args)); return true;
            }
            @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
                return args.length == 2 ? List.of("profile", "exp") : List.of();
            }
        };
        command = new FirearmsCommand(configuration, debug, logger);
    }
    private CommandSender sender(String... permissions) {
        Set<String> grants = Set.of(permissions);
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> grants.contains(args[0]);
                    case "sendMessage" -> { messages.add((String) args[0]); yield null; }
                    case "getName" -> "TestConsole";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    private boolean run(CommandSender sender, String... args) {
        return command.onCommand(sender, bukkitCommand, "firearms", args);
    }
    private List<String> tab(CommandSender sender, String... args) {
        return command.onTabComplete(sender, bukkitCommand, "firearms", args);
    }
    @Test void reloadWorksWithOnlyReloadPermissionWhenDebugIsOffAndEnablesDebugImmediately() throws Exception {
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true"));
        assertFalse(configuration.debugEnabled());
        assertTrue(run(sender("feato.gunvalhalla.reload"), "reload"));
        assertTrue(configuration.debugEnabled());
        assertTrue(messages.getFirst().contains("debug.enabled=true"));
        assertTrue(logs.getFirst().getMessage().contains("TestConsole")); assertTrue(debugCalls.isEmpty());
        assertTrue(run(sender("feato.gunvalhalla.debug"), "debug", "profile", "Player"));
        assertEquals(List.of(List.of("debug", "profile", "Player")), debugCalls);
    }
    @Test void reloadReportsFalseAndDisablesDebugImmediately() throws Exception {
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true")); configuration.reload();
        Files.writeString(file, defaults);
        assertTrue(run(sender("feato.gunvalhalla.reload"), "reload"));
        assertFalse(configuration.debugEnabled()); assertTrue(messages.getFirst().contains("debug.enabled=false"));
        assertTrue(run(sender("feato.gunvalhalla.debug"), "debug", "exp", "Player", "100"));
        assertTrue(debugCalls.isEmpty());
    }
    @Test void bothDebugOperationsAreBlockedWhenDebugIsOff() {
        var sender = sender("feato.gunvalhalla.debug");
        assertTrue(run(sender, "debug", "profile", "Player"));
        assertTrue(run(sender, "debug", "exp", "Player", "1"));
        assertEquals(2, messages.size()); assertTrue(messages.stream().allMatch(m -> m.contains("disabled")));
        assertTrue(debugCalls.isEmpty());
    }
    @Test void debugPermissionAloneCannotReloadOrChangeSettings() throws Exception {
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true"));
        assertTrue(run(sender("feato.gunvalhalla.debug"), "reload"));
        assertEquals(List.of("Permission denied"), messages); assertFalse(configuration.debugEnabled());
        assertTrue(logs.isEmpty());
    }
    @Test void reloadPermissionAloneCannotInvokeEitherDebugOperation() throws Exception {
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true")); configuration.reload();
        var sender = sender("feato.gunvalhalla.reload");
        assertTrue(run(sender, "debug", "profile", "Player"));
        assertTrue(run(sender, "debug", "exp", "Player", "1"));
        assertTrue(messages.stream().allMatch(m -> m.equals("Permission denied"))); assertTrue(debugCalls.isEmpty());
    }
    @Test void validDebugArgumentsStillReachOriginalDebugHandler() throws Exception {
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true")); configuration.reload();
        var sender = sender("feato.gunvalhalla.debug");
        run(sender, "debug", "profile", "Player"); run(sender, "debug", "exp", "Player", "1");
        assertEquals(List.of(List.of("debug", "profile", "Player"), List.of("debug", "exp", "Player", "1")), debugCalls);
    }
    @Test void deniedRootCommandsAndCompletionCannotExposeAdminActions() {
        var sender = sender();
        assertTrue(run(sender, "reload")); assertTrue(run(sender, "debug", "profile", "Player"));
        assertTrue(tab(sender, "").isEmpty()); assertTrue(tab(sender, "debug", "").isEmpty());
        assertTrue(debugCalls.isEmpty());
    }
    @Test void reloadCompletionWorksWhileDebugIsOffAndHonorsPermissionAndPrefix() throws Exception {
        var both = sender("feato.gunvalhalla.reload", "feato.gunvalhalla.debug");
        assertEquals(List.of("reload"), tab(both, "")); assertEquals(List.of("reload"), tab(both, "re"));
        assertTrue(tab(sender("feato.gunvalhalla.debug"), "").isEmpty());
        assertTrue(tab(both, "debug", "").isEmpty());
        Files.writeString(file, defaults.replace("enabled: false", "enabled: true")); configuration.reload();
        assertEquals(List.of("reload", "debug"), tab(both, ""));
        assertEquals(List.of("debug"), tab(sender("feato.gunvalhalla.debug"), "d"));
        assertEquals(List.of("profile"), tab(both, "debug", "pr"));
        assertTrue(tab(sender("feato.gunvalhalla.reload"), "debug", "").isEmpty());
    }
    @Test void invalidRangeWarnsKeepsSettingsAndDoesNotCallDebugHandler() throws Exception {
        var original = configuration.settings();
        Files.writeString(file, defaults.replace("magazine-multiplier: 0.85", "magazine-multiplier: 1.5"));
        assertTrue(run(sender("feato.gunvalhalla.reload"), "reload"));
        assertSame(original, configuration.settings()); assertTrue(debugCalls.isEmpty());
        assertTrue(messages.getFirst().contains("tactical-reload.magazine-multiplier must be > 0 and <= 1"));
        assertEquals(Level.WARNING, logs.getFirst().getLevel());
    }
    @Test void malformedYamlDoesNotLeakFileContentOrOverwriteFile() throws Exception {
        String invalid = "private-value: [do-not-echo-this\n"; Files.writeString(file, invalid);
        var original = configuration.settings();
        assertTrue(run(sender("feato.gunvalhalla.reload"), "reload"));
        assertSame(original, configuration.settings()); assertEquals(invalid, Files.readString(file));
        assertEquals(List.of("Configuration reload failed: Invalid YAML syntax"), messages);
        assertFalse(logs.getFirst().getMessage().contains("do-not-echo-this")); assertTrue(debugCalls.isEmpty());
    }
    @Test void unreadableFileReportsFailureAndPreservesCurrentSnapshot() throws Exception {
        var original = configuration.settings(); Files.delete(file);
        run(sender("feato.gunvalhalla.reload"), "reload");
        assertSame(original, configuration.settings()); assertFalse(Files.exists(file));
        assertEquals(List.of("Configuration reload failed: Cannot read config.yml"), messages);
        assertEquals(Level.WARNING, logs.getFirst().getLevel());
    }
    @Test void reloadRequiresExactArityAndNeverTreatsUnknownSubcommandAsDebug() {
        var original = configuration.settings();
        assertFalse(run(sender("feato.gunvalhalla.reload"), "reload", "extra"));
        assertFalse(run(sender(), "unknown")); assertFalse(run(sender()));
        assertSame(original, configuration.settings()); assertTrue(logs.isEmpty()); assertTrue(debugCalls.isEmpty());
    }
}
