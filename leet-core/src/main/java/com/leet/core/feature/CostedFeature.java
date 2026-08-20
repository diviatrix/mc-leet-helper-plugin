package com.leet.core.feature;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

/**
 * Opt-in role: a feature that charges a per-use Vault economy cost.
 *
 * <p>Requires the {@link MessagingFeature} role to report insufficient funds.
 * Default implementation reads {@link #cost()} and {@link #economy()} (null
 * economy = free).
 */
public interface CostedFeature extends MessagingFeature {

    /** Configured per-use cost (0 = free). */
    double cost();

    /** Shared Vault economy; may be null when Vault is absent. */
    Economy economy();

    /** Whether the player can afford the per-use cost. */
    default boolean hasBalance(Player player, double amount) {
        if (amount <= 0) return true;
        if (economy() == null) return true;
        return economy().has(player, amount);
    }

    /** Withdraws the per-use cost; true on success (or when free). */
    default boolean withdraw(Player player, double amount) {
        if (amount <= 0) return true;
        if (economy() == null) return true;
        return economy().withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * True when the use may proceed. With a cost of 0 (default) this is always
     * free; otherwise blocks and reports when the player lacks funds.
     */
    default boolean chargeUse(Player player) {
        double c = cost();
        if (c <= 0) return true;
        if (economy() == null) return true;
        if (!hasBalance(player, c)) {
            sendMessage(player, "insufficient-funds", "<cost>", String.valueOf(c));
            return false;
        }
        return withdraw(player, c);
    }
}
