# Feature Specification: Multi-warehouse inventory service

**Feature branch**: `001-inventory-service`
**Created**: 2026-11-04
**Status**: Implemented
**Input**: "A growing online retailer sells from three warehouses. Their storefront, warehouse scanners and ERP each keep their own stock numbers, so they oversell during sales and nobody trusts the counts. They want one inventory service that every system calls: it tracks stock per warehouse, holds stock for orders at checkout, and never sells what isn't there."

## Clarifications

### Session 2026-11-04
- Q: How long does a checkout hold last before it expires? → A: 15 minutes by default; the caller may choose 1 to 120 minutes.
- Q: Can one hold cover several products in the same order? → A: Yes. A hold has one or more lines in one warehouse and succeeds or fails as a whole.
- Q: Are transfers in transit for a while, or instant? → A: Instant in this version. In-transit stock is out of scope.
- Q: How long are idempotency keys remembered? → A: 24 hours, per calling client.
- Q: Who may record count corrections? → A: Any caller with write permission, and a reason is required.

## User scenarios and testing *(mandatory)*

### User story 1: Know what can be sold (Priority: P1)
The storefront asks how many units of a product are available, in total and per warehouse, before showing "In stock".

**Why this priority**: Every other story depends on trustworthy numbers.

**Independent test**: Receive 10 units of a product into one warehouse and see on-hand 10, reserved 0, available 10.

**Acceptance scenarios**
1. **Given** a product with stock in two warehouses, **when** availability is requested, **then** the response shows on-hand, reserved and available per warehouse and in total.
2. **Given** an unknown product code, **when** availability is requested, **then** the service returns not found.

### User story 2: Record stock changes (Priority: P1)
Warehouse systems record goods received, goods shipped, transfers between warehouses and count corrections. Each change is kept as a permanent record.

**Independent test**: Receive 10, ship 3, and see on-hand 7 with two entries in the product's history.

**Acceptance scenarios**
1. **Given** 7 on hand, **when** a shipment of 9 is recorded, **then** it is rejected and nothing changes.
2. **Given** a transfer of 4 from warehouse A to B, **then** A drops by 4 and B rises by 4 together, or neither changes.
3. **Given** a network retry of the same request, **then** the change is applied once and the retry gets the original answer.

### User story 3: Hold stock at checkout (Priority: P1)
When a shopper checks out, the storefront holds the units for their order. The hold is turned into a shipment when the order ships, released if the order is cancelled, and released automatically if the shopper never pays.

**Independent test**: With 5 available, hold 2 for order A; available becomes 3. Confirm the hold; on-hand becomes 3 and the hold is gone.

**Acceptance scenarios**
1. **Given** 5 available and 10 shoppers each trying to hold 1 at the same moment, **then** exactly 5 holds succeed and 5 are told the item is out of stock.
4. **Given** an order with two products where the second is out of stock, **when** a hold is requested, **then** nothing is held for either product.
2. **Given** a hold that is not confirmed in time, **then** it expires and the units become available again.
3. **Given** a confirmed or released hold, **when** someone tries to confirm or release it again, **then** they are told it is no longer active.

### User story 4: Restock before running out (Priority: P2)
Buyers see which products have fallen to or below their reorder point.

**Acceptance scenarios**
1. **Given** a product with reorder point 5 and 4 available in total, **then** it appears on the low-stock list with the shortfall.

### Edge cases
- Quantities must be positive whole numbers. Count corrections may be negative but cannot take on-hand below what is held.
- A product code is unique and case-insensitive.
- A hold for more than is available in the chosen warehouse fails; the service does not split a hold across warehouses.

## Requirements *(mandatory)*

### Functional requirements
- **FR-001**: Manage products (code, name, reorder point) and warehouses (code, name).
- **FR-002**: Report on-hand, reserved and available stock per product, per warehouse and in total.
- **FR-003**: Record receive, ship, transfer and adjust movements in an append-only ledger with who, when and why.
- **FR-004**: The system MUST NOT let on-hand or available stock go below zero, under any concurrency.
- **FR-005**: Create multi-line holds in one warehouse (all or nothing), confirm them into shipments, release them, and expire unconfirmed holds after 15 minutes by default (1 to 120 allowed).
- **FR-006**: All writes accept an idempotency key, remembered for 24 hours per client.
- **FR-007**: List products at or below their reorder point.
- **FR-008**: Callers authenticate; reading and writing are separate permissions.
- **FR-009**: Errors are machine-readable and say what went wrong.

### Key entities
- **Product**: code, name, reorder point.
- **Warehouse**: code, name.
- **Stock level**: on-hand and reserved for one product in one warehouse.
- **Movement**: an immutable record of one change to on-hand stock.
- **Hold**: units reserved for an order in one warehouse, with one or more lines, a status and an expiry.

## Success criteria *(mandatory)*
- **SC-001**: Zero oversells in a concurrency test of simultaneous holds, run on every build.
- **SC-002**: Current stock equals the sum of the ledger for every product, checked by a test.
- **SC-003**: Availability answers in under 50 ms at the 95th percentile on a small database.
- **SC-004**: Every documented response matches the published API contract, checked on every build.
