package headstash;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class HeadStash extends JavaPlugin {
    private HeadStashListener deathEventListener;

    @Override
    public void onEnable() {
        File dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not create data file", e);
            }
        }
        deathEventListener = new HeadStashListener(this);
        deathEventListener.loadData();
        getServer().getPluginManager().registerEvents(deathEventListener, this);
        getLogger().info("HeadStash enabled!");
    }

    @Override
    public void onDisable() {
        deathEventListener.saveData();
        getLogger().info("HeadStash disabled!");
    }
}
