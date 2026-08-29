package dev.ua.ikeepcalm.wiic.utils;

import dev.ua.ikeepcalm.coi.api.audit.AuditEmission;
import dev.ua.ikeepcalm.coi.api.audit.AuditOutcome;
import dev.ua.ikeepcalm.coi.api.audit.AuditPrivacy;
import dev.ua.ikeepcalm.coi.api.audit.AuditRisk;
import dev.ua.ikeepcalm.coi.api.audit.MysterriaAudit;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Optional, non-blocking bridge to the shared Mysterria audit ledger. */
public final class MysterriaAuditBridge {
    public static final String ITEM_UUID_KEY = "item_uuid";
    public static final String PARENT_ITEM_UUID_KEY = "parent_item_uuid";
    private static final NamespacedKey ITEM_UUID_PDC =
            new NamespacedKey("circleofimagination", "item_uuid");
    private static final NamespacedKey PARENT_ITEM_UUID_PDC =
            new NamespacedKey("circleofimagination", "item_parent_uuid");

    private MysterriaAuditBridge() {
    }

    /** Correlates every step of one attempt while retaining a stable domain-facing identifier. */
    public record AuditIdentity(UUID correlationId, String businessId) {
        public AuditIdentity {
            if (correlationId == null) throw new IllegalArgumentException("correlationId is required");
            if (businessId == null || businessId.isBlank()) throw new IllegalArgumentException("businessId is required");
        }
    }

    public static AuditIdentity randomIdentity(String domain) {
        UUID correlationId = UUID.randomUUID();
        return new AuditIdentity(correlationId, "wiic:" + domain + ":" + correlationId);
    }

    public static AuditIdentity identity(String domain, UUID id) {
        return new AuditIdentity(id, "wiic:" + domain + ":" + id);
    }

    public static AuditIdentity identity(String domain, String id) {
        UUID correlationId;
        try {
            correlationId = UUID.fromString(id);
        } catch (IllegalArgumentException invalidUuid) {
            correlationId = UUID.nameUUIDFromBytes((domain + ":" + id).getBytes(StandardCharsets.UTF_8));
        }
        return new AuditIdentity(correlationId, "wiic:" + domain + ":" + id);
    }

    public static void emitWallet(String operation, Player player, ItemStack item,
                                  long amount, boolean success, BigDecimal before,
                                  BigDecimal after, AuditIdentity identity) {
        if (player == null || item == null) return;
        Map<String, Object> metadata = moneyMetadata(operation.equals("withdrawn") ? -amount : amount,
                before, after, itemMetadata(item));
        metadata.put("success", success);
        emit("wallet." + operation, success, player.getUniqueId(), player.getUniqueId(), null,
                identity, success ? null : operation + " failed", metadata);
    }

    /** Generic WIIC event helper. All callers pass only immutable values. */
    public static void emit(String operation, AuditOutcome outcome, UUID actorId,
                            UUID subjectId, UUID targetId, AuditIdentity identity,
                            String reason, Map<String, ?> metadata) {
        try {
            MysterriaAudit audit = Bukkit.getServicesManager().load(MysterriaAudit.class);
            if (audit == null) return;
            Map<String, Object> copy = new LinkedHashMap<>();
            if (metadata != null) metadata.forEach(copy::put);
            audit.emit(new AuditEmission(
                    "mysterria-wiiconomy." + operation,
                    outcome,
                    AuditRisk.NORMAL,
                    AuditPrivacy.STAFF_RESTRICTED,
                    identity.correlationId(),
                    identity.businessId(),
                    actorId,
                    subjectId,
                    targetId,
                    reason,
                    Map.copyOf(copy)));
        } catch (RuntimeException | LinkageError ignored) {
            // Audit is best effort and must never alter WIIC behavior.
        }
    }

    public static void emit(String operation, boolean success, UUID actorId,
                            UUID subjectId, UUID targetId, AuditIdentity identity,
                            String reason, Map<String, ?> metadata) {
        emit(operation, success ? AuditOutcome.COMMITTED : AuditOutcome.FAILED,
                actorId, subjectId, targetId, identity, reason, metadata);
    }

    /** Standard indexed monetary projection. Delta is signed from the subject's perspective. */
    public static Map<String, Object> moneyMetadata(long delta, BigDecimal before, BigDecimal after,
                                                     Map<String, ?> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("currency", "coppets");
        metadata.put("amount", Math.abs(delta));
        metadata.put("delta", delta);
        if (before != null) metadata.put("balance_before", before);
        if (after != null) metadata.put("balance_after", after);
        if (extra != null) extra.forEach(metadata::put);
        return metadata;
    }

    public static Map<String, Object> moneyMetadata(long delta, Map<String, ?> extra) {
        return moneyMetadata(delta, null, null, extra);
    }

    /** Bounded item projection, including canonical physical UUID keys when present. */
    public static Map<String, Object> itemMetadata(ItemStack item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (item == null) return metadata;
        metadata.put("material", item.getType().name().toLowerCase());
        metadata.put("item_amount", item.getAmount());
        if (!item.hasItemMeta()) return metadata;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        copyString(pdc.get(ITEM_UUID_PDC, PersistentDataType.STRING), ITEM_UUID_KEY, metadata);
        copyString(pdc.get(PARENT_ITEM_UUID_PDC, PersistentDataType.STRING), PARENT_ITEM_UUID_KEY, metadata);
        return metadata;
    }

    public static Map<String, Object> itemMetadata(byte[] itemBytes) {
        if (itemBytes == null) return Map.of();
        try {
            return itemMetadata(ItemStack.deserializeBytes(itemBytes));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    public static Map<String, Object> metadata(Map<String, ?> first, Map<String, ?> second) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (first != null) first.forEach(merged::put);
        if (second != null) second.forEach(merged::put);
        return merged;
    }

    private static void copyString(String value, String key, Map<String, Object> target) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
