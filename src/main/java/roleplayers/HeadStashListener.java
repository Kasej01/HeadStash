package headstash;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class HeadStashListener implements Listener {

    private final JavaPlugin plugin;
    private final HashMap<String, ItemStack[]> deathInventories = new HashMap<>();
    private final HashMap<String, UUID> deathLocations = new HashMap<>();
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    public HeadStashListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private String locationToString(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    private Location stringToLocation(String locStr) {
        String[] parts = locStr.split("_");
        return new Location(Bukkit.getWorld(parts[0]), 
                            Integer.parseInt(parts[1]), 
                            Integer.parseInt(parts[2]), 
                            Integer.parseInt(parts[3]));
    }

    public void saveData() {
        for (Map.Entry<String, ItemStack[]> entry : deathInventories.entrySet()) {
            String locStr = entry.getKey();
            ItemStack[] inventory = entry.getValue();
            dataConfig.set("inventories." + locStr, inventory);
        }

        for (Map.Entry<String, UUID> entry : deathLocations.entrySet()) {
            String locStr = entry.getKey();
            UUID uuid = entry.getValue();
            dataConfig.set("locations." + locStr, uuid.toString());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data to file", e);
        }
    }

    public void loadData() {
        if (!dataFile.exists()) return;

        for (String locStr : dataConfig.getConfigurationSection("inventories").getKeys(false)) {
            List<ItemStack> inventoryList = (List<ItemStack>) dataConfig.get("inventories." + locStr);
            ItemStack[] inventory = inventoryList.toArray(new ItemStack[0]);
            deathInventories.put(locStr, inventory);
        }

        for (String locStr : dataConfig.getConfigurationSection("locations").getKeys(false)) {
            UUID uuid = UUID.fromString(dataConfig.getString("locations." + locStr));
            deathLocations.put(locStr, uuid);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();

        // Store player inventory
        ItemStack[] playerInventory = player.getInventory().getContents();

        // Clear player's inventory from drops
        event.getDrops().clear();

        // Place a player head at the death location
        Location deathLocation = player.getLocation();
        Block block = deathLocation.getBlock();
        block.setType(Material.PLAYER_HEAD);

        Skull skull = (Skull) block.getState();
        skull.setOwningPlayer(player);
        skull.update();

        // Add a unique identifier to the head using PersistentDataContainer
        NamespacedKey key = new NamespacedKey(plugin, "playerUUID");
        skull.getPersistentDataContainer().set(key, PersistentDataType.STRING, playerUUID.toString());
        skull.update();

        // Store the inventory and death location
        String locationKey = locationToString(deathLocation);
        deathInventories.put(locationKey, playerInventory);
        deathLocations.put(locationKey, playerUUID);

        // Notify player
        String deathLocationMessage = String.format("X: %d, Y: %d, Z: %d", deathLocation.getBlockX(), deathLocation.getBlockY(), deathLocation.getBlockZ());
        player.sendMessage(ChatColor.GRAY + "Your inventory is stored in your head at: " + deathLocationMessage);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.PLAYER_HEAD) {
                Player player = event.getPlayer();
                Skull skull = (Skull) clickedBlock.getState();
                NamespacedKey key = new NamespacedKey(plugin, "playerUUID");
                String storedUUID = skull.getPersistentDataContainer().get(key, PersistentDataType.STRING);

                if (storedUUID != null) {
                    if (storedUUID.equals(player.getUniqueId().toString())) {
                        String locationKey = locationToString(clickedBlock.getLocation());

                        if (deathInventories.containsKey(locationKey)) {
                            ItemStack[] storedInventory = deathInventories.get(locationKey);

                            // Add stored inventory to player's current inventory
                            for (ItemStack item : storedInventory) {
                                if (item != null) {
                                    player.getInventory().addItem(item);
                                }
                            }

                            // Remove the stored inventory and death location
                            deathInventories.remove(locationKey);
                            deathLocations.remove(locationKey);

                            // Remove the head block
                            clickedBlock.setType(Material.AIR);

                            player.sendMessage(ChatColor.GREEN + "Your inventory has been restored.");
                        } else {
                            player.sendMessage(ChatColor.RED + "No player data found in this head.");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "This is not your death inventory");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "No player data found in this head.");
                }
            }
        }
    }
}
