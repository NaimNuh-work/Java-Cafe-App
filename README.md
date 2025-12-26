☕ Cafe Inventory Management System

A Java-based Point of Sale (POS) and Inventory Management system designed for small cafes. This application uses a lightweight HTTP server to serve a web-based frontend and manages data persistence using CSV and JSON files.

🚀 Features

Point of Sale (POS): Interactive web interface to browse the menu and place orders.

Real-time Inventory Tracking: Automatically deducts ingredients (e.g., beans, milk) from stock when items are sold.

Low Stock Alerts: Sends automated email notifications (via SMTP) when ingredients drop below a specific threshold.

Role-Based Access:

Staff: Can view menu and place orders.

Admin: Can view stock levels and restock items.

Data Persistence: Saves all inventory changes to inventory.csv automatically.

📂 Project Structure

CafeProject/
├── src/
│   └── CafeWebServer.java    # Main backend logic (Server, Services, Controllers)
├── index.html                # Frontend User Interface
├── inventory.csv             # Database for raw ingredient stocks
├── recipes.json              # Database for menu items and recipes
├── users.csv                 # Database for user credentials
├── javax.mail.jar            # Library for sending emails
├── javax.activation.jar      # Dependency for JavaMail
└── README.md                 # This documentation


🛠️ Prerequisites

To run this project, you need:

Java Development Kit (JDK): Version 11 or higher (JDK 17 recommended).

IntelliJ IDEA (or any Java IDE).

External Libraries:

javax.mail-1.6.2.jar

javax.activation-1.2.0.jar

⚙️ Setup & Installation

Clone/Download the Project:
Extract the files into a folder.

Add Libraries:

In IntelliJ, go to File > Project Structure > Modules > Dependencies.

Click (+) and add the javax.mail.jar and javax.activation.jar files.

Configure Email (Optional):

Open src/CafeWebServer.java.

Locate the EmailService class.

Update SENDER_EMAIL, APP_PASSWORD, and RECIPIENT_EMAIL with your own credentials to enable alerts.

▶️ How to Run

Open the project in IntelliJ IDEA.

Run the CafeWebServer.java file.

Wait for the console message:

>>> SERVER READY at http://localhost:8080


Open your web browser and navigate to: http://localhost:8080

📖 User Guide

1. Login

When you open the app, you must login. Use the default credentials found in users.csv:

Role

Username

Password

Access

Admin

admin

admin123

Can Order + Restock Items

Staff

staff

staff123

Can Order Only

2. Making a Sale

Click on any menu item (e.g., "Latte").

If stock is sufficient, a success message appears, and inventory is deducted.

If stock is low, the transaction fails to prevent negative inventory.

3. Restocking (Admin Only)

Login as Admin.

In the Inventory table, click the (+) button next to an item.

Enter the quantity to add (e.g., 500 for 500ml/grams).

The inventory.csv file will be updated immediately.

💾 Data Formats

Inventory Database (inventory.csv)

Stores raw materials.

ID,Name,CurrentStock,Unit,LowStockThreshold
501,Espresso Beans,1000.0,grams,200.0
503,Whole Milk,5000.0,ml,1000.0


Menu Recipes (recipes.json)

Maps menu items to required ingredients.

[
  {
    "id": 104,
    "name": "Latte",
    "price": 5.00,
    "ingredients": [
      { "id": 501, "qty": 18 },   // 18g Beans
      { "id": 503, "qty": 250 }   // 250ml Milk
    ]
  }
]


🏗️ Design Patterns Used

MVC Architecture: Separation of Frontend (View), inventory.csv (Model), and CafeWebServer (Controller).

Singleton Pattern: The InventoryService ensures a single source of truth for stock levels.

Inheritance: All API controllers extend a BaseController for shared JSON handling logic.

Encapsulation: Data fields are private and accessed only through Service methods.

⚠️ Troubleshooting

"Port 8080 already in use": Stop any other running instances of the server or change the port in CafeWebServer.java.

"NoClassDefFoundError": Ensure both JAR files are correctly added to the Project Dependencies.

Email not sending: Check your Google App Password and ensure your antivirus is not blocking Java.
