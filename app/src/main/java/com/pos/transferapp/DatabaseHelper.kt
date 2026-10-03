package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phoneSyriatel: String, val phoneMtn: String)
data class Transaction(val id: Int, val customerName: String, val amount: Int, val type: Int, val note: String, val date: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT, Date TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        db.execSQL("DROP TABLE IF EXISTS Transactions")
        onCreate(db)
    }

    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("Name", name); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
        val result = db.insert("Customers", null, values)
        db.close()
        return result != -1L
    }

    fun updateCustomer(id: Int, oldName: String, newName: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply { put("Name", newName); put("PhoneSyriatel", phoneSyriatel); put("PhoneMtn", phoneMtn) }
            db.update("Customers", values, "ID=?", arrayOf(id.toString()))
            if (oldName != newName) {
                val transValues = ContentValues().apply { put("CustomerName", newName) }
                db.update("Transactions", transValues, "CustomerName=?", arrayOf(oldName))
            }
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) { return false } 
        finally { db.endTransaction(); db.close() }
    }

    fun deleteCustomer(id: Int): Boolean {
        val db = this.writableDatabase
        val result = db.delete("Customers", "ID=?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    fun getAllCustomers(): ArrayList<Customer> {
        val list = ArrayList<Customer>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) {
            do { list.add(Customer(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))) } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return list
    }

    fun addTransaction(customerName: String, amount: Int, type: Int, note: String, date: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("CustomerName", customerName); put("Amount", amount); put("Type", type); put("Note", note); put("Date", date) }
        val result = db.insert("Transactions", null, values)
        db.close()
        return result != -1L
    }

    fun updateTransaction(id: Int, amount: Int, type: Int, note: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put("Amount", amount); put("Type", type); put("Note", note) }
        val result = db.update("Transactions", values, "ID=?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    fun deleteTransaction(id: Int): Boolean {
        val db = this.writableDatabase
        val result = db.delete("Transactions", "ID=?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }

    fun getAllTransactions(): ArrayList<Transaction> {
        val list = ArrayList<Transaction>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Transactions ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) {
            do { list.add(Transaction(cursor.getInt(0), cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5))) } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return list
    }

    fun getTotalDebt(): Int {
        var total = 0
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions", null)
        if (cursor.moveToFirst()) { total = cursor.getInt(0) }
        cursor.close(); db.close()
        return total
    }

    fun getCustomerBalance(customerName: String): Int {
        var total = 0
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) FROM Transactions WHERE CustomerName = ?", arrayOf(customerName))
        if (cursor.moveToFirst()) { total = cursor.getInt(0) }
        cursor.close(); db.close()
        return total
    }
}