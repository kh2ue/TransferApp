package com.pos.transferapp
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phoneSyriatel: String, val phoneMtn: String)
data class Transaction(val id: Int, val customerName: String, val amount: Int, val type: Int, val note: String, val date: String, val serviceType: String)
data class PendingRequest(val id: Int, val customerName: String, val network: String, val amount: Int, val price: Int, val date: String, val serviceType: String, val serviceAmount: Int)
data class CompanyPurchase(val id: Int, val companyName: String, val amount: Int, val cost: Int, val date: String, val note: String)
data class CompanyPayment(val id: Int, val companyName: String, val paidAmount: Int, val date: String, val note: String)
data class CompanyLogEntry(val id: Int, val companyName: String, val type: String, val amount: Long, val quantity: Long, val date: String, val note: String)
data class CompanyReportEntry(val id: Int, val type: String, val amount: Long, val quantity: Long, val date: String, val note: String)
data class SalesReport(val totalCredit: Long, val totalFinancial: Long, val count: Int)
data class ServiceCategory(val id: Int, val serviceName: String, val amount: Long, val price: Long)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 12) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT, Date TEXT, ServiceType TEXT, ServiceAmount INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE PendingRequests (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Network TEXT, Amount INTEGER, Price INTEGER, Date TEXT, ServiceType TEXT, ServiceAmount INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE CompanyPurchases (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, Amount INTEGER, Cost INTEGER, Date TEXT, Note TEXT)")
        db.execSQL("CREATE TABLE CompanyPayments (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, PaidAmount INTEGER, Date TEXT, Note TEXT)")
        db.execSQL("CREATE TABLE ServiceCategories (id INTEGER PRIMARY KEY AUTOINCREMENT, service_name TEXT NOT NULL, amount INTEGER NOT NULL, price INTEGER NOT NULL)")
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
    }

    fun addCategory(serviceName: String, amount: Long, price: Long): Boolean {
        if (serviceName.isBlank() || amount <= 0L || price <= 0L) return false
        val db = writableDatabase
        return try {
            db.beginTransaction()
            val existingId = db.rawQuery(
                "SELECT id FROM ServiceCategories WHERE service_name=? AND amount=? ORDER BY id ASC LIMIT 1",
                arrayOf(serviceName, amount.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            val saved = if (existingId != null) {
                val values = ContentValues().apply { put("price", price) }
                db.update("ServiceCategories", values, "id=?", arrayOf(existingId.toString())) > 0
            } else {
                val values = ContentValues().apply {
                    put("service_name", serviceName)
                    put("amount", amount)
                    put("price", price)
                }
                db.insert("ServiceCategories", null, values) != -1L
            }
            if (saved) db.setTransactionSuccessful()
            saved
        } finally {
            if (db.inTransaction()) db.endTransaction()
            db.close()
        }
    }

    fun updateCategory(id: Int, serviceName: String, amount: Long, price: Long): Boolean {
        if (id <= 0 || serviceName.isBlank() || amount <= 0L || price <= 0L) return false
        val db = writableDatabase
        return try {
            db.beginTransaction()
            val duplicateId = db.rawQuery(
                "SELECT id FROM ServiceCategories WHERE service_name=? AND amount=? AND id<>? ORDER BY id ASC LIMIT 1",
                arrayOf(serviceName, amount.toString(), id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            if (duplicateId != null) {
                val duplicateValues = ContentValues().apply { put("price", price) }
                val updated = db.update("ServiceCategories", duplicateValues, "id=?", arrayOf(duplicateId.toString())) > 0
                val deleted = db.delete("ServiceCategories", "id=?", arrayOf(id.toString())) > 0
                if (updated && deleted) db.setTransactionSuccessful()
                return updated && deleted
            }
            val values = ContentValues().apply {
                put("service_name", serviceName)
                put("amount", amount)
                put("price", price)
            }
            val updated = db.update("ServiceCategories", values, "id=?", arrayOf(id.toString())) > 0
            if (updated) db.setTransactionSuccessful()
            updated
        } finally {
            if (db.inTransaction()) db.endTransaction()
            db.close()
        }
    }

    fun deleteCategory(id: Int): Boolean {
        if (id <= 0) return false
        val db = writableDatabase
        return try {
            db.delete("ServiceCategories", "id=?", arrayOf(id.toString())) > 0
        } finally {
            db.close()
        }
    }

    fun getCategoriesByService(serviceName: String): List<ServiceCategory> {
        val categories = ArrayList<ServiceCategory>()
        val db = readableDatabase
        try {
            db.rawQuery(
                "SELECT id, service_name, amount, price FROM ServiceCategories WHERE service_name=? ORDER BY amount ASC",
                arrayOf(serviceName)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        categories.add(
                            ServiceCategory(
                                cursor.getInt(0),
                                cursor.getString(1),
                                cursor.getLong(2),
                                cursor.getLong(3)
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } finally {
            db.close()
        }
        return categories
    }

    // --- Customers ---
    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        val exists = db.rawQuery("SELECT ID FROM Customers WHERE Name=?", arrayOf(name)).use { it.count > 0 }
        if (exists) { db.close(); return false }
        val values = ContentValues().apply { put("Name", name); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
        val result = db.insert("Customers", null, values)
        db.close(); return result != -1L
    }
    fun updateCustomer(id: Int, oldName: String, newName: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase; db.beginTransaction()
        try {
            if (oldName != newName) {
                val nameExists = db.rawQuery("SELECT ID FROM Customers WHERE Name=?", arrayOf(newName)).use { it.count > 0 }
                if (nameExists) return false
            }
            val values = ContentValues().apply { put("Name", newName); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
            if (db.update("Customers", values, "ID=?", arrayOf(id.toString())) == 0) return false
            if (oldName != newName) {
                db.update("Transactions", ContentValues().apply { put("CustomerName", newName) }, "CustomerName=?", arrayOf(oldName))
                db.update("PendingRequests", ContentValues().apply { put("CustomerName", newName) }, "CustomerName=?", arrayOf(oldName))
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
            db.close()
        }
    }
    fun deleteCustomer(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("Customers", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllCustomers(): ArrayList<Customer> {
        val list = ArrayList<Customer>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        cursor.use {
            if (it.moveToFirst()) do { list.add(Customer(it.getInt(0), it.getString(1), it.getString(2), it.getString(3))) } while (it.moveToNext())
        }
        db.close(); return list
    }

    // --- Transactions ---
    fun addTransaction(customerName: String, amount: Int, type: Int, note: String, date: String, serviceType: String = "", serviceAmount: Int = 0): Boolean {
        if (amount <= 0 || serviceAmount < 0 || (type == 1 && serviceAmount == 0)) return false
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CustomerName", customerName); put("Amount", amount); put("Type", type); put("Note", note); put("Date", date); put("ServiceType", serviceType); put("ServiceAmount", serviceAmount) }
        val result = db.insert("Transactions", null, values)
        db.close(); return result != -1L
    }
    fun updateTransaction(id: Int, amount: Int, type: Int, note: String, serviceType: String = ""): Boolean {
        val db = this.writableDatabase; val values = ContentValues().apply { put("Amount", amount); put("Type", type); put("Note", note); put("ServiceType", serviceType) }
        val r = db.update("Transactions", values, "ID=?", arrayOf(id.toString())); db.close(); return r > 0
    }
    fun deleteTransaction(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("Transactions", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllTransactions(): ArrayList<Transaction> {
        val list = ArrayList<Transaction>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM Transactions ORDER BY ID DESC", null)
        val stIdx = cursor.getColumnIndex("ServiceType")
        cursor.use {
            if (it.moveToFirst()) do {
                val st = if (stIdx != -1 && !it.isNull(stIdx)) it.getString(stIdx) else ""
                list.add(Transaction(it.getInt(0), it.getString(1), it.getInt(2), it.getInt(3), it.getString(4), it.getString(5), st))
            } while (it.moveToNext())
        }
        db.close(); return list
    }
    fun getTotalDebt(): Long {
        var total = 0L; val db = this.readableDatabase; val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions", null)
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) total = it.getLong(0) }
        db.close(); return total
    }
    fun getCustomerBalance(customerName: String): Long {
        var total = 0L; val db = this.readableDatabase; val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions WHERE CustomerName = ?", arrayOf(customerName))
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) total = it.getLong(0) }
        db.close(); return total
    }

    // --- Pending Requests ---
    fun addPending(customerName: String, network: String, amount: Int, price: Int, date: String, serviceType: String = "", serviceAmount: Int = amount): Boolean {
        if (amount <= 0 || price <= 0 || serviceAmount <= 0) return false
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CustomerName", customerName); put("Network", network); put("Amount", amount); put("Price", price); put("Date", date); put("ServiceType", serviceType); put("ServiceAmount", serviceAmount) }
        val result = db.insert("PendingRequests", null, values)
        db.close(); return result != -1L
    }
    fun completePendingRequest(id: Int, customerName: String, transactionAmount: Int, note: String, date: String, serviceType: String, serviceAmount: Int): Boolean {
        if (transactionAmount <= 0 || serviceAmount <= 0) return false
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("CustomerName", customerName)
                put("Amount", transactionAmount)
                put("Type", 1)
                put("Note", note)
                put("Date", date)
                put("ServiceType", serviceType)
                put("ServiceAmount", serviceAmount)
            }
            if (db.insert("Transactions", null, values) == -1L) return false
            if (db.delete("PendingRequests", "ID=?", arrayOf(id.toString())) != 1) return false
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
            db.close()
        }
    }
    fun deletePending(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("PendingRequests", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllPending(): ArrayList<PendingRequest> {
        val list = ArrayList<PendingRequest>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM PendingRequests ORDER BY ID ASC", null)
        val stIdx = cursor.getColumnIndex("ServiceType")
        val serviceAmountIdx = cursor.getColumnIndex("ServiceAmount")
        cursor.use {
            if (it.moveToFirst()) do {
                val st = if (stIdx != -1 && !it.isNull(stIdx)) it.getString(stIdx) else ""
                val serviceAmount = if (serviceAmountIdx != -1 && !it.isNull(serviceAmountIdx)) it.getInt(serviceAmountIdx) else it.getInt(3)
                list.add(PendingRequest(it.getInt(0), it.getString(1), it.getString(2), it.getInt(3), it.getInt(4), it.getString(5), st, serviceAmount))
            } while (it.moveToNext())
        }
        db.close(); return list
    }

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
    fun getCompanyAvailableBalance(companyName: String): Long {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT " +
                "COALESCE((SELECT SUM(Amount) FROM CompanyPurchases WHERE CompanyName=?), 0) - " +
                "COALESCE((SELECT SUM(ServiceAmount) FROM Transactions WHERE ServiceType=? AND Type=1), 0)",
            arrayOf(companyName, companyName)
        )
        val availableBalance = cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        db.close()
        return availableBalance
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
}