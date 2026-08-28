package dev.ua.ikeepcalm.wiic.utils;

import dev.ua.ikeepcalm.coi.api.audit.AuditEmission;
import dev.ua.ikeepcalm.coi.api.audit.AuditOutcome;
import dev.ua.ikeepcalm.coi.api.audit.AuditPrivacy;
import dev.ua.ikeepcalm.coi.api.audit.AuditRisk;
import dev.ua.ikeepcalm.coi.api.audit.MysterriaAudit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Optional, non-blocking bridge to the shared Mysterria audit ledger. */
public final class MysterriaAuditBridge {
    private MysterriaAuditBridge() {
    }

    public static void emitWallet(String operation, Player player, ItemStack item,
                                  long coppets, boolean success) {
        if (player == null || item == null) return;
        try {
            MysterriaAudit audit = Bukkit.getServicesManager().load(MysterriaAudit.class);
            if (audit == null) return;
            audit.emit(new AuditEmission(
                    "mysterria-wiiconomy.wallet." + operation,
                    success ? AuditOutcome.COMMITTED : AuditOutcome.FAILED,
                    AuditRisk.NORMAL,
                    AuditPrivacy.STAFF_RESTRICTED,
                    null,
                    null,
                    player.getUniqueId(),
                    player.getUniqueId(),
                    null,
                    success ? null : operation + " failed",
                    Map.of(
                            "item", item.getType().name().toLowerCase(),
                            "amount", item.getAmount(),
                            "coppets", coppets,
                            "success", success))));
        } catch (RuntimeException | LinkageError ignored) {
            // Audit is best effort and must never alter a wallet transaction.
        }
    }
}
