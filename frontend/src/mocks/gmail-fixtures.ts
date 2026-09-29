import type { DbGmailMessage } from './db';
import { newId } from './utils';

/** Emails the mock "finds" on the first Gmail scan (fictional order data). */
export function createGmailMessages(userId: string, now = new Date()): DbGmailMessage[] {
  const at = (daysBack: number, hour: number) => {
    const date = new Date(now);
    date.setDate(date.getDate() - daysBack);
    date.setHours(hour, 12, 0, 0);
    return date.toISOString();
  };

  const messages: Omit<DbGmailMessage, 'id' | 'userId' | 'status' | 'documentIds'>[] = [
    {
      fromName: 'Amazon.in',
      fromEmail: 'auto-confirm@amazon.in',
      subject: 'Your Amazon.in order of Sony WH-1000XM5 Wireless Headphones',
      snippet: 'Thank you for shopping with us. The tax invoice for order #408-5561234-9912 is attached to this email.',
      receivedAt: at(2, 9),
      attachments: [{ fileName: 'Invoice_408-5561234-9912.pdf', mimeType: 'application/pdf', size: 84_213 }],
      detectedType: 'INVOICE',
      confidence: 0.97,
      templateKey: 'sony-wh1000xm5',
    },
    {
      fromName: 'Flipkart',
      fromEmail: 'no-reply@flipkart.com',
      subject: 'Tax invoice for your order: Bajaj Majesty DX-11 Dry Iron',
      snippet: 'Your order has been delivered. Please find the tax invoice attached for your records.',
      receivedAt: at(5, 18),
      attachments: [{ fileName: 'FAWRX2608871.pdf', mimeType: 'application/pdf', size: 61_902 }],
      detectedType: 'INVOICE',
      confidence: 0.94,
      templateKey: 'bajaj-iron',
    },
    {
      fromName: 'ElectroMart Online',
      fromEmail: 'invoices@electromart-online.in',
      subject: 'Invoice #EM-88213 — Samsung Galaxy Watch6 Classic',
      snippet: 'Hi! Thanks for your purchase. Your invoice and warranty details are attached.',
      receivedAt: at(9, 13),
      attachments: [{ fileName: 'EM-88213.pdf', mimeType: 'application/pdf', size: 102_557 }],
      detectedType: 'INVOICE',
      confidence: 0.93,
      templateKey: 'galaxy-watch',
    },
    {
      fromName: 'CoolAir Solutions',
      fromEmail: 'service@coolair-solutions.in',
      subject: 'Service receipt — AC maintenance visit',
      snippet: 'Your Voltas split AC was serviced today: filter cleaning and gas pressure check. Receipt attached.',
      receivedAt: at(16, 16),
      attachments: [{ fileName: 'CAS-SRV-2291.pdf', mimeType: 'application/pdf', size: 45_310 }],
      detectedType: 'SERVICE_RECEIPT',
      confidence: 0.88,
      templateKey: 'ac-service',
    },
    {
      fromName: 'Sony India',
      fromEmail: 'warranty@sony-care.in',
      subject: 'Your extended warranty is active — BRAVIA KD-55X74L',
      snippet: 'Your extended warranty registration is complete. Your TV is now covered for 3 years from purchase.',
      receivedAt: at(21, 11),
      attachments: [],
      detectedType: 'WARRANTY_CARD',
      confidence: 0.91,
      templateKey: 'sony-extended-warranty',
    },
    {
      fromName: 'Swiggy',
      fromEmail: 'noreply@swiggy.in',
      subject: 'Your order from Meghana Foods was delivered',
      snippet: 'Hope you enjoyed your meal! Order total ₹642. Rate your experience.',
      receivedAt: at(30, 21),
      attachments: [],
      detectedType: 'OTHER',
      confidence: 0.22,
      templateKey: 'food-order',
    },
  ];

  return messages.map((message) => ({ ...message, id: newId('gm'), userId, status: 'NEW', documentIds: [] }));
}
