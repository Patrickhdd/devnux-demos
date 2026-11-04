import type { Db } from "./db.js";
import { withTransaction } from "./db.js";

const DAY = 24 * 60 * 60 * 1000;

const articles: Array<[string, string, string]> = [
  ["returns-and-refunds", "Returns and refunds",
    "You can return unused items within 30 days of delivery for a full refund to your original payment method. " +
    "Start a return from chat or your account. Once the return is approved we email a prepaid UPS label. " +
    "Refunds are issued within 5 to 7 business days after the item reaches our warehouse. " +
    "Items used outdoors, final-sale items and gift cards cannot be returned. Orders past 30 days can be reviewed by our team as an exception."],
  ["shipping-times", "Shipping times and costs",
    "Standard shipping is free on orders over $75 and takes 3 to 6 business days within the contiguous United States. " +
    "Expedited shipping takes 2 business days and costs $14.95. We ship Monday to Friday from Portland, Oregon. " +
    "We do not currently ship to P.O. boxes, Alaska, Hawaii or outside the United States."],
  ["track-an-order", "Tracking your order",
    "When your order ships we email a tracking link. Tracking can take up to 24 hours to show the first scan. " +
    "If tracking has not updated for 5 business days, contact us and we will open a trace with the carrier."],
  ["cancel-or-change", "Cancelling or changing an order",
    "Orders can be cancelled while they are still processing, usually within 2 hours of being placed. " +
    "Once an order has shipped it cannot be cancelled, but you can return it after delivery. " +
    "To change a shipping address, contact us before the order ships."],
  ["warranty", "Warranty",
    "Harbor & Pine gear carries a one-year warranty against manufacturing defects such as broken zippers, seam failures and delamination. " +
    "Damage from normal wear, accidents or misuse is not covered. Warranty claims are reviewed by our support team and need a photo of the issue."],
  ["damaged-or-wrong-item", "Damaged or wrong item received",
    "If an item arrives damaged or is not what you ordered, tell us within 14 days of delivery. " +
    "We will send a replacement or a full refund, and you will not need to pay for return shipping."],
  ["sizing", "Sizing help",
    "Our jackets run true to size with room for a mid layer. Boots run half a size small, so we suggest ordering half a size up. " +
    "Size charts are on every product page."],
];

const products: Array<[string, string, number]> = [
  ["HP-JKT-RAIN", "Cascade rain jacket", 18900],
  ["HP-BOOT-TRL", "Ridgeline trail boots", 15900],
  ["HP-PACK-28", "Summit 28L daypack", 11900],
  ["HP-BTL-1L", "Insulated bottle, 1 L", 3400],
  ["HP-TENT-2P", "Basecamp 2-person tent", 32900],
  ["HP-SOCK-3", "Merino hiking socks, 3 pack", 4200],
];

/** Creates demo data relative to "now" so return windows stay realistic. Runs once. */
export async function seedDemoData(db: Db, now = new Date()): Promise<void> {
  const { rows } = await db.query("SELECT count(*)::int AS n FROM customer");
  if (rows[0].n > 0) return;

  const at = (daysAgo: number) => new Date(now.getTime() - daysAgo * DAY);

  await withTransaction(db, async (c) => {
    for (const [slug, title, body] of articles) {
      await c.query("INSERT INTO help_article (slug, title, body) VALUES ($1, $2, $3)", [slug, title, body]);
    }
    const productId: Record<string, number> = {};
    for (const [sku, name, price] of products) {
      const r = await c.query("INSERT INTO product (sku, name, price_cents) VALUES ($1, $2, $3) RETURNING id", [sku, name, price]);
      productId[sku] = r.rows[0].id;
    }
    const customer = async (email: string, name: string) =>
      (await c.query("INSERT INTO customer (email, full_name) VALUES ($1, $2) RETURNING id", [email, name])).rows[0].id as number;

    const jordan = await customer("jordan.rivera@example.com", "Jordan Rivera");
    const taylor = await customer("taylor.brooks@example.com", "Taylor Brooks");

    const order = async (o: {
      number: string; customerId: number; status: string; placed: number; shipped?: number; delivered?: number;
      carrier?: string; tracking?: string; shipTo: string; items: Array<[string, number]>;
    }) => {
      const r = await c.query(
        `INSERT INTO shop_order (order_number, customer_id, status, placed_at, shipped_at, delivered_at, carrier, tracking_code, ship_to)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id`,
        [o.number, o.customerId, o.status, at(o.placed), o.shipped == null ? null : at(o.shipped),
         o.delivered == null ? null : at(o.delivered), o.carrier ?? null, o.tracking ?? null, o.shipTo]);
      for (const [sku, qty] of o.items) {
        const price = products.find((p) => p[0] === sku)![2];
        await c.query("INSERT INTO order_item (order_id, product_id, quantity, price_cents) VALUES ($1, $2, $3, $4)",
          [r.rows[0].id, productId[sku], qty, price]);
      }
    };

    const jordanAddress = "418 Alder St, Portland, OR 97205";
    await order({ number: "HP-10482", customerId: jordan, status: "delivered", placed: 14, shipped: 13, delivered: 10,
      carrier: "UPS", tracking: "1Z84A9E60312345678", shipTo: jordanAddress, items: [["HP-BOOT-TRL", 1], ["HP-SOCK-3", 1]] });
    await order({ number: "HP-10391", customerId: jordan, status: "delivered", placed: 52, shipped: 51, delivered: 47,
      carrier: "UPS", tracking: "1Z84A9E60398765432", shipTo: jordanAddress, items: [["HP-JKT-RAIN", 1]] });
    await order({ number: "HP-10517", customerId: jordan, status: "shipped", placed: 3, shipped: 2,
      carrier: "USPS", tracking: "9400111202555842761234", shipTo: jordanAddress, items: [["HP-PACK-28", 1], ["HP-BTL-1L", 2]] });
    await order({ number: "HP-10533", customerId: jordan, status: "processing", placed: 0.04,
      shipTo: jordanAddress, items: [["HP-TENT-2P", 1]] });

    await order({ number: "HP-10455", customerId: taylor, status: "delivered", placed: 20, shipped: 19, delivered: 16,
      carrier: "FedEx", tracking: "772899104512", shipTo: "77 Lakeview Dr, Austin, TX 78701", items: [["HP-TENT-2P", 1]] });
  });
}
