package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phoneSyriatel: String, val phoneMtn: String)
// type: 1 = دين (عليه) , 2 = دفعة (إله)
data class Transaction(val id: Int, val customerId: Int, val customerName: String, val amount: Int, val type: Int, val note: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, PhoneSyriatel TEXT, PhoneMtn TEXT)")
        db.execSQL("CREATE TABLE Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, CustomerID INTEGER, CustomerName TEXT, Amount INTEGER, Type INTEGER, Note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        db.execSQL("DROP TABLE IF EXISTS Debts")
        db.execSQL("DROP TABLE IF EXISTS Transactions")
        onCreate(db)
    }

    fun addCustomer(name: String, phoneSyriatel: String, phoneMtn: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("Name", name)
        values.put("PhoneSyriatel", phoneSyriatel)
        values.put("PhoneMtn", phoneMtn)
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
}