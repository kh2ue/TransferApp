package com.pos.transferapp
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class Customer(
    val id: Int,
    val name: String,
    val phoneSyriatel: String,
    val phoneMtn: String,
    val initialDebt: Double = 0.0
)
data class Transaction(
    val id: Int,
    val customerName: String,
    val amount: Int,
    val type: Int,
    val note: String,
    val date: String,
    val serviceType: String,
    val serviceAmount: Int = 0,
    val discountAddition: Double = 0.0,
    val profit: Double = 0.0,
    val paymentMethod: Int = 0
)
data class PendingRequest(
    val id: Int,
    val customerName: String,
    val network: String,
    val amount: Int,
    val price: Int,
    val date: String,
    val serviceType: String,
    val serviceAmount: Int,
    val dueDate: String = ""
)
data class CompanyPurchase(val id: Int, val companyName: String, val amount: Int, val cost: Int, val date: String, val note: String)
data class CompanyPayment(val id: Int, val companyName: String, val paidAmount: Int, val date: String, val note: String)
data class Official(val id: Int, val name: String)
data class Company(
    val id: Int,
    val name: String,
    val officialId: Int,
    val initialBalance: Double,
    val buyPrice: Double,
    val sellPrice: Double
)
data class CashTransaction(val id: Int, val type: Int, val amount: Double, val note: String, val date: String)
data class CompanyLogEntry(val id: Int, val companyName: String, val type: String, val amount: Long, val quantity: Long, val date: String, val note: String)
data class CompanyReportEntry(val id: Int, val type: String, val amount: Long, val quantity: Long, val date: String, val note: String)
data class OfficialReportEntry(
    val id: Int,
    val companyName: String,
    val type: String,
    val amount: Long,
    val quantity: Long,
    val date: String,
    val note: String
)
data class SalesReport(val totalCredit: Long, val totalFinancial: Long, val count: Int)
data class ServiceCategory(val id: Int, val serviceName: String, val amount: Long, val price: Long)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 13) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA busy_timeout=5000", null).use { }
        db.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val mode = cursor.getString(0)
                if (!mode.equals("delete", ignoreCase = true)) {
                    Log.w(TAG, "Database journal mode stayed $mode")
                }
            }
        }
        db.execSQL("PRAGMA synchronous=FULL")
    }

    fun flushToDisk() {
        val db = writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT, initial_debt REAL DEFAULT 0.0)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT, Date TEXT, ServiceType TEXT, ServiceAmount INTEGER DEFAULT 0, discount_addition REAL DEFAULT 0.0, profit REAL DEFAULT 0.0, payment_method INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE PendingRequests (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Network TEXT, Amount INTEGER, Price INTEGER, Date TEXT, ServiceType TEXT, ServiceAmount INTEGER DEFAULT 0, due_date TEXT DEFAULT '')")
        db.execSQL("CREATE TABLE CompanyPurchases (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, Amount INTEGER, Cost INTEGER, Date TEXT, Note TEXT)")
        db.execSQL("CREATE TABLE CompanyPayments (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, PaidAmount INTEGER, Date TEXT, Note TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS Officials (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS Companies (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, OfficialID INTEGER, InitialBalance REAL DEFAULT 0.0, BuyPrice REAL DEFAULT 0.0, SellPrice REAL DEFAULT 0.0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS CashBox (ID INTEGER PRIMARY KEY AUTOINCREMENT, Type INTEGER, Amount REAL, Note TEXT, Date TEXT)")
        db.execSQL("INSERT INTO Companies (Name, InitialBalance, BuyPrice, SellPrice) VALUES ('شام كاش', 0.0, 1.0, 1.0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            try { db.execSQL("ALTER TABLE Transactions ADD COLUMN ServiceType TEXT DEFAULT ''") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE PendingRequests ADD COLUMN ServiceType TEXT DEFAULT ''") } catch (e: Exception) {}
        }
        if (oldVersion < 10) {
            db.execSQL("CREATE TABLE IF NOT EXISTS CompanyPurchases (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, Amount INTEGER, Cost INTEGER, Date TEXT, Note TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS CompanyPayments (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, PaidAmount INTEGER, Date TEXT, Note TEXT)")
        }
        if (oldVersion < 11) {
            db.execSQL("ALTER TABLE Transactions ADD COLUMN ServiceAmount INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE PendingRequests ADD COLUMN ServiceAmount INTEGER DEFAULT 0")
            db.execSQL(
                "UPDATE Transactions SET ServiceAmount = CAST(substr(Note, 7, instr(substr(Note, 7) || ' ', ' ') - 1) AS INTEGER) " +
                    "WHERE Type=1 AND Note LIKE 'تحويل %' AND ServiceAmount=0"
            )
            db.execSQL("UPDATE PendingRequests SET ServiceAmount = Amount WHERE ServiceAmount=0")
        }
        if (oldVersion < 12) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ServiceCategories (id INTEGER PRIMARY KEY AUTOINCREMENT, service_name TEXT NOT NULL, amount INTEGER NOT NULL, price INTEGER NOT NULL)")
        }
        if (oldVersion < 13) {
            db.execSQL("DROP TABLE IF EXISTS ServiceCategories")

            db.execSQL("CREATE TABLE IF NOT EXISTS Officials (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS Companies (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, OfficialID INTEGER, InitialBalance REAL DEFAULT 0.0, BuyPrice REAL DEFAULT 0.0, SellPrice REAL DEFAULT 0.0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS CashBox (ID INTEGER PRIMARY KEY AUTOINCREMENT, Type INTEGER, Amount REAL, Note TEXT, Date TEXT)")

            db.execSQL("ALTER TABLE Customers ADD COLUMN initial_debt REAL DEFAULT 0.0")

            db.execSQL("ALTER TABLE Transactions ADD COLUMN discount_addition REAL DEFAULT 0.0")
            db.execSQL("ALTER TABLE Transactions ADD COLUMN profit REAL DEFAULT 0.0")
            db.execSQL("ALTER TABLE Transactions ADD COLUMN payment_method INTEGER DEFAULT 0")

            db.execSQL("ALTER TABLE PendingRequests ADD COLUMN due_date TEXT DEFAULT ''")

            // إضافة شركة شام كاش بشكل افتراضي
            db.execSQL("INSERT INTO Companies (Name, InitialBalance, BuyPrice, SellPrice) VALUES ('شام كاش', 0.0, 1.0, 1.0)")
        }
    }

    fun addOfficial(name: String): Long {
        if (name.isBlank()) return -1L
        val db = writableDatabase
        val values = ContentValues().apply { put("Name", name) }
        val result = db.insert("Officials", null, values)
        db.close()
        return result
    }

    fun getAllOfficials(): List<Official> {
        val list = ArrayList<Official>()
        val db = readableDatabase
        db.rawQuery("SELECT ID, Name FROM Officials ORDER BY ID ASC", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        Official(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("Name"))
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        db.close()
        return list
    }

    fun addCompany(company: Company): Long {
        if (company.name.isBlank()) return -1L
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Name", company.name)
            put("OfficialID", company.officialId)
            put("InitialBalance", company.initialBalance)
            put("BuyPrice", company.buyPrice)
            put("SellPrice", company.sellPrice)
        }
        val result = db.insert("Companies", null, values)
        db.close()
        return result
    }

    fun updateCompany(company: Company): Int {
        if (company.id <= 0 || company.name.isBlank()) return 0
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Name", company.name)
            put("OfficialID", company.officialId)
            put("InitialBalance", company.initialBalance)
            put("BuyPrice", company.buyPrice)
            put("SellPrice", company.sellPrice)
        }
        val result = db.update("Companies", values, "ID=?", arrayOf(company.id.toString()))
        db.close()
        return result
    }

    fun getAllCompanies(): List<Company> {
        val list = ArrayList<Company>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT ID, Name, OfficialID, InitialBalance, BuyPrice, SellPrice FROM Companies ORDER BY ID ASC",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    list.add(
                        Company(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("Name")),
                            officialId = cursor.getInt(cursor.getColumnIndexOrThrow("OfficialID")),
                            initialBalance = cursor.getDouble(cursor.getColumnIndexOrThrow("InitialBalance")),
                            buyPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("BuyPrice")),
                            sellPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("SellPrice"))
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        db.close()
        return list
    }

    fun deleteCompany(id: Int): Int {
        if (id <= 0) return 0
        val db = writableDatabase
        val result = db.delete("Companies", "ID=?", arrayOf(id.toString()))
        db.close()
        return result
    }

    fun addCashTransaction(type: Int, amount: Double, note: String, date: String): Boolean {
        if (!amount.isFinite() || amount <= 0.0 || date.isBlank()) return false
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Type", type)
            put("Amount", amount)
            put("Note", note)
            put("Date", date)
        }
        val result = db.insert("CashBox", null, values)
        db.close()
        return result != -1L
    }

    fun getAllCashTransactions(): List<CashTransaction> {
        val transactions = ArrayList<CashTransaction>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT ID, Type, Amount, Note, Date FROM CashBox ORDER BY Date DESC, ID DESC",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    transactions.add(
                        CashTransaction(
                            id = cursor.getInt(0),
                            type = cursor.getInt(1),
                            amount = cursor.getDouble(2),
                            note = cursor.getString(3).orEmpty(),
                            date = cursor.getString(4).orEmpty()
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        db.close()
        return transactions
    }

    fun getTotalCashAndProfit(): Double {
        val db = readableDatabase
        val total = db.rawQuery(
            "SELECT " +
                "COALESCE((SELECT SUM(Amount) FROM Transactions WHERE Type=2 AND payment_method=1), 0) + " +
                "COALESCE((SELECT SUM(profit) FROM Transactions WHERE Type=1), 0) + " +
                "COALESCE((SELECT SUM(CASE WHEN Type=0 THEN -Amount ELSE Amount END) FROM CashBox), 0)",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        db.close()
        return total
    }

    fun getTotalCashPayments(): Double {
        val db = readableDatabase
        val total = db.rawQuery(
            "SELECT COALESCE(SUM(Amount), 0) FROM Transactions WHERE Type=2 AND payment_method=1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        db.close()
        return total
    }

    fun getTotalCashExpenses(): Double {
        val db = readableDatabase
        val total = db.rawQuery(
            "SELECT COALESCE(SUM(Amount), 0) FROM CashBox WHERE Type=0",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        db.close()
        return total
    }

    // --- Customers ---
    fun addCustomer(customer: Customer): Boolean =
        addCustomer(
            customer.name,
            customer.phoneSyriatel,
            customer.phoneMtn,
            customer.initialDebt
        )

    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        return addCustomer(name, phoneSyriatel, phoneMtn, 0.0)
    }

    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String, initialDebt: Double): Boolean {
        if (name.isBlank() || !initialDebt.isFinite() || initialDebt < 0.0) return false
        val db = this.writableDatabase
        val exists = db.rawQuery("SELECT ID FROM Customers WHERE Name=?", arrayOf(name)).use { it.count > 0 }
        if (exists) { db.close(); return false }
        val values = ContentValues().apply {
            put("Name", name)
            put("PhoneSyriatel", phoneSyriatel)
            put("PhoneMtn", phoneMtn)
            put("initial_debt", initialDebt)
        }
        val result = db.insert("Customers", null, values)
        db.close(); return result != -1L
    }
    fun updateCustomer(
        id: Int,
        oldName: String,
        newName: String,
        phoneSyriatel: String,
        phoneMtn: String,
        initialDebt: Double = 0.0
    ): Boolean {
        if (id <= 0 || newName.isBlank() || !initialDebt.isFinite() || initialDebt < 0.0) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            if (oldName != newName) {
                val nameExists = db.rawQuery(
                    "SELECT ID FROM Customers WHERE Name=? AND ID<>?",
                    arrayOf(newName, id.toString())
                ).use { it.count > 0 }
                if (nameExists) return false
            }
            val values = ContentValues().apply {
                put("Name", newName)
                put("PhoneSyriatel", phoneSyriatel)
                put("PhoneMtn", phoneMtn)
                put("initial_debt", initialDebt)
            }
            if (db.update("Customers", values, "ID=?", arrayOf(id.toString())) == 0) return false
            if (oldName != newName) {
                db.update(
                    "Transactions",
                    ContentValues().apply { put("CustomerName", newName) },
                    "CustomerName=?",
                    arrayOf(oldName)
                )
                db.update(
                    "PendingRequests",
                    ContentValues().apply { put("CustomerName", newName) },
                    "CustomerName=?",
                    arrayOf(oldName)
                )
            }
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
            db.close()
        }
    }
    fun getCustomerRelatedCounts(name: String): Pair<Int, Int> {
        val db = readableDatabase
        return try {
            val transactions = db.rawQuery(
                "SELECT COUNT(*) FROM Transactions WHERE CustomerName=?",
                arrayOf(name)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val pending = db.rawQuery(
                "SELECT COUNT(*) FROM PendingRequests WHERE CustomerName=?",
                arrayOf(name)
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            Pair(transactions, pending)
        } finally {
            db.close()
        }
    }

    fun deleteCustomer(id: Int, name: String): Boolean {
        if (id <= 0 || name.isBlank()) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete("Transactions", "CustomerName=?", arrayOf(name))
            db.delete("PendingRequests", "CustomerName=?", arrayOf(name))
            if (db.delete("Customers", "ID=?", arrayOf(id.toString())) == 0) return false
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun getAllCustomers(): ArrayList<Customer> {
        val list = ArrayList<Customer>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        cursor.use {
            val initialDebtIndex = it.getColumnIndex("initial_debt")
            if (it.moveToFirst()) do {
                list.add(
                    Customer(
                        it.getInt(0),
                        it.getString(1),
                        it.getString(2),
                        it.getString(3),
                        if (initialDebtIndex >= 0) it.getDouble(initialDebtIndex) else 0.0
                    )
                )
            } while (it.moveToNext())
        }
        db.close(); return list
    }

    // --- Transactions ---
    fun addTransaction(
        customerName: String,
        amount: Int,
        type: Int,
        note: String,
        date: String,
        serviceType: String = "",
        serviceAmount: Int = 0,
        paymentMethod: Int = 0,
        discountAddition: Double = 0.0,
        profit: Double = 0.0
    ): Boolean {
        if (amount <= 0 || serviceAmount < 0 || (type == 1 && serviceAmount == 0)) return false
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("CustomerName", customerName)
            put("Amount", amount)
            put("Type", type)
            put("Note", note)
            put("Date", date)
            put("ServiceType", serviceType)
            put("ServiceAmount", serviceAmount)
            put("discount_addition", discountAddition)
            put("profit", profit)
            put("payment_method", paymentMethod)
        }
        val result = db.insert("Transactions", null, values)
        db.close(); return result != -1L
    }

    fun addTransaction(transaction: Transaction): Boolean =
        addTransactionInternal(transaction, null)

    fun addTransaction(transaction: Transaction, pendingRequestId: Int): Boolean =
        addTransactionInternal(transaction, pendingRequestId)

    private fun addTransactionInternal(transaction: Transaction, pendingRequestId: Int?): Boolean {
        if (transaction.amount <= 0 || transaction.serviceAmount < 0 ||
            (transaction.type == 1 && transaction.serviceAmount == 0) ||
            !transaction.discountAddition.isFinite() || !transaction.profit.isFinite()
        ) return false

        val db = writableDatabase
        db.beginTransaction()
        return try {
            val values = ContentValues().apply {
                put("CustomerName", transaction.customerName)
                put("Amount", transaction.amount)
                put("Type", transaction.type)
                put("Note", transaction.note)
                put("Date", transaction.date)
                put("ServiceType", transaction.serviceType)
                put("ServiceAmount", transaction.serviceAmount)
                put("discount_addition", transaction.discountAddition)
                put("profit", transaction.profit)
                put("payment_method", transaction.paymentMethod)
            }
            if (db.insert("Transactions", null, values) == -1L) return false
            if (pendingRequestId != null &&
                db.delete("PendingRequests", "ID=?", arrayOf(pendingRequestId.toString())) != 1
            ) return false
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun updateTransaction(
        id: Int,
        amount: Int,
        type: Int,
        note: String,
        serviceType: String = "",
        serviceAmount: Int = 0
    ): Boolean {
        if (id <= 0 || amount <= 0) return false
        val normalizedServiceAmount = if (type == 1) serviceAmount else 0
        if (type == 1 && normalizedServiceAmount <= 0) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val exists = db.rawQuery(
                "SELECT ID FROM Transactions WHERE ID=?",
                arrayOf(id.toString())
            ).use { it.moveToFirst() }
            if (!exists) return false

            if (type == 1 && serviceType.isNotBlank()) {
                val oldServiceAmount = db.rawQuery(
                    "SELECT ServiceAmount FROM Transactions WHERE ID=?",
                    arrayOf(id.toString())
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                val companyId = db.rawQuery(
                    "SELECT ID FROM Companies WHERE Name=? ORDER BY ID ASC LIMIT 1",
                    arrayOf(serviceType)
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                val currentAvailable =
                    getCompanyAvailableBalance(db, companyId, serviceType) + oldServiceAmount
                if (normalizedServiceAmount > currentAvailable) return false
            }

            val values = ContentValues().apply {
                put("Amount", amount)
                put("Type", type)
                put("Note", note)
                put("ServiceType", serviceType)
                put("ServiceAmount", normalizedServiceAmount)
            }
            if (db.update("Transactions", values, "ID=?", arrayOf(id.toString())) == 0) return false
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun deleteTransaction(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("Transactions", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllTransactions(): ArrayList<Transaction> {
        val list = ArrayList<Transaction>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM Transactions ORDER BY ID DESC", null)
        val stIdx = cursor.getColumnIndex("ServiceType")
        val saIdx = cursor.getColumnIndex("ServiceAmount")
        val discountAdditionIdx = cursor.getColumnIndex("discount_addition")
        val profitIdx = cursor.getColumnIndex("profit")
        val paymentMethodIdx = cursor.getColumnIndex("payment_method")
        cursor.use {
            if (it.moveToFirst()) do {
                val st = if (stIdx != -1 && !it.isNull(stIdx)) it.getString(stIdx) else ""
                val sa = if (saIdx != -1 && !it.isNull(saIdx)) it.getInt(saIdx) else 0
                val discountAddition = if (discountAdditionIdx != -1 && !it.isNull(discountAdditionIdx)) it.getDouble(discountAdditionIdx) else 0.0
                val profit = if (profitIdx != -1 && !it.isNull(profitIdx)) it.getDouble(profitIdx) else 0.0
                val paymentMethod = if (paymentMethodIdx != -1 && !it.isNull(paymentMethodIdx)) it.getInt(paymentMethodIdx) else 0
                list.add(Transaction(it.getInt(0), it.getString(1), it.getInt(2), it.getInt(3), it.getString(4), it.getString(5), st, sa, discountAddition, profit, paymentMethod))
            } while (it.moveToNext())
        }
        db.close(); return list
    }
    fun getTotalDebt(): Long {
        var total = 0L; val db = this.readableDatabase; val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions", null)
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) total = it.getLong(0) }
        db.close(); return total
    }
    fun getCustomerBalance(customerName: String): Double {
        var total = 0.0
        val db = readableDatabase
        db.rawQuery(
            "SELECT COALESCE((SELECT initial_debt FROM Customers WHERE Name=?), 0) + " +
                "COALESCE((SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions WHERE CustomerName=?), 0)",
            arrayOf(customerName, customerName)
        ).use { cursor ->
            if (cursor.moveToFirst()) total = cursor.getDouble(0)
        }
        db.close(); return total
    }

    // --- Pending Requests ---
    fun addPending(customerName: String, network: String, amount: Int, price: Int, date: String, serviceType: String = "", serviceAmount: Int = amount): Boolean {
        if (amount <= 0 || price <= 0 || serviceAmount <= 0) return false
        return addPendingRequest(
            PendingRequest(
                id = 0,
                customerName = customerName,
                network = network,
                amount = amount,
                price = price,
                date = date,
                serviceType = serviceType,
                serviceAmount = serviceAmount
            )
        ) != -1L
    }
    fun addPendingRequest(request: PendingRequest): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("CustomerName", request.customerName)
            put("Network", request.network)
            put("Amount", request.amount)
            put("Price", request.price)
            put("Date", request.date)
            put("ServiceType", request.serviceType)
            put("ServiceAmount", request.serviceAmount)
            put("due_date", request.dueDate)
        }
        return try {
            db.insert("PendingRequests", null, values)
        } finally {
            db.close()
        }
    }
    fun deletePending(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("PendingRequests", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllPendingRequests(): ArrayList<PendingRequest> {
        val list = ArrayList<PendingRequest>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM PendingRequests ORDER BY Date ASC", null)
        val stIdx = cursor.getColumnIndex("ServiceType")
        val serviceAmountIdx = cursor.getColumnIndex("ServiceAmount")
        cursor.use {
            if (it.moveToFirst()) do {
                val st = if (stIdx != -1 && !it.isNull(stIdx)) it.getString(stIdx) else ""
                val serviceAmount = if (serviceAmountIdx != -1 && !it.isNull(serviceAmountIdx)) it.getInt(serviceAmountIdx) else it.getInt(3)
                val dueDate = it.getString(it.getColumnIndexOrThrow("due_date")) ?: ""
                list.add(PendingRequest(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3), it.getInt(4), it.getString(5), st, serviceAmount, dueDate))
            } while (it.moveToNext())
        }
        db.close(); return list
    }
    fun getAllPending(): ArrayList<PendingRequest> = getAllPendingRequests()

    // --- Companies (Purchases & Payments) ---
    fun addCompanyPurchase(name: String, amount: Int, cost: Int, note: String, date: String): Boolean {
        if (amount <= 0 || cost <= 0) return false
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CompanyName", name); put("Amount", amount); put("Cost", cost); put("Date", date); put("Note", note) }
        val result = db.insert("CompanyPurchases", null, values)
        db.close(); return result != -1L
    }
    fun addCompanyPayment(name: String, paidAmount: Int, note: String, date: String): Boolean {
        if (paidAmount <= 0) return false
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CompanyName", name); put("PaidAmount", paidAmount); put("Date", date); put("Note", note) }
        val result = db.insert("CompanyPayments", null, values)
        db.close(); return result != -1L
    }
    fun getCompanyDetailedReport(companyName: String, fromDate: String, toDate: String): List<CompanyReportEntry> {
        val db = readableDatabase
        val dateFilter = StringBuilder()
        val args = ArrayList<String>()
        if (fromDate.isNotEmpty()) {
            dateFilter.append(" AND substr(Date, 1, 10) >= ?")
        }
        if (toDate.isNotEmpty()) {
            dateFilter.append(" AND substr(Date, 1, 10) <= ?")
        }
        fun addBaseArgs() {
            args.add(companyName)
            if (fromDate.isNotEmpty()) args.add(fromDate)
            if (toDate.isNotEmpty()) args.add(toDate)
        }
        val purchaseQuery =
            "SELECT ID, 'شحن رصيد' AS Type, Cost AS Amount, Amount AS Quantity, Date, Note " +
                "FROM CompanyPurchases WHERE CompanyName=?$dateFilter"
        val paymentQuery =
            "SELECT ID, 'دفعة للشركة' AS Type, PaidAmount AS Amount, 0 AS Quantity, Date, Note " +
                "FROM CompanyPayments WHERE CompanyName=?$dateFilter"
        val salesQuery =
            "SELECT ID, 'مبيعات للزبائن' AS Type, Amount, ServiceAmount AS Quantity, Date, " +
                "'الزبون: ' || COALESCE(CustomerName, '') || CASE WHEN COALESCE(Note, '')='' THEN '' ELSE ' - ' || Note END AS Note " +
                "FROM Transactions WHERE ServiceType=? AND Type=1$dateFilter"
        addBaseArgs()
        addBaseArgs()
        addBaseArgs()
        val query = "$purchaseQuery UNION ALL $paymentQuery UNION ALL $salesQuery ORDER BY Date DESC, ID DESC"
        val entries = ArrayList<CompanyReportEntry>()
        try {
            val cursor = db.rawQuery(query, args.toTypedArray())
            cursor.use {
                if (it.moveToFirst()) do {
                    entries.add(
                        CompanyReportEntry(
                            it.getInt(0),
                            it.getString(1) ?: "",
                            it.getLong(2),
                            it.getLong(3),
                            it.getString(4) ?: "",
                            it.getString(5) ?: ""
                        )
                    )
                } while (it.moveToNext())
            }
        } finally {
            db.close()
        }
        return entries
    }

    fun getOfficialReport(
        officialId: Int,
        fromDate: String,
        toDate: String,
        includePurchases: Boolean,
        includePayments: Boolean
    ): List<OfficialReportEntry> {
        val db = readableDatabase
        val entries = ArrayList<OfficialReportEntry>()
        if (!includePurchases && !includePayments) {
            db.close()
            return entries
        }

        val dateFilter = buildString {
            if (fromDate.isNotEmpty()) append(" AND substr(Date, 1, 10) >= ?")
            if (toDate.isNotEmpty()) append(" AND substr(Date, 1, 10) <= ?")
        }
        val queries = ArrayList<String>()
        val args = ArrayList<String>()

        fun appendArgs() {
            args.add(officialId.toString())
            if (fromDate.isNotEmpty()) args.add(fromDate)
            if (toDate.isNotEmpty()) args.add(toDate)
        }

        if (includePurchases) {
            queries.add(
                "SELECT cp.ID, c.Name AS CompanyName, 'أرصدة شحن' AS Type, " +
                    "cp.Cost AS Amount, cp.Amount AS Quantity, cp.Date, cp.Note " +
                    "FROM CompanyPurchases cp INNER JOIN Companies c ON c.Name=cp.CompanyName " +
                    "WHERE c.OfficialID=?$dateFilter"
            )
            appendArgs()
        }
        if (includePayments) {
            queries.add(
                "SELECT pm.ID, c.Name AS CompanyName, 'دفعات مالية' AS Type, " +
                    "pm.PaidAmount AS Amount, 0 AS Quantity, pm.Date, pm.Note " +
                    "FROM CompanyPayments pm INNER JOIN Companies c ON c.Name=pm.CompanyName " +
                    "WHERE c.OfficialID=?$dateFilter"
            )
            appendArgs()
        }

        try {
            db.rawQuery(
                "${queries.joinToString(" UNION ALL ")} ORDER BY Date DESC, ID DESC",
                args.toTypedArray()
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        entries.add(
                            OfficialReportEntry(
                                id = cursor.getInt(0),
                                companyName = cursor.getString(1).orEmpty(),
                                type = cursor.getString(2).orEmpty(),
                                amount = cursor.getLong(3),
                                quantity = cursor.getLong(4),
                                date = cursor.getString(5).orEmpty(),
                                note = cursor.getString(6).orEmpty()
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } finally {
            db.close()
        }
        return entries
    }

    fun getCompanyLog(companyName: String, filterType: String): List<CompanyLogEntry> {
        val db = readableDatabase
        val purchaseQuery =
            "SELECT ID, CompanyName, 'شحن رصيد' AS Type, Cost AS Amount, Amount AS Quantity, Date, Note " +
                "FROM CompanyPurchases WHERE CompanyName=?"
        val paymentQuery =
            "SELECT ID, CompanyName, 'دفعات' AS Type, PaidAmount AS Amount, 0 AS Quantity, Date, Note " +
                "FROM CompanyPayments WHERE CompanyName=?"
        val (query, args) = when (filterType) {
            "شحن رصيد", "Purchases" -> purchaseQuery to arrayOf(companyName)
            "دفعات", "Payments" -> paymentQuery to arrayOf(companyName)
            else -> "$purchaseQuery UNION ALL $paymentQuery" to arrayOf(companyName, companyName)
        }

        val entries = ArrayList<CompanyLogEntry>()
        val cursor = db.rawQuery("$query ORDER BY Date DESC, ID DESC", args)
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("ID")
            val companyIndex = it.getColumnIndexOrThrow("CompanyName")
            val typeIndex = it.getColumnIndexOrThrow("Type")
            val amountIndex = it.getColumnIndexOrThrow("Amount")
            val quantityIndex = it.getColumnIndexOrThrow("Quantity")
            val dateIndex = it.getColumnIndexOrThrow("Date")
            val noteIndex = it.getColumnIndexOrThrow("Note")
            if (it.moveToFirst()) do {
                entries.add(
                    CompanyLogEntry(
                        it.getInt(idIndex),
                        it.getString(companyIndex) ?: "",
                        it.getString(typeIndex) ?: "",
                        it.getLong(amountIndex),
                        it.getLong(quantityIndex),
                        it.getString(dateIndex) ?: "",
                        it.getString(noteIndex) ?: ""
                    )
                )
            } while (it.moveToNext())
        }
        db.close()
        return entries
    }
    fun getCompanyAvailableBalance(companyId: Int, companyName: String): Double {
        val db = readableDatabase
        return try {
            getCompanyAvailableBalance(db, companyId, companyName)
        } finally {
            db.close()
        }
    }

    private fun getCompanyAvailableBalance(
        db: SQLiteDatabase,
        companyId: Int,
        companyName: String
    ): Double {
        return db.rawQuery(
            "SELECT " +
                "COALESCE((SELECT InitialBalance FROM Companies WHERE ID=? AND Name=?), 0) + " +
                "COALESCE((SELECT SUM(Amount) FROM CompanyPurchases WHERE CompanyName=?), 0) - " +
                "COALESCE((SELECT SUM(ServiceAmount) FROM Transactions WHERE Type=1 AND ServiceType=?), 0) + " +
                "CASE WHEN ?='شام كاش' THEN " +
                "COALESCE((SELECT SUM(Amount) FROM Transactions WHERE Type=2 AND payment_method=2), 0) " +
                "ELSE 0 END",
            arrayOf(
                companyId.toString(),
                companyName,
                companyName,
                companyName,
                companyName
            )
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
    }

    fun getCompanyAvailableBalance(companyName: String): Double {
        val db = readableDatabase
        val companyId = db.rawQuery(
            "SELECT ID FROM Companies WHERE Name=? ORDER BY ID ASC LIMIT 1",
            arrayOf(companyName)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        db.close()
        return getCompanyAvailableBalance(companyId, companyName)
    }
    fun getCompanyFinancialDebt(companyName: String): Long {
        var cost = 0L
        var paid = 0L
        val db = this.readableDatabase
        var cursor = db.rawQuery("SELECT SUM(Cost) FROM CompanyPurchases WHERE CompanyName=?", arrayOf(companyName))
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) cost = it.getLong(0) }
        cursor = db.rawQuery("SELECT SUM(PaidAmount) FROM CompanyPayments WHERE CompanyName=?", arrayOf(companyName))
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) paid = it.getLong(0) }
        db.close()
        return cost - paid
    }

    fun getOfficialDebt(officialId: Int): Double {
        val db = readableDatabase
        val query = """
            SELECT
                COALESCE((
                    SELECT SUM(cp.Cost)
                    FROM CompanyPurchases cp
                    INNER JOIN Companies c ON c.Name = cp.CompanyName
                    WHERE c.OfficialID = ?
                ), 0) -
                COALESCE((
                    SELECT SUM(pm.PaidAmount)
                    FROM CompanyPayments pm
                    INNER JOIN Companies c ON c.Name = pm.CompanyName
                    WHERE c.OfficialID = ?
                ), 0)
        """.trimIndent()
        val debt = db.rawQuery(query, arrayOf(officialId.toString(), officialId.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        db.close()
        return debt
    }

    fun getCompanyProfit(companyName: String): Double {
        val db = readableDatabase
        val profit = db.rawQuery(
            "SELECT COALESCE(SUM(profit), 0) FROM Transactions WHERE ServiceType=? AND Type=1",
            arrayOf(companyName)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        db.close()
        return profit
    }

    fun getCompanySalesReport(companyName: String, fromDate: String, toDate: String): Pair<Long, Long> {
        var totalCredit = 0L
        var totalFinancial = 0L
        val db = this.readableDatabase
        val selection = StringBuilder("ServiceType=? AND Type=1")
        val selectionArgs = ArrayList<String>().apply { add(companyName) }
        if (fromDate.isNotEmpty()) {
            selection.append(" AND substr(Date, 1, 10) >= ?")
            selectionArgs.add(fromDate)
        }
        if (toDate.isNotEmpty()) {
            selection.append(" AND substr(Date, 1, 10) <= ?")
            selectionArgs.add(toDate)
        }
        val cursor = db.rawQuery(
            "SELECT COALESCE(SUM(ServiceAmount), 0), COALESCE(SUM(Amount), 0) FROM Transactions WHERE $selection",
            selectionArgs.toTypedArray()
        )
        cursor.use {
            if (it.moveToFirst()) {
                totalCredit = it.getLong(0)
                totalFinancial = it.getLong(1)
            }
        }
        db.close()
        return Pair(totalCredit, totalFinancial)
    }
    fun getCompanySalesCount(companyName: String, fromDate: String, toDate: String): Int {
        val db = this.readableDatabase
        val selection = StringBuilder("ServiceType=? AND Type=1")
        val selectionArgs = ArrayList<String>().apply { add(companyName) }
        if (fromDate.isNotEmpty()) {
            selection.append(" AND substr(Date, 1, 10) >= ?")
            selectionArgs.add(fromDate)
        }
        if (toDate.isNotEmpty()) {
            selection.append(" AND substr(Date, 1, 10) <= ?")
            selectionArgs.add(toDate)
        }
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM Transactions WHERE $selection",
            selectionArgs.toTypedArray()
        )
        val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        db.close()
        return count
    }

    private companion object {
        const val TAG = "DatabaseHelper"
    }

    
    
}