package first.giveaway_plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GiveawayPlugin extends JavaPlugin {

    private List<Player> participants = new ArrayList<>();
    private String prefix; // Der Prefix aus der Konfiguration

    @Override
    public void onEnable() {
        // Lade die Konfiguration
        saveDefaultConfig();

        // Lade den Prefix aus der Konfiguration
        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", ""));

        // Registriere die Befehle
        getCommand("giveaway").setExecutor(this);
        getCommand("reloadgiveaway").setExecutor(this); // Befehl für das Neu-Laden des Plugins
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("giveaway")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Hole das Item in der Hand des Spielers
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand == null || itemInHand.getType().isAir()) {
                    player.sendMessage(prefix + formatMessage("messages.no_item_in_hand"));
                    return false;
                }

                // Itemnamen auslesen
                String itemName = getItemDisplayName(itemInHand);

                // Item vom Spieler wegnehmen
                player.getInventory().setItemInMainHand(null);

                // Teilnehmerliste leeren und alle Spieler hinzufügen
                participants.clear();
                participants.addAll(Bukkit.getOnlinePlayers());

                // Nachricht, dass das Giveaway gestartet wurde
                Bukkit.broadcastMessage(prefix + formatMessage("messages.giveaway_start")
                        .replace("{item}", itemName)
                        .replace("{player}", player.getName()));

                // Titel anzeigen
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(
                            formatMessage("titles.giveaway_start.title"),
                            formatMessage("titles.giveaway_start.subtitle").replace("{item}", itemName),
                            getConfig().getInt("titles.giveaway_start.fade_in"),
                            getConfig().getInt("titles.giveaway_start.stay"),
                            getConfig().getInt("titles.giveaway_start.fade_out")
                    );
                    playSound(p, "sounds.giveaway_start");
                }

                // Giveaway starten (10 Sekunden warten und dann einen zufälligen Spieler auswählen)
                new BukkitRunnable() {
                    int countdown = getConfig().getInt("giveaway.duration");
                    int currentPlayerIndex = 0;

                    @Override
                    public void run() {
                        if (countdown <= 0) {
                            // Zufälligen Gewinner auswählen
                            Player winner = participants.get(new Random().nextInt(participants.size()));
                            winner.getInventory().addItem(itemInHand);

                            // Gewinnernachricht senden
                            Bukkit.broadcastMessage(prefix + formatMessage("messages.giveaway_end")
                                    .replace("{winner}", winner.getName())
                                    .replace("{item}", itemName));

                            // Gewinner-Titel anzeigen
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendTitle(
                                        formatMessage("titles.giveaway_end.title"),
                                        formatMessage("titles.giveaway_end.subtitle")
                                                .replace("{winner}", winner.getName())
                                                .replace("{item}", itemName),
                                        getConfig().getInt("titles.giveaway_end.fade_in"),
                                        getConfig().getInt("titles.giveaway_end.stay"),
                                        getConfig().getInt("titles.giveaway_end.fade_out")
                                );
                                playSound(p, "sounds.giveaway_end");
                            }

                            cancel();
                        } else {
                            // Nächsten Spieler auswählen und in der ActionBar anzeigen
                            Player currentPlayer = participants.get(currentPlayerIndex);
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendActionBar(formatMessage("actionbars.giveaway_ongoing")
                                        .replace("{player}", currentPlayer.getName()));
                                playSound(p, "sounds.giveaway_tick");
                            }

                            // Countdown und Spielerrotation
                            countdown--;
                            currentPlayerIndex = (currentPlayerIndex + 1) % participants.size();
                        }
                    }
                }.runTaskTimer(this, 0L, 20L); // Alle 20 Ticks (1 Sekunde)

                return true;
            } else {
                sender.sendMessage(prefix + "Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
                return false;
            }
        }

        if (cmd.getName().equalsIgnoreCase("reloadgiveaway")) {
            if (sender.hasPermission("giveaway.reload")) {
                reloadPlugin();
                sender.sendMessage(prefix + formatMessage("messages.plugin_reloaded"));
            } else {
                sender.sendMessage(prefix + formatMessage("messages.no_permission"));
            }
            return true;
        }

        return false;
    }

    private void reloadPlugin() {
        // Plugin wird deaktiviert und wieder aktiviert
        Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("giveaway_plugin");
        if (plugin != null) {
            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            Bukkit.getServer().getPluginManager().enablePlugin(plugin);
        }

        // Reload den Prefix aus der Konfiguration
        prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("prefix", ""));
    }

    // Hilfsmethode für das Formatieren von Nachrichten
    private String formatMessage(String key) {
        String message = getConfig().getString(key, "Message not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Hilfsmethode für das Abspielen von Sounds
    private void playSound(Player player, String key) {
        String soundName = getConfig().getString(key + ".name", "BLOCK_NOTE_BLOCK_PLING");
        float volume = (float) getConfig().getDouble(key + ".volume", 1.0);
        float pitch = (float) getConfig().getDouble(key + ".pitch", 1.0);

        player.playSound(player.getLocation(), Sound.valueOf(soundName), volume, pitch);
    }

    // Hilfsmethode zum Abrufen des Item-Namens
    private String getItemDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName(); // Benutzerdefinierter Name
        }
        return item.getType().toString().replace("_", " ").toLowerCase(); // Generischer Name
    }
}
