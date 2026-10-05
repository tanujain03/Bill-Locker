# Sample bills for testing Gmail import

Five made-up documents for testing (fictional shops, and each one says **SAMPLE**).
Email them to the Gmail account you connected, or send them to yourself, then click
**Scan now** on the Gmail Import page.

## The emails to send

| # | Subject | Text of the email | Attach |
|---|---|---|---|
| 1 | Your tax invoice LE/2026-27/01842 from Lakeview Electronics | Thank you for shopping with us. Your tax invoice is attached. | `01-tax-invoice-laptop.pdf` |
| 2 | Order confirmation: your ShopNest order 405-1234567-7654321 | Your order has been delivered. The invoice for your order is attached. | `02-order-invoice-refrigerator.pdf` |
| 3 | Warranty registration – your LG warranty card | Keep this warranty card safe. Scan its QR code to register. | `03-warranty-card-ac.png` |
| 4 | Service receipt for your washing machine | Thanks for choosing QuickFix. Your service receipt is attached. | `04-service-receipt-washing-machine.jpg` |
| 5 | Big festive sale – our new catalogue | Show this catalogue at the store and save on your bill. | `05-festive-sale-catalogue.pdf` |
| 6 | Your electricity bill is ready | Pay before the 15th. | *nothing* |

Why the wording matters: the scan only looks at emails from the last year that have
an attachment **and** mention invoice, receipt, bill, "order confirmation" or
warranty (`GmailScanWorker.QUERY`).

## What should happen

**After "Scan now", the shortlist has emails 1–5.** Email 6 has no file, so it isn't
kept. Each email gets a type and a score from `EmailClassifier`:

| # | Type | Score | In "Import all"? |
|---|---|---|---|
| 1 | Invoice | 95% | yes |
| 2 | Invoice | 95% | yes |
| 3 | Warranty card | 85% | yes |
| 4 | Service receipt | 80% | yes |
| 5 | Other (a sale, not a bill) | 15% | no |

The bell shows **"New bills found in Gmail — Found 4 bills in your inbox."**

**After importing, each bill is read.** This is checked with Bill Locker's own reader:

| File | What the review screen shows |
|---|---|
| 01 laptop invoice (PDF + GST e-invoice QR) | Dell Inspiron 15 Laptop · Dell · model I3530-7XK2 · serial DL3530X7K2P91 · 28 Sep 2026 · ₹62,990 · Lakeview Electronics, Bengaluru · invoice LE/2026-27/01842 · 1 year. **Codes:** GST e-invoice QR |
| 02 refrigerator order (PDF) | Samsung 253 L … Refrigerator (RT28C3053S8) · Samsung · 15 Sep 2026 · ₹24,990 · ShopNest Retail Private Ltd · invoice SN-DEL-77821 (not the order number) · 1 year |
| 03 warranty card (PNG photo) | LG 1.5 Ton 5 Star Inverter Split AC · LG · PS-Q19YNZE · serial 305KAXY4M512 (exact, from the barcode) · 3 Aug 2026 · CoolAir Appliances, Pune · 1 year. **Codes:** a link (example.com) and the serial barcode |
| 04 service receipt (JPG photo) | Service receipt · Bosch Front Load Washing Machine · Bosch · WAJ2416WIN · 22 Sep 2026 · ₹1,850 |
| 05 sale catalogue (PDF) | Type "Other", no details. Correct: it isn't a bill |
