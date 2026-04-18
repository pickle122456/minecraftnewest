package com.duel.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class DuelPlugin extends JavaPlugin {

    private DuelManager duelManager;

    @Override
    public void onEnable() {
        duelManager = new DuelManager(this);

        DuelCommand duelCommand = new DuelCommand(duelManager);
        getCommand("duel").setExecutor(duelCommand);
        getCommand("duel").setTabCompleter(duelCommand);
        getServer().getPluginManager().registerEvents(new DuelListener(duelManager), this);

        getLogger().info("DuelPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (duelManager != null) {
            duelManager.cancelAllDuels();
        }
        getLogger().info("DuelPlugin disabled!");
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }
}
