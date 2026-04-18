package com.duel.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DuelListener implements Listener {

    private final DuelManager duelManager;

    public DuelListener(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    /**
     * Track damage between duel opponents to reset the draw timer.
     * Anyone can hit anyone — we only care about resetting the timer
     * when the two duel opponents hit each other.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Only reset the draw timer when the two dueling opponents hit each other
        if (duelManager.isInDuel(attacker.getUniqueId()) && duelManager.isInDuel(victim.getUniqueId())) {
            ActiveDuel duel = duelManager.getActiveDuel(attacker.getUniqueId());
            if (duel != null && duel.involves(victim.getUniqueId())) {
                duelManager.registerHit(attacker.getUniqueId(), victim.getUniqueId());
            }
        }
    }

    /**
     * When a dueling player dies, end the duel.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (duelManager.isInDuel(player.getUniqueId())) {
            duelManager.handlePlayerDeath(player);
        }
    }

    /**
     * When a dueling player disconnects, they lose the duel.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (duelManager.isInDuel(player.getUniqueId())) {
            duelManager.handlePlayerDisconnect(player);
        }
    }
}
