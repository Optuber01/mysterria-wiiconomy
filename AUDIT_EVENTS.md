# WIIC audit events

WIIC emits best-effort events to the shared `MysterriaAudit` service. Events are emitted
after the owning operation has a final result; a missing or failing provider never changes
gameplay. Existing `TransactionLogger` text records remain enabled during the review window
and are the operational fallback until parity is confirmed.

## Event contract

All events use the `mysterria-wiiconomy.` namespace and `STAFF_RESTRICTED` privacy. Every
emitted operation event has a UUID `correlationId` and a separate stable string `businessId`.
Result, failure, refund, courier handoff, and recovery events reuse both values when the
operation reaches an auditable point; preflight rejections may be intentionally sampled or
omitted to protect the main thread and audit sink.
Metadata is bounded and contains only immutable primitives captured at the commit point.

Monetary events use the indexed keys `currency=coppets`, unsigned `amount`, signed `delta`,
`balance_before`, and `balance_after` whenever both balances are observable. Other metadata
uses snake_case. Item projections use `material` and `item_amount`; when present, physical
identity is copied under the canonical indexed top-level keys `item_uuid` and
`parent_item_uuid`. Raw serialized item bytes are never emitted.

Balance fields are best-effort observations around the external economy call, not an atomic
transaction boundary. The signed `delta` records what WIIC attempted or completed and is the
authoritative monetary projection when concurrent economy activity changes either snapshot.

`plots.rent.charge_pending` is an `ATTEMPTED` observation of a debit that already succeeded
in Vault but is still waiting for the plot-row commit. Consumers should use the final
committed, failed, or refunded outcome for settlement and retain the pending row for crash
reconciliation rather than counting the same debit twice.

## Coverage

| Event | Commit/failure point | Main metadata |
| --- | --- | --- |
| `wallet.deposited`, `wallet.withdrawn`, `wallet.sold` | Vault wallet operation result | monetary projection, item projection, success |
| `shop.purchased`, `shop.refunded` | Admin shop charge/delivery/refund | monetary projection, material, item_amount, attempted_total when declined, outcome reason |
| `agora.listing.created`, `agora.listing.cancelled` | Listing DB commit | listing_id, price, fee, item projection, plot_id |
| `agora.listing.failed`, `agora.listing.fee_refunded` | Listing validation/charge/insert failure | price, fee, listing/request id, reason |
| `agora.purchase.completed`, `agora.purchase.failed`, `agora.purchase.refunded` | Market reservation, sale commit, and refund | monetary projection, listing_id, tax, net, item projection |
| `agora.purchase.recovered`, `agora.purchase.recovery_refunded`, `agora.purchase.recovery_unproven` | Journal recovery result | original purchase identity, monetary projection, listing_id |
| `agora.listing.item_recovered` | Interrupted listing item restored to stash | listing_id, item projection |
| `courier.contract.created`, `courier.contract.withdrawn` | Courier horn escrow mutation | courier_type, item projection |
| `courier.contract.failed`, `courier.contract.withdraw_failed` | Courier contract errors | reason |
| `courier.delivery.dispatched`, `courier.delivery.failed` | Stash claim and postman handoff | purchase identity, stash_id, seller, item projection, fee, courier_type |
| `courier.fee.collected`, `courier.fee.failed` | Optional delivery fee result | purchase identity, monetary projection, fee |
| `stash.deposited`, `stash.deposit_failed` | Stash row insertion | stash id, source, reference, item projection |
| `stash.claimed` | Stash claim batch result | delivered, remaining, claimed_ids |
| `ledger.claimed`, `ledger.claim_pending_recovery`, `ledger.claim_failed`, `ledger.claim_recovered`, `ledger.claim_reverted` | Proceeds deposit/claim/recovery result | original claim identity, monetary projection |
| `plots.rent.charge_pending`, `plots.rent.committed`, `plots.rent.failed` | Plot rent charge and DB claim | monetary projection, plot_id, paid_until, reason |
| `plots.rent.charge_pending`, `plots.rent.upkeep`, `plots.rent.upkeep_failed` | Plot extension charge and DB update | monetary projection, plot_id, paid_until |
| `plots.rent.refunded` | Failed rent/upkeep refund | original rent/upkeep identity, monetary projection, plot_id, operation, reason |
| `plots.eviction.committed`, `plots.eviction.failed` | Eviction DB commit | plot_id, harvested_stacks, material totals, bounded item summaries, reason |
| `plot_shop.created`, `plot_shop.create_failed` | Stall counter DB insert | plot_id, price, bundle |
| `plot_shop.updated`, `plot_shop.update_failed` | Stall goods/price mutation | plot_id, price, stocked, item projection |
| `plot_shop.purchase_completed`, `plot_shop.purchase_failed` | Stall charge, stock, and ledger result | purchase identity, monetary projection, plot_id, quantity, tax, net, item projection |
| `plot_shop.ledger_failed`, `plot_shop.refunded` | Stall ledger failure/refund | purchase identity, monetary projection, plot_id, shop_id, net, reason |

An Agora purchase uses its journal attempt ID as the business identity, while `listing_id`
remains the purchased resource. WIIC courier events retain that purchase identity and expose
the stash row separately as `stash_id`, allowing downstream Delivery emitters to reuse the
same business ID without conflating a purchase, listing, and stash record.
