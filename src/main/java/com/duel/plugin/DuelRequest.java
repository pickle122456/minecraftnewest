package com.duel.plugin;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class DuelRequest {

    private final UUID challengerId;
    private final UUID challengedId;
    private BukkitTask expiryTask;

    public DuelRequest(UUID challengerId, UUID challengedId) {
        this.challengerId = challengerId;
        this.challengedId = challengedId;
    }

    public UUID getChallengerId() {
        return challengerId;
    }

    public UUID getChallengedId() {
        return challengedId;
    }

    public BukkitTask getExpiryTask() {
        return expiryTask;
    }

    public void setExpiryTask(BukkitTask expiryTask) {
        this.expiryTask = expiryTask;
    }

    public void cancelExpiryTask() {
        if (expiryTask != null && !expiryTask.isCancelled()) {
            expiryTask.cancel();
        }
    }
}
