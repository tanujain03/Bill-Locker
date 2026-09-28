# 🔐 Bill Locker

###  Personal Purchase, Warranty & Service Manager

> **Never lose a bill. Never miss a warranty. And you don't even have to upload the bill.**

Bill Locker is an AI-powered personal purchase management platform that automatically collects, understands, organizes, and manages a user's purchase information, invoices, warranties, and service records.

Instead of forcing users to manually upload every bill, Bill Locker can connect with their **Gmail account** and identify purchase-related emails, invoices, receipts, and order confirmations. AI then extracts useful information from those emails and documents and creates structured purchase records.

Users can also manually upload bills when required.

The goal is to turn scattered purchase information into a single intelligent **digital locker for everything a user owns**.

---

# 📌 Table of Contents

* [Project Overview](#-project-overview)
* [Problem Statement](#-problem-statement)
* [Solution](#-solution)
* [Key Features](#-key-features)
* [Core User Flow](#-core-user-flow)
* [System Architecture](#-system-architecture)
* [Technology Stack](#-technology-stack)
* [Database Design](#-database-design)
* [Development Roadmap](#-development-roadmap)
* [Phase 1 — Project Foundation](#phase-1--project-foundation)
* [Phase 2 — Authentication](#phase-2--authentication)
* [Phase 3 — Product Management](#phase-3--product-management)
* [Phase 4 — Document Management](#phase-4--document-management)
* [Phase 5 — OCR & AI Extraction](#phase-5--ocr--ai-extraction)
* [Phase 6 — Warranty Management](#phase-6--warranty-management)
* [Phase 7 — Gmail Integration](#phase-7--gmail-integration)
* [Phase 8 — Purchase Inbox](#phase-8--purchase-inbox)
* [Phase 9 — Service Management](#phase-9--service-management)
* [Phase 10 — Notifications](#phase-10--notifications)
* [Phase 11 — AI Assistant & RAG](#phase-11--ai-assistant--rag)
* [Phase 12 — Product Lifecycle](#phase-12--product-lifecycle)
* [Phase 13 — Warranty Claim Assistant](#phase-13--warranty-claim-assistant)
* [Phase 14 — Search](#phase-14--natural-language-search)
* [Phase 15 — Security](#phase-15--security)
* [Phase 16 — Testing](#phase-16--testing)
* [Phase 17 — Docker & Deployment](#phase-17--docker--deployment)
* [Demo Flow](#-final-hackathon-demo-flow)
* [Future Improvements](#-future-improvements)

---

# 🚀 Project Overview

People purchase many products throughout the year:

* Mobile phones
* Laptops
* TVs
* Refrigerators
* Washing machines
* Air conditioners
* Headphones
* Printers
* Furniture
* Appliances

However, their purchase information is usually scattered across:

* Gmail
* E-commerce websites
* PDF invoices
* WhatsApp
* Phone galleries
* Downloads folders
* Physical bills
* Warranty cards

As a result, users often:

* Lose invoices
* Forget warranty expiry dates
* Miss service dates
* Cannot find serial numbers
* Don't remember where they purchased a product
* Spend time searching old emails for invoices
* Don't know which products are currently under warranty

Bill Locker solves this problem by creating a centralized digital locker for the user's purchases.

---

# ❗ Problem Statement

The traditional process looks like:

```text
Purchase Product
       ↓
Receive Email / Bill
       ↓
Forget About It
       ↓
Warranty Period Passes
       ↓
Product Stops Working
       ↓
Search Thousands of Emails
       ↓
Try to Find Invoice
       ↓
Try to Find Warranty Card
```

Bill Locker changes this into:

```text
Purchase Product
       ↓
Bill Locker Detects Purchase
       ↓
AI Extracts Details
       ↓
Purchase Added to Locker
       ↓
Warranty Automatically Calculated
       ↓
Service Dates Tracked
       ↓
User Gets Reminders
       ↓
Invoice & Warranty Available When Needed
```

---

# 💡 Solution

Bill Locker provides multiple ways to add purchases.

### Method 1 — Gmail Auto Import

```text
Connect Gmail
      ↓
Search purchase-related emails
      ↓
Find invoices / receipts / order emails
      ↓
Extract information
      ↓
AI processes information
      ↓
User reviews
      ↓
Add to Locker
```

### Method 2 — Manual Upload

Users can upload:

* PDF
* JPG
* JPEG
* PNG

Bill Locker then:

```text
Upload
  ↓
OCR
  ↓
Extract Text
  ↓
AI
  ↓
Structured Information
  ↓
User Confirmation
  ↓
Product Created
```

### Method 3 — Manual Product Entry

Users can also enter product information manually.

---

# ⭐ Key Features

## 1. Gmail Auto Import

Users can connect their Gmail account.

Bill Locker searches for relevant purchase emails and identifies:

* Order confirmations
* Invoices
* Tax invoices
* Receipts
* Payment confirmations
* Delivery confirmations
* Warranty-related emails

The system should not automatically create purchases without user verification.

Instead, detected purchases go into the **Purchase Inbox**.

---

## 2. AI Bill Scanner

Upload a bill or invoice.

AI extracts:

```text
Product Name
Brand
Model
Serial Number
Purchase Date
Purchase Price
Currency
Seller
Invoice Number
Warranty Period
```

---

## 3. Warranty Tracker

Every product can have a warranty.

Warranty status:

```text
ACTIVE
EXPIRING_SOON
EXPIRED
UNKNOWN
```

The system automatically calculates the warranty expiry date.

---

## 4. Warranty Reminders

Users receive reminders such as:

```text
⚠ Warranty expires in 30 days

⚠ Warranty expires in 7 days

❌ Warranty expired
```

---

## 5. Service Tracking

Users can record:

```text
Service Date
Service Type
Service Center
Cost
Notes
Next Service Date
```

---

## 6. Purchase Inbox

Automatically detected purchases appear here.

Example:

```text
3 New Purchases Found

📱 Apple iPhone
Found in Gmail
₹1,34,900
12 September 2026

[ Add to Locker ] [ Ignore ]
```

The user remains in control of what gets added.

---

## 7. Product Lifecycle

Each product has a timeline:

```text
Purchased
    ↓
Warranty Started
    ↓
Service
    ↓
Repair
    ↓
Warranty Expiry
```

---

## 8. AI Assistant

Users can ask questions such as:

```text
Which products are under warranty?

Which warranty expires soon?

Where did I buy my laptop?

What is my refrigerator invoice number?

How much did I spend on electronics?

Show products purchased this year.
```

---

## 9. Warranty Claim Assistant

Users can ask:

> My washing machine stopped working. Is it still under warranty?

Bill Locker checks the available product, purchase, warranty, and document information and provides a factual summary based on the stored information.

It should clearly indicate when information is missing and should not guarantee that a warranty claim will be accepted.

---

# 🔄 Core User Flow

```text
                    BILL LOCKER
                         │
             ┌───────────┴───────────┐
             │                       │
        Connect Gmail           Upload Bill
             │                       │
             ▼                       ▼
      Search Emails                OCR
             │                       │
             ▼                       ▼
      Find Purchase              Extract Text
             │                       │
             └───────────┬───────────┘
                         ▼
                   AI Extraction
                         │
                         ▼
                 User Verification
                         │
                         ▼
                  Product Created
                         │
             ┌───────────┼───────────┐
             ▼           ▼           ▼
         Warranty      Service    Documents
             │           │           │
             └───────────┼───────────┘
                         ▼
                    Notifications
                         │
                         ▼
                    AI Assistant
```

---

# 🏗 System Architecture

```text
┌────────────────────────────────────────────┐
│              React Frontend               │
│                                            │
│ Dashboard | Products | Documents           │
│ Warranties | Services | AI Assistant       │
│ Purchase Inbox | Notifications             │
└─────────────────────┬──────────────────────┘
                      │
                      │ REST API
                      ▼
┌────────────────────────────────────────────┐
│            Spring Boot Backend             │
│                                            │
│ Auth                                       │
│ Products                                   │
│ Documents                                  │
│ Warranty                                   │
│ Services                                   │
│ Notifications                              │
│ Gmail Integration                          │
│ AI Processing                              │
│ RAG                                        │
└───────────┬────────────┬────────────┬──────┘
            │            │            │
            ▼            ▼            ▼
       PostgreSQL      AI/OCR       Gmail API
            │
            ▼
       pgvector
            │
            ▼
       MinIO / S3
```

---

# 🛠 Technology Stack

## Frontend

* React
* TypeScript
* Vite
* Tailwind CSS
* React Router
* Axios
* TanStack Query
* React Hook Form
* Zod
* Recharts
* Lucide React

## Backend

* Java 21
* Spring Boot
* Spring Security
* JWT
* Spring Data JPA
* Hibernate
* Maven
* Bean Validation
* Flyway
* OpenAPI / Swagger

## Database

* PostgreSQL
* pgvector

## AI

* Gemini / configurable LLM provider
* OCR provider
* Embeddings
* Retrieval-Augmented Generation (RAG)

AI services should be implemented through interfaces so that the AI provider can be replaced later.

## File Storage

Development:

* MinIO

Production:

* S3-compatible storage

## External Integration

* Gmail API
* Google OAuth 2.0

## DevOps

* Docker
* Docker Compose
* Git / GitHub

---

# 🗄 Database Design

The main entities will be:

```text
users
products
documents
warranties
service_records
notifications
gmail_connections
imported_emails
document_chunks
chat_sessions
chat_messages
```

## users

```text
id
name
email
password_hash
created_at
updated_at
```

## products

```text
id
user_id
category_id
name
brand
model
serial_number
purchase_date
purchase_price
currency
seller
invoice_number
created_at
updated_at
```

## documents

```text
id
user_id
product_id
document_type
file_name
storage_key
mime_type
file_size
extracted_text
processing_status
created_at
updated_at
```

## warranties

```text
id
product_id
warranty_months
start_date
expiry_date
status
source_document_id
created_at
updated_at
```

## service_records

```text
id
product_id
service_date
service_type
service_center
cost
next_service_date
notes
created_at
updated_at
```

## gmail_connections

```text
id
user_id
google_account_email
encrypted_access_token
encrypted_refresh_token
token_expiry
created_at
updated_at
```

## imported_emails

```text
id
user_id
gmail_message_id
sender
subject
received_at
email_type
processing_status
created_at
```

---

# 🛣 Development Roadmap

The project will be developed incrementally.

**Do not implement all features at once.**

Each phase should be completed, tested, and committed before moving to the next phase.

---

# Phase 1 — Project Foundation

### Goal

Create the basic project structure.

### Tasks

```text
1. Create Git repository
2. Create frontend
3. Create backend
4. Setup PostgreSQL
5. Setup Docker
6. Setup Flyway
7. Create health endpoint
8. Create basic frontend
9. Connect frontend to backend
```

Expected result:

```text
React
   ↓
Spring Boot
   ↓
PostgreSQL
```

---

# Phase 2 — Authentication

### Goal

Allow users to securely register and login.

### Tasks

```text
1. Create users table
2. Registration API
3. Login API
4. Password hashing
5. JWT authentication
6. Protected APIs
7. Login page
8. Register page
9. Logout
```

Expected result:

```text
Register
   ↓
Login
   ↓
JWT
   ↓
Dashboard
```

---

# Phase 3 — Product Management

### Goal

Allow users to manage their products.

### Tasks

```text
1. Create products table
2. Product entity
3. Product repository
4. Product service
5. Product controller
6. CRUD APIs
7. Product list page
8. Add product page
9. Edit product
10. Delete product
11. Product details
```

---

# Phase 4 — Document Management

### Goal

Allow users to upload and store bills.

### Tasks

```text
1. Setup MinIO
2. File upload API
3. File validation
4. Store file metadata
5. Store actual file in MinIO
6. Download API
7. Delete API
8. Documents page
9. Upload UI
```

Supported files:

```text
PDF
JPG
JPEG
PNG
```

---

# Phase 5 — OCR & AI Extraction

### Goal

Automatically understand uploaded bills.

### Pipeline

```text
Bill
 ↓
OCR
 ↓
Extracted Text
 ↓
LLM
 ↓
Structured JSON
 ↓
Validation
 ↓
User Review
 ↓
Confirmation
```

### AI extraction fields

```text
Product Name
Brand
Model
Serial Number
Purchase Date
Price
Currency
Seller
Invoice Number
Warranty
```

### Important rule

AI must never invent missing information.

Missing values should be:

```text
null
```

or:

```text
NOT_FOUND
```

---

# Phase 6 — Warranty Management

### Goal

Automatically calculate and track warranties.

### Tasks

```text
1. Create warranty table
2. Create warranty service
3. Calculate expiry date
4. Determine warranty status
5. Warranty dashboard
6. Expiring warranty page
7. Product warranty details
```

Warranty calculation must be deterministic backend logic.

Example:

```text
Purchase Date:
12 September 2026

Warranty:
12 months

Expiry:
12 September 2027
```

---

# Phase 7 — Gmail Integration

### Goal

Allow users to automatically discover purchases without uploading bills.

### Flow

```text
User
 ↓
Connect Gmail
 ↓
Google OAuth
 ↓
Grant Permission
 ↓
Bill Locker receives authorization
 ↓
Search relevant emails
 ↓
Find purchase emails
 ↓
Process email / attachment
 ↓
AI extraction
 ↓
Purchase Inbox
```

### Email types

The system should look for:

```text
Invoice
Receipt
Order Confirmation
Payment Confirmation
Shipping Confirmation
Delivery Confirmation
Warranty Email
```

### Important

Do not download or process the user's entire mailbox.

Only process relevant messages required by the application.

---

# Phase 8 — Purchase Inbox

### Goal

Give users control over automatically discovered purchases.

Example:

```text
New Purchase Found

Product:
Samsung Refrigerator

Price:
₹65,000

Purchase Date:
20 August 2026

Source:
Gmail

[ Add to Locker ]

[ Ignore ]
```

### Tasks

```text
1. Create imported_emails table
2. Gmail message processing
3. Purchase detection
4. AI classification
5. Extract purchase information
6. Duplicate detection
7. Purchase Inbox UI
8. Add to Locker
9. Ignore purchase
```

---

# Phase 9 — Service Management

### Goal

Track product service and maintenance.

### Tasks

```text
1. Create service_records table
2. Add service API
3. Edit service
4. Delete service
5. Service history
6. Next service date
7. Service dashboard
```

---

# Phase 10 — Notifications

### Goal

Automatically remind users about important dates.

Examples:

```text
Warranty expires in 30 days
Warranty expires in 7 days
Warranty expired
Service due in 30 days
Service due in 7 days
```

Implement scheduled backend jobs.

---

# Phase 11 — AI Assistant & RAG

### Goal

Allow users to ask questions about their own documents and products.

Example questions:

```text
Which products are under warranty?

Which warranties expire this month?

What is my laptop invoice number?

Where did I buy my TV?

What does my warranty document say?

Show all electronics purchased this year.
```

### RAG Pipeline

```text
Documents
 ↓
Text extraction
 ↓
Chunking
 ↓
Embeddings
 ↓
pgvector
 ↓
User Question
 ↓
Similarity Search
 ↓
Relevant Documents
 ↓
LLM
 ↓
Answer
```

The AI must only retrieve information belonging to the authenticated user.

---

# Phase 12 — Product Lifecycle

### Goal

Represent the complete lifecycle of a product.

Example:

```text
Purchase
   ↓
Warranty Started
   ↓
Service
   ↓
Repair
   ↓
Warranty Expiry
```

Display this as a visual timeline on the product details page.

---

# Phase 13 — Warranty Claim Assistant

### Goal

Help users understand what information they have available for a potential warranty claim.

Example:

```text
User:

"My washing machine stopped working."
```

System checks:

```text
Product
Purchase Date
Warranty
Invoice
Warranty Document
Serial Number
Service History
```

Then provides a factual summary.

The system must clearly distinguish:

```text
Known Information
Missing Information
User Documents
Potential Next Steps
```

It must not guarantee that a warranty provider will approve a claim.

---

# Phase 14 — Natural Language Search

### Goal

Allow users to search their purchases naturally.

Example:

```text
"Show electronics purchased this year."

"Which products cost more than ₹50,000?"

"Show warranties expiring next month."
```

The backend should convert natural language into validated structured filters.

The LLM must never directly execute arbitrary SQL.

---

# Phase 15 — Security

Security requirements:

```text
✓ JWT authentication
✓ Password hashing
✓ User-specific database queries
✓ User-specific document access
✓ Secure file downloads
✓ OAuth token protection
✓ Input validation
✓ File type validation
✓ File size limits
✓ API authorization
```

One user's information must never be accessible to another user.

---

# Phase 16 — Testing

Test:

```text
Authentication
Products
Documents
OCR
AI extraction
Warranty calculation
Gmail import
Duplicate detection
Services
Notifications
AI assistant
User data isolation
```

Also test failure cases:

```text
Invalid bill
Unreadable image
Missing warranty
Missing purchase date
Duplicate invoice
Expired Gmail token
Gmail API failure
AI API failure
Unsupported file
```

---

# Phase 17 — Docker & Deployment

The final application should be easy to run.

Expected architecture:

```text
Docker Compose
│
├── Frontend
├── Backend
├── PostgreSQL
├── MinIO
└── pgvector
```

The goal is eventually:

```bash
docker compose up
```

to start the local development environment.

---

# 🔌 API Structure

## Authentication

```text
POST /api/auth/register
POST /api/auth/login
```

## Products

```text
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

## Documents

```text
POST   /api/documents/upload
GET    /api/documents
GET    /api/documents/{id}
GET    /api/documents/{id}/download
DELETE /api/documents/{id}
```

## AI Processing

```text
POST /api/documents/{id}/process
GET  /api/documents/{id}/extraction
POST /api/documents/{id}/confirm
```

## Warranty

```text
GET /api/warranties
GET /api/warranties/expiring
GET /api/products/{id}/warranty
```

## Services

```text
POST   /api/products/{id}/services
GET    /api/products/{id}/services
PUT    /api/services/{id}
DELETE /api/services/{id}
```

## Gmail

```text
GET  /api/gmail/connect
GET  /api/gmail/callback
POST /api/gmail/sync
GET  /api/gmail/status
POST /api/gmail/disconnect
```

## Purchase Inbox

```text
GET  /api/purchases/inbox
POST /api/purchases/{id}/confirm
POST /api/purchases/{id}/ignore
```

## AI Assistant

```text
POST /api/ai/chat
POST /api/ai/search
```

---

# 📱 Frontend Pages

The application should contain:

```text
/
├── Landing Page
│
├── /login
│
├── /register
│
├── /dashboard
│
├── /products
│
├── /products/:id
│
├── /documents
│
├── /warranties
│
├── /services
│
├── /purchase-inbox
│
├── /assistant
│
├── /notifications
│
└── /settings
```

---

# 🎨 UI Design

The application should use a modern SaaS dashboard design.

Sidebar:

```text
🏠 Dashboard

📦 My Products

📄 Documents

🛡 Warranties

🔧 Services

📥 Purchase Inbox

🤖 AI Assistant

🔔 Notifications

⚙ Settings

🚪 Logout
```

Dashboard cards:

```text
Total Products
Total Spending
Active Warranties
Expiring Soon
Expired
```

---

# 🤖 AI Design Principles

AI should be used where it provides real value.

Use AI for:

```text
✓ Document understanding
✓ Invoice extraction
✓ Email classification
✓ Purchase detection
✓ Natural language search
✓ Document Q&A
✓ Warranty document understanding
```

Do NOT use AI for deterministic operations such as:

```text
✗ Warranty date arithmetic
✗ User authorization
✗ Database access control
✗ Authentication
✗ Permission checks
```

Those should be handled by normal backend logic.

---

# 🧠 AI Extraction Rules

The AI must:

1. Never invent information.
2. Return structured JSON.
3. Return `null` for missing values.
4. Preserve dates accurately.
5. Preserve currency and price accurately.
6. Distinguish invoice number from order number.
7. Distinguish serial number from model number.
8. Provide confidence information where useful.
9. Allow human verification.
10. Never directly modify the database without backend validation.

---

# 🔐 Gmail Security Principles

Gmail integration must use OAuth 2.0.

The application should:

```text
✓ Request minimum required permissions
✓ Secure OAuth tokens
✓ Allow Gmail disconnection
✓ Avoid scanning unrelated emails
✓ Process only relevant messages
✓ Keep Gmail data associated with the correct user
```

Users should always know that Gmail integration is enabled.

---

# 🧪 Development Rules

The project should be developed incrementally.

### Rule 1

Never implement multiple major phases simultaneously.

### Rule 2

After every phase:

```text
Build
 ↓
Run
 ↓
Test
 ↓
Fix errors
 ↓
Git commit
```

### Rule 3

Never delete working functionality just to implement a new feature.

### Rule 4

Reuse existing components and services.

### Rule 5

Keep AI providers replaceable.

### Rule 6

Keep Gmail as a connector abstraction so other purchase sources can be added later.

# 🔮 Future Improvements

After the core application is complete, the following features can be added:

```text
Amazon integration
Flipkart integration
Other e-commerce connectors
WhatsApp document import
Google Drive integration
Email notifications
WhatsApp notifications
Mobile application
Family/shared locker
```

