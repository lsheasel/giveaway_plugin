package first.giveaway_plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class GiveawayPlugin extends JavaPlugin {

    // Reads both & and § color codes, including §x§R§R§G§G§B§B hex colors
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final List<Player> participants = new ArrayList<>();
    private final Random random = new Random();
    private boolean giveawayRunning = false;
    private boolean nexoEnabled = false;
    private Component prefix;
    private ConfigGui configGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        nexoEnabled = Bukkit.getPluginManager().isPluginEnabled("Nexo");
        prefix = format(getConfig().getString("prefix", ""), Map.of());

        getCommand("giveaway").setExecutor(this);
        getCommand("reloadgiveaway").setExecutor(this);
        getCommand("giveawayconfig").setExecutor(this);
        configGui = new ConfigGui(this);
        Bukkit.getPluginManager().registerEvents(configGui, this);
        String BLUE = "\u001B[34m";
        String GREEN = "\u001B[32m";
        String WHITE = "\u001B[37m";
        String RESET = "\u001B[0m";

        getLogger().info(BLUE + "┌─────────────────────" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Plugin: " + GREEN + "enabled" + RESET);
        getLogger().info(BLUE + "│ " + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Commands: " + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "/giveaway ✅" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "/reloadgiveaway ✅" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "/giveawayconfig ✅" + RESET);
        getLogger().info(BLUE + "│ " + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Nexo: " + (nexoEnabled ? GREEN + "hooked" : WHITE + "not found") + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Version: " + getPluginMeta().getVersion() + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Made by Shease" + RESET);
        getLogger().info(BLUE + "└─────────────────────" + RESET);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("giveaway")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix.append(Component.text("Only players can start a giveaway.")));
                return true;
            }
            if (!player.hasPermission("giveaway.use")) {
                player.sendMessage(message("messages.no_permission", Map.of()));
                return true;
            }
            if (giveawayRunning) {
                player.sendMessage(message("messages.giveaway_running", Map.of()));
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType().isAir()) {
                player.sendMessage(message("messages.no_item_in_hand", Map.of()));
                return true;
            }

            ItemStack prize = itemInHand.clone();
            // Uses the item's real name (custom names from Nexo included) and shows its tooltip on hover
            Component itemName = prize.effectiveName().hoverEvent(prize.asHoverEvent());

            player.getInventory().setItemInMainHand(null);

            participants.clear();
            participants.addAll(Bukkit.getOnlinePlayers());
            // Random order, so the last name shown in the action bar (the winner) is random too
            Collections.shuffle(participants, random);
            giveawayRunning = true;

            Bukkit.broadcast(message("messages.giveaway_start", Map.of(
                    "{item}", itemName,
                    "{player}", Component.text(player.getName()))));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title("titles.giveaway_start", Map.of("{item}", itemName)));
                playSound(p, "sounds.giveaway_start");
            }

            new BukkitRunnable() {
                int countdown = getConfig().getInt("giveaway.duration");
                int currentPlayerIndex = 0;
                Player lastShown = null;

                @Override
                public void run() {
                    // Players who left during the giveaway can't win
                    participants.removeIf(p -> !p.isOnline());

                    if (participants.isEmpty()) {
                        // Nobody left to win, give the item back (or drop it where the host was)
                        giveItem(player, prize);
                        giveawayRunning = false;
                        cancel();
                        return;
                    }

                    if (countdown <= 0) {
                        // The winner is whoever the action bar stopped on
                        Player winner = lastShown != null && lastShown.isOnline()
                                ? lastShown
                                : participants.get(random.nextInt(participants.size()));
                        giveItem(winner, prize);

                        Map<String, Component> placeholders = Map.of(
                                "{winner}", Component.text(winner.getName()),
                                "{item}", itemName);

                        Bukkit.broadcast(message("messages.giveaway_end", placeholders));

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.showTitle(title("titles.giveaway_end", placeholders));
                            playSound(p, "sounds.giveaway_end");
                        }

                        giveawayRunning = false;
                        cancel();
                    } else {
                        currentPlayerIndex %= participants.size();
                        Player currentPlayer = participants.get(currentPlayerIndex);
                        lastShown = currentPlayer;
                        Component actionBar = format(getConfig().getString("actionbars.giveaway_ongoing", ""),
                                Map.of("{player}", Component.text(currentPlayer.getName()), "{item}", itemName));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendActionBar(actionBar);
                            playSound(p, "sounds.giveaway_tick");
                        }
                        countdown--;
                        currentPlayerIndex = (currentPlayerIndex + 1) % participants.size();
                    }
                }
            }.runTaskTimer(this, 0L, 20L);

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("giveawayconfig")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix.append(Component.text("Only players can open the config menu.")));
            } else if (!player.hasPermission("giveaway.config")) {
                player.sendMessage(message("messages.no_permission", Map.of()));
            } else {
                configGui.openMain(player);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("reloadgiveaway")) {
            if (sender.hasPermission("giveaway.reload")) {
                reloadPlugin();
                sender.sendMessage(message("messages.plugin_reloaded", Map.of()));
            } else {
                sender.sendMessage(message("messages.no_permission", Map.of()));
            }
            return true;
        }

        return false;
    }

    void reloadPlugin() {
        reloadConfig();
        prefix = format(getConfig().getString("prefix", ""), Map.of());
    }

    /**
     * Saves changes made from the config GUI and applies them right away.
     */
    void saveSettings() {
        saveConfig();
        prefix = format(getConfig().getString("prefix", ""), Map.of());
    }

    private void giveItem(Player player, ItemStack item) {
        if (player.isOnline()) {
            // Drop whatever doesn't fit into the inventory
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private Component message(String key, Map<String, Component> placeholders) {
        return prefix.append(format(getConfig().getString(key, "Message not found: " + key), placeholders));
    }

    private Title title(String key, Map<String, Component> placeholders) {
        return Title.title(
                format(getConfig().getString(key + ".title", ""), placeholders),
                format(getConfig().getString(key + ".subtitle", ""), placeholders),
                Title.Times.times(
                        ticks(getConfig().getInt(key + ".fade_in")),
                        ticks(getConfig().getInt(key + ".stay")),
                        ticks(getConfig().getInt(key + ".fade_out"))
                )
        );
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(ticks * 50L);
    }

    /**
     * Turns a config string into a component: color codes, {placeholders} and Nexo glyphs.
     */
    Component format(String text, Map<String, Component> placeholders) {
        Component component = LEGACY.deserialize(text.replace('&', LegacyComponentSerializer.SECTION_CHAR));

        for (Map.Entry<String, Component> placeholder : placeholders.entrySet()) {
            component = component.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(placeholder.getKey())
                    .replacement(placeholder.getValue())
                    .build());
        }

        if (nexoEnabled) {
            component = NexoHook.replaceGlyphs(component);
        }
        return component;
    }

    void playSound(Player player, String key) {
        String soundName = getConfig().getString(key + ".name", "BLOCK_NOTE_BLOCK_PLING");
        float volume = (float) getConfig().getDouble(key + ".volume", 1.0);
        float pitch = (float) getConfig().getDouble(key + ".pitch", 1.0);

        Sound sound = resolveSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } else {
            // Not a known constant, treat it as a sound key like "minecraft:block.note_block.pling"
            // (custom sounds from a Nexo resource pack work this way too)
            player.playSound(player.getLocation(), soundName.toLowerCase(Locale.ROOT), volume, pitch);
        }
    }

    private Sound resolveSound(String name) {
        try {
            Object value = Sound.class.getField(name.toUpperCase(Locale.ROOT)).get(null);
            return value instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    public void onDisable() {
        String BLUE = "\u001B[34m";
        String RED = "\u001B[31m";
        String WHITE = "\u001B[37m";
        String RESET = "\u001B[0m";

        getLogger().info(BLUE + "┌──────────────────────" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Plugin: " + RED + "disabled" + RESET);
        getLogger().info(BLUE + "│ " + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Commands:" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "/giveaway ❌" + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "/reloadgiveaway ❌" + RESET);
        getLogger().info(BLUE + "│ " + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Version: " + getPluginMeta().getVersion() + RESET);
        getLogger().info(BLUE + "│ " + WHITE + "Made by Shease" + RESET);
        getLogger().info(BLUE + "└──────────────────────" + RESET);
    }
}
