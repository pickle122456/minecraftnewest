package com.duel.plugin;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class ActiveDuel {

    private final UUID player1;
    private final UUID player2;

    // Tracks the last time each player dealt damage (in ms)
    private long lastHitTime;

    private BukkitTask drawCheckTask;

    public ActiveDuel(UUID player1, UUID player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.lastHitTime = System.currentTimeMillis();
    }

    public UUID getPlayer1() {
        return player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    public boolean involves(UUID playerId) {
        return player1.equals(playerId) || player2.equals(playerId);
    }

    public UUID getOpponent(UUID playerId) {
        if (player1.equals(playerId)) return player2;
        if (player2.equals(playerId)) return player1;
        return null;
    }

    public long getLastHitTime() {
        return lastHitTime;
    }

    public void updateLastHitTime() {
        this.lastHitTime = System.currentTimeMillis();
    }

    public BukkitTask getDrawCheckTask() {
        return drawCheckTask;
    }

    public void setDrawCheckTask(BukkitTask drawCheckTask) {
        this.drawCheckTask = drawCheckTask;
    }

    public void cancelDrawCheckTask() {
        if (drawCheckTask != null && !drawCheckTask.isCancelled()) {
            drawCheckTask.cancel();
        }
    }
}
