package com.duel.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DuelManager {

    private final DuelPlugin plugin;

    // Key: challenged player UUID, Value: their pending request
    private final Map<UUID, DuelRequest> pendingRequests = new ConcurrentHashMap<>();

    // Key: either player's UUID, Value: the active duel
    private final Map<UUID, ActiveDuel> activeDuels = new ConcurrentHashMap<>();

    // How long (seconds) a duel request lasts before expiring
    private static final int REQUEST_EXPIRY_SECONDS = 30;

    // How long (ms) with no hits before a draw is declared
    private static final long DRAW_TIMEOUT_MS = 60_000L;

    // How often (ticks) we check for the draw condition (every 5 seconds)
    private static final long DRAW_CHECK_INTERVAL_TICKS = 100L;

    public DuelManager(DuelPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Sending a duel request
    // -------------------------------------------------------------------------

    public void sendDuelRequest(Player challenger, Player challenged) {
        UUID challengerId = challenger.getUniqueId();
        UUID challengedId = challenged.getUniqueId();

        if (challenger.equals(challenged)) {
            challenger.sendMessage(Component.text("You can't duel yourself!", NamedTextColor.RED));
            return;
        }

        if (isInDuel(challengerId)) {
            challenger.sendMessage(Component.text("You are already in a duel!", NamedTextColor.RED));
            return;
        }

        if (isInDuel(challengedId)) {
            challenger.sendMessage(Component.text(challenged.getName() + " is already in a duel!", NamedTextColor.RED));
            return;
        }

        if (hasPendingRequestFrom(challengerId, challengedId)) {
            challenger.sendMessage(Component.text("You already sent a duel request to " + challenged.getName() + "!", NamedTextColor.RED));
            return;
        }

        DuelRequest request = new DuelRequest(challengerId, challengedId);
        pendingRequests.put(challengedId, request);

        // Tell the challenger
        challenger.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        challenger.sendMessage(Component.text("⚔ Duel request sent to ", NamedTextColor.YELLOW)
                .append(Component.text(challenged.getName(), NamedTextColor.AQUA))
                .append(Component.text("!", NamedTextColor.YELLOW)));
        challenger.sendMessage(Component.text("Waiting for them to accept... (" + REQUEST_EXPIRY_SECONDS + "s)", NamedTextColor.GRAY));
        challenger.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        // Tell the challenged player
        challenged.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        challenged.sendMessage(Component.text("⚔ ", NamedTextColor.GOLD)
                .append(Component.text(challenger.getName(), NamedTextColor.AQUA))
                .append(Component.text(" has challenged you to a duel!", NamedTextColor.YELLOW)));
        challenged.sendMessage(Component.text("Type ", NamedTextColor.GRAY)
                .append(Component.text("/duel accept", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" to accept or ", NamedTextColor.GRAY))
                .append(Component.text("/duel deny", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text(" to decline.", NamedTextColor.GRAY)));
        challenged.sendMessage(Component.text("This request expires in " + REQUEST_EXPIRY_SECONDS + " seconds.", NamedTextColor.GRAY));
        challenged.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        // Schedule expiry
        var expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.get(challengedId) == request) {
                pendingRequests.remove(challengedId);
                Player c = Bukkit.getPlayer(challengerId);
                Player ch = Bukkit.getPlayer(challengedId);
                if (c != null) c.sendMessage(Component.text("Your duel request to " + challenged.getName() + " has expired.", NamedTextColor.RED));
                if (ch != null) ch.sendMessage(Component.text("The duel request from " + challenger.getName() + " has expired.", NamedTextColor.RED));
            }
        }, REQUEST_EXPIRY_SECONDS * 20L);

        request.setExpiryTask(expiryTask);
    }

    // -------------------------------------------------------------------------
    // Accepting a duel request
    // -------------------------------------------------------------------------

    public void acceptDuelRequest(Player challenged) {
        UUID challengedId = challenged.getUniqueId();
        DuelRequest request = pendingRequests.remove(challengedId);

        if (request == null) {
            challenged.sendMessage(Component.text("You have no pending duel request!", NamedTextColor.RED));
            return;
        }

        request.cancelExpiryTask();

        Player challenger = Bukkit.getPlayer(request.getChallengerId());
        if (challenger == null || !challenger.isOnline()) {
            challenged.sendMessage(Component.text("The challenger is no longer online.", NamedTextColor.RED));
            return;
        }

        if (isInDuel(request.getChallengerId())) {
            challenged.sendMessage(Component.text(challenger.getName() + " is already in another duel.", NamedTextColor.RED));
            return;
        }

        startDuel(challenger, challenged);
    }

    // -------------------------------------------------------------------------
    // Denying a duel request
    // -------------------------------------------------------------------------

    public void denyDuelRequest(Player challenged) {
        UUID challengedId = challenged.getUniqueId();
        DuelRequest request = pendingRequests.remove(challengedId);

        if (request == null) {
            challenged.sendMessage(Component.text("You have no pending duel request!", NamedTextColor.RED));
            return;
        }

        request.cancelExpiryTask();

        challenged.sendMessage(Component.text("You denied the duel request.", NamedTextColor.RED));

        Player challenger = Bukkit.getPlayer(request.getChallengerId());
        if (challenger != null) {
            challenger.sendMessage(Component.text(challenged.getName() + " denied your duel request.", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Starting a duel
    // -------------------------------------------------------------------------

    private void startDuel(Player challenger, Player challenged) {
        ActiveDuel duel = new ActiveDuel(challenger.getUniqueId(), challenged.getUniqueId());

        activeDuels.put(challenger.getUniqueId(), duel);
        activeDuels.put(challenged.getUniqueId(), duel);

        // Broadcast to both players
        broadcastToDuel(duel,
                Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
        broadcastToDuel(duel,
                Component.text("⚔ DUEL STARTED! ⚔", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        broadcastToDuel(duel,
                Component.text(challenger.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" vs ", NamedTextColor.WHITE))
                        .append(Component.text(challenged.getName(), NamedTextColor.AQUA)));
        broadcastToDuel(duel,
                Component.text("If you leave the server, you lose!", NamedTextColor.YELLOW));
        broadcastToDuel(duel,
                Component.text("No hits for 60 seconds = draw!", NamedTextColor.YELLOW));
        broadcastToDuel(duel,
                Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));

        // Schedule periodic draw check
        var drawCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long elapsed = System.currentTimeMillis() - duel.getLastHitTime();
            if (elapsed >= DRAW_TIMEOUT_MS) {
                endDuelDraw(duel);
            }
        }, DRAW_CHECK_INTERVAL_TICKS, DRAW_CHECK_INTERVAL_TICKS);

        duel.setDrawCheckTask(drawCheckTask);
    }

    // -------------------------------------------------------------------------
    // Registering a hit (resets the draw timer)
    // -------------------------------------------------------------------------

    public void registerHit(UUID attackerId, UUID victimId) {
        ActiveDuel duel = activeDuels.get(attackerId);
        if (duel == null) return;
        if (!duel.involves(victimId)) return;
        duel.updateLastHitTime();
    }

    // -------------------------------------------------------------------------
    // Player death during a duel
    // -------------------------------------------------------------------------

    public void handlePlayerDeath(Player loser) {
        UUID loserId = loser.getUniqueId();
        ActiveDuel duel = activeDuels.get(loserId);
        if (duel == null) return;

        UUID winnerId = duel.getOpponent(loserId);
        Player winner = Bukkit.getPlayer(winnerId);

        cleanupDuel(duel);

        broadcastDuelResult(
                winner != null ? winner.getName() : "Unknown",
                loser.getName(),
                "won the duel!"
        );

        if (winner != null) {
            winner.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            winner.sendMessage(Component.text("🏆 You won the duel against " + loser.getName() + "!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            winner.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        }
    }

    // -------------------------------------------------------------------------
    // Player disconnects during a duel
    // -------------------------------------------------------------------------

    public void handlePlayerDisconnect(Player quitter) {
        UUID quitterId = quitter.getUniqueId();
        ActiveDuel duel = activeDuels.get(quitterId);
        if (duel == null) {
            // Also clean up any pending requests they made or received
            cleanupPendingRequestsFor(quitterId);
            return;
        }

        UUID winnerId = duel.getOpponent(quitterId);
        Player winner = Bukkit.getPlayer(winnerId);
        String winnerName = winner != null ? winner.getName() : Bukkit.getOfflinePlayer(winnerId).getName();

        // Kill the disconnecting player via scheduler since they're offline
        cleanupDuel(duel);

        // Announce
        Bukkit.broadcastMessage("");
        broadcastDuelResult(
                winnerName != null ? winnerName : "Unknown",
                quitter.getName(),
                "won! (opponent disconnected)"
        );

        // Notify the winner if online
        if (winner != null) {
            winner.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            winner.sendMessage(Component.text("🏆 You won! " + quitter.getName() + " left the server!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            winner.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        }

        cleanupPendingRequestsFor(quitterId);
    }

    // -------------------------------------------------------------------------
    // Draw
    // -------------------------------------------------------------------------

    private void endDuelDraw(ActiveDuel duel) {
        cleanupDuel(duel);

        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());

        String name1 = p1 != null ? p1.getName() : Bukkit.getOfflinePlayer(duel.getPlayer1()).getName();
        String name2 = p2 != null ? p2.getName() : Bukkit.getOfflinePlayer(duel.getPlayer2()).getName();

        if (p1 != null) {
            p1.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
            p1.sendMessage(Component.text("🤝 The duel has ended in a DRAW! (No hits for 60 seconds)", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            p1.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        }
        if (p2 != null) {
            p2.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
            p2.sendMessage(Component.text("🤝 The duel has ended in a DRAW! (No hits for 60 seconds)", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            p2.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.YELLOW));
        }

        Bukkit.getServer().sendMessage(
                Component.text("[Duel] ", NamedTextColor.GOLD)
                        .append(Component.text(name1 + " vs " + name2 + " ended in a draw!", NamedTextColor.YELLOW))
        );
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    public boolean isInDuel(UUID playerId) {
        return activeDuels.containsKey(playerId);
    }

    public ActiveDuel getActiveDuel(UUID playerId) {
        return activeDuels.get(playerId);
    }

    private boolean hasPendingRequestFrom(UUID challengerId, UUID challengedId) {
        DuelRequest existing = pendingRequests.get(challengedId);
        return existing != null && existing.getChallengerId().equals(challengerId);
    }

    private void cleanupDuel(ActiveDuel duel) {
        duel.cancelDrawCheckTask();
        activeDuels.remove(duel.getPlayer1());
        activeDuels.remove(duel.getPlayer2());
    }

    private void cleanupPendingRequestsFor(UUID playerId) {
        // Remove if they are the challenged player
        DuelRequest req = pendingRequests.remove(playerId);
        if (req != null) {
            req.cancelExpiryTask();
        }
        // Remove any request they made as challenger
        pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().getChallengerId().equals(playerId)) {
                entry.getValue().cancelExpiryTask();
                return true;
            }
            return false;
        });
    }

    private void broadcastToDuel(ActiveDuel duel, Component message) {
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        if (p1 != null) p1.sendMessage(message);
        if (p2 != null) p2.sendMessage(message);
    }

    private void broadcastDuelResult(String winnerName, String loserName, String suffix) {
        Bukkit.getServer().sendMessage(
                Component.text("[Duel] ", NamedTextColor.GOLD)
                        .append(Component.text(winnerName, NamedTextColor.AQUA))
                        .append(Component.text(" " + suffix + " (defeated ", NamedTextColor.YELLOW))
                        .append(Component.text(loserName, NamedTextColor.RED))
                        .append(Component.text(")", NamedTextColor.YELLOW))
        );
    }

    public void cancelAllDuels() {
        // Collect unique duels (each duel is stored twice)
        Set<ActiveDuel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ActiveDuel duel : activeDuels.values()) {
            if (seen.add(duel)) {
                duel.cancelDrawCheckTask();
            }
        }
        activeDuels.clear();

        for (DuelRequest req : pendingRequests.values()) {
            req.cancelExpiryTask();
        }
        pendingRequests.clear();
    }
}
