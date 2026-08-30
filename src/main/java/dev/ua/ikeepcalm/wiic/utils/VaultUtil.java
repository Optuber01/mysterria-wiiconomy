package dev.ua.ikeepcalm.wiic.utils;

import dev.ua.ikeepcalm.wiic.WIIC;
import dev.ua.ikeepcalm.wiic.domain.wallet.models.WalletData;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VaultUtil {

    public static boolean deposit(UUID player, double amount) {
        if (WIIC.getEcon() == null) return false;
        try {
            EconomyResponse response = WIIC.getEcon().deposit("iConomyUnlocked", player, BigDecimal.valueOf(amount));
            return response != null && response.transactionSuccess();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean withdraw(UUID player, double amount) {
        if (WIIC.getEcon() == null) return false;
        try {
            EconomyResponse response = WIIC.getEcon().withdraw("iConomyUnlocked", player, BigDecimal.valueOf(amount));
            return response != null && response.transactionSuccess();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Reads the same account used by {@link #deposit(UUID, double)} and
     * {@link #withdraw(UUID, double)}. A null result means the balance was not
     * observable; callers must not treat it as a real zero balance.
     */
    public static @Nullable BigDecimal balance(UUID player) {
        if (WIIC.getEcon() == null) return null;
        try {
            return WIIC.getEcon().balance("iConomyUnlocked", player);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static CompletableFuture<Double> getBalance(UUID player) {
        final CompletableFuture<Double> result = new CompletableFuture<>();
        if (WIIC.getEcon() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(WIIC.INSTANCE, () -> {
                BigDecimal balance = balance(player);
                result.complete(balance == null ? 0.0 : balance.doubleValue());
            });
        } else {
            result.complete(0.0);
        }
        return result;
    }

    public static CompletableFuture<WalletData> getWalletData(@NotNull UUID uniqueId) {
        return getBalance(uniqueId).thenApplyAsync(value -> new WalletData(value.intValue()));
    }
}
