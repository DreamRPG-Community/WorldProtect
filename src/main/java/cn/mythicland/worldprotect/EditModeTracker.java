package cn.mythicland.worldprotect;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds temporary per-player edit mode state.
 */
final class EditModeTracker {

    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    boolean toggle(UUID playerId) {
        if (enabledPlayers.remove(playerId)) return false;
        enabledPlayers.add(playerId);
        return true;
    }

    boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    void remove(UUID playerId) {
        enabledPlayers.remove(playerId);
    }

    void clear() {
        enabledPlayers.clear();
    }
}
