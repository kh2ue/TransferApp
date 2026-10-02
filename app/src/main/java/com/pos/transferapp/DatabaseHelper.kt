package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phoneSyriatel: String, val phoneMtn: String)
// Type: 1 = دين (أخذ رصيد) , 2 = دفعة (سدد مبلغ)
data class Transaction(val id: Int, val customerName: String, val amount: Int, val type: Int, val note: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        db.execSQL("DROP TABLE IF EXISTS Debts")
        db.execSQL("DROP TABLE IF EXISTS Transactions")
        onCreate(db)
    }

    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("Name", name)
            put("PhoneSyriatel", phoneSyriatel)
            put("PhoneMtn", phoneMtn)
        }
        val result = db.insert("Customers", null, values)
        db.close()
        return result != -1L
    }

    fun getAllCustomers(): ArrayList<Customer> {
        val list = ArrayList<Customer>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Customer(
                    cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("PhoneSyriatel")),
                    cursor.getString(cursor.getColumnIndexOrThrow("PhoneMtn"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun addTransaction(customerName: String, amount: Int, type: Int, note: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("CustomerName", customerName)
            put("Amount", amount)
            put("Type", type)
            put("Note", note)
        }
        val result = db.insert("Transactions", null, values)
        db.close()
        return result != -1L
    }

    fun getAllTransactions(): ArrayList<Transaction> {
        val list = ArrayList<Transaction>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Transactions ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Transaction(
                    cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                    cursor.getString(cursor.getColumnIndexOrThrow("CustomerName")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("Amount")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("Type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Note"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun getTotalDebt(): Int {
        var total = 0
        val db = this.readableDatabase
        // الديون (+) ناقص الدفعات (-)
        val cursor = db.rawQuery("SELECT SUM(CASE WHEN Type = 1 THEN Amount ELSE -Amount END) as Total FROM Transactions", null)
        if (cursor.moveToFirst()) {
            total = cursor.getInt(cursor.getColumnIndexOrThrow("Total"))
        }
        cursor.close()
        db.close()
        return total
    }
}