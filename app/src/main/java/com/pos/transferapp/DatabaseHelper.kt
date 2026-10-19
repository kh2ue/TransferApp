package com.pos.transferapp
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phoneSyriatel: String, val phoneMtn: String)
data class Transaction(val id: Int, val customerName: String, val amount: Int, val type: Int, val note: String, val date: String, val serviceType: String)
data class PendingRequest(val id: Int, val customerName: String, val network: String, val amount: Int, val price: Int, val date: String, val serviceType: String)
data class CompanyPurchase(val id: Int, val companyName: String, val amount: Int, val cost: Int, val date: String, val note: String)
data class CompanyPayment(val id: Int, val companyName: String, val paidAmount: Int, val date: String, val note: String)
data class SalesReport(val totalCredit: Int, val totalFinancial: Int, val count: Int)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 9) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT, Date TEXT, ServiceType TEXT)")
        db.execSQL("CREATE TABLE PendingRequests (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Network TEXT, Amount INTEGER, Price INTEGER, Date TEXT, ServiceType TEXT)")
        db.execSQL("CREATE TABLE CompanyPurchases (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, Amount INTEGER, Cost INTEGER, Date TEXT, Note TEXT)")
        db.execSQL("CREATE TABLE CompanyPayments (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, PaidAmount INTEGER, Date TEXT, Note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            try { db.execSQL("ALTER TABLE Transactions ADD COLUMN ServiceType TEXT DEFAULT ''") } catch (e: Exception) {}
            try { db.execSQL("ALTER TABLE PendingRequests ADD COLUMN ServiceType TEXT DEFAULT ''") } catch (e: Exception) {}
        }
        if (oldVersion < 9) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS CompanyPurchases (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, Amount INTEGER, Cost INTEGER, Date TEXT, Note TEXT)") } catch (e: Exception) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS CompanyPayments (ID INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT, PaidAmount INTEGER, Date TEXT, Note TEXT)") } catch (e: Exception) {}
        }
    }

    // --- Customers ---
    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT ID FROM Customers WHERE Name=?", arrayOf(name))
        if (cursor.count > 0) { cursor.close(); db.close(); return false }
        cursor.close()
        val values = ContentValues().apply { put("Name", name); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
        val result = db.insert("Customers", null, values)
        db.close(); return result != -1L
    }
    fun updateCustomer(id: Int, oldName: String, newName: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase; db.beginTransaction()
        try {
            if (oldName != newName) {
                val cursor = db.rawQuery("SELECT ID FROM Customers WHERE Name=?", arrayOf(newName))
                if (cursor.count > 0) { cursor.close(); db.endTransaction(); db.close(); return false }
                cursor.close()
            }
            val values = ContentValues().apply { put("Name", newName); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
            db.update("Customers", values, "ID=?", arrayOf(id.toString()))
            if (oldName != newName) {
                db.update("Transactions", ContentValues().apply { put("CustomerName", newName) }, "CustomerName=?", arrayOf(oldName))
                db.update("PendingRequests", ContentValues().apply { put("CustomerName", newName) }, "CustomerName=?", arrayOf(oldName))
            }
            db.setTransactionSuccessful(); return true
        } catch (e: Exception) { return false } finally { db.endTransaction(); db.close() }
    }
    fun deleteCustomer(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("Customers", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllCustomers(): ArrayList<Customer> {
        val list = ArrayList<Customer>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) do { list.add(Customer(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))) } while (cursor.moveToNext())
        cursor.close(); db.close(); return list
    }

    // --- Transactions ---
    fun addTransaction(customerName: String, amount: Int, type: Int, note: String, date: String, serviceType: String = ""): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CustomerName", customerName); put("Amount", amount); put("Type", type); put("Note", note); put("Date", date); put("ServiceType", serviceType) }
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
        if (cursor.moveToFirst()) do {
            val st = if (stIdx != -1 && !cursor.isNull(stIdx)) cursor.getString(stIdx) else ""
            list.add(Transaction(cursor.getInt(0), cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), st))
        } while (cursor.moveToNext())
        cursor.close(); db.close(); return list
    }
    fun getTotalDebt(): Int {
        var total = 0; val db = this.readableDatabase; val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions", null)
        if (cursor.moveToFirst()) { total = cursor.getInt(0) }; cursor.close(); db.close(); return total
    }
    fun getCustomerBalance(customerName: String): Int {
        var total = 0; val db = this.readableDatabase; val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions WHERE CustomerName = ?", arrayOf(customerName))
        if (cursor.moveToFirst()) { total = cursor.getInt(0) }; cursor.close(); db.close(); return total
    }

    // --- Pending Requests ---
    fun addPending(customerName: String, network: String, amount: Int, price: Int, date: String, serviceType: String = ""): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CustomerName", customerName); put("Network", network); put("Amount", amount); put("Price", price); put("Date", date); put("ServiceType", serviceType) }
        val result = db.insert("PendingRequests", null, values)
        db.close(); return result != -1L
    }
    fun deletePending(id: Int): Boolean { val db = this.writableDatabase; val r = db.delete("PendingRequests", "ID=?", arrayOf(id.toString())); db.close(); return r > 0 }
    fun getAllPending(): ArrayList<PendingRequest> {
        val list = ArrayList<PendingRequest>(); val db = this.readableDatabase; val cursor = db.rawQuery("SELECT * FROM PendingRequests ORDER BY ID ASC", null)
        val stIdx = cursor.getColumnIndex("ServiceType")
        if (cursor.moveToFirst()) do {
            val st = if (stIdx != -1 && !cursor.isNull(stIdx)) cursor.getString(stIdx) else ""
            list.add(PendingRequest(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4), cursor.getString(5), st))
        } while (cursor.moveToNext())
        cursor.close(); db.close(); return list
    }

    // --- Companies (Purchases & Payments) ---
    fun addCompanyPurchase(name: String, amount: Int, cost: Int, note: String, date: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CompanyName", name); put("Amount", amount); put("Cost", cost); put("Date", date); put("Note", note) }
        val result = db.insert("CompanyPurchases", null, values)
        db.close(); return result != -1L
    }
    fun addCompanyPayment(name: String, paidAmount: Int, note: String, date: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CompanyName", name); put("PaidAmount", paidAmount); put("Date", date); put("Note", note) }
        val result = db.insert("CompanyPayments", null, values)
        db.close(); return result != -1L
    }
    fun getCompanyAvailableBalance(companyName: String): Int {
        var purchased = 0; val db = this.readableDatabase
        var cursor = db.rawQuery("SELECT SUM(Amount) FROM CompanyPurchases WHERE CompanyName=?", arrayOf(companyName))
        if (cursor.moveToFirst()) purchased = cursor.getInt(0)
        cursor.close()
        var sold = 0
        cursor = db.rawQuery("SELECT Note FROM Transactions WHERE ServiceType=? AND Type=1", arrayOf(companyName))
        val regex = Regex("تحويل (\\\\d+)")
        if (cursor.moveToFirst()) do {
            val note = cursor.getString(0) ?: ""
            val match = regex.find(note)
            sold += match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } while (cursor.moveToNext())
        cursor.close(); db.close()
        return purchased - sold
    }
    fun getCompanyFinancialDebt(companyName: String): Int {
        var cost = 0; var paid = 0; val db = this.readableDatabase
        var cursor = db.rawQuery("SELECT SUM(Cost) FROM CompanyPurchases WHERE CompanyName=?", arrayOf(companyName))
        if (cursor.moveToFirst()) cost = cursor.getInt(0)
        cursor.close()
        cursor = db.rawQuery("SELECT SUM(PaidAmount) FROM CompanyPayments WHERE CompanyName=?", arrayOf(companyName))
        if (cursor.moveToFirst()) paid = cursor.getInt(0)
        cursor.close(); db.close()
        return cost - paid
    }
    fun getCompanySalesReport(companyName: String, fromDate: String, toDate: String): SalesReport {
        var totalCredit = 0; var totalFinancial = 0; var count = 0; val db = this.readableDatabase
        val query = "SELECT Amount, Note, Date FROM Transactions WHERE ServiceType=? AND Type=1"
        val cursor = db.rawQuery(query, arrayOf(companyName))
        val regex = Regex("تحويل (\\\\d+)")
        if (cursor.moveToFirst()) do {
            val date = cursor.getString(2) ?: ""
            val dateStr = if (date.length >= 10) date.substring(0, 10) else date
            var inRange = true
            if (fromDate.isNotEmpty() && dateStr < fromDate) inRange = false
            if (toDate.isNotEmpty() && dateStr > toDate) inRange = false
            if (inRange) {
                count++
                totalFinancial += cursor.getInt(0)
                val note = cursor.getString(1) ?: ""
                val match = regex.find(note)
                totalCredit += match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
        } while (cursor.moveToNext())
        cursor.close(); db.close()
        return SalesReport(totalCredit, totalFinancial, count)
    }
}