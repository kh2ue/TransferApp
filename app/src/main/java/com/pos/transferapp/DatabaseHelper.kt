package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phone: String)
data class Debt(val id: Int, val name: String, val amount: Int, val note: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, Phone TEXT)")
        db.execSQL("CREATE TABLE Debts (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, Amount INTEGER, Note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        db.execSQL("DROP TABLE IF EXISTS Debts")
        onCreate(db)
    }

    // زبائن
    fun addCustomer(name: String, phone: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("Name", name)
        values.put("Phone", phone)
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
                    cursor.getString(cursor.getColumnIndexOrThrow("Phone"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // ديون
    fun addDebt(name: String, amount: Int, note: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("Name", name)
        values.put("Amount", amount)
        values.put("Note", note)
        val result = db.insert("Debts", null, values)
        db.close()
        return result != -1L
    }
    fun getAllDebts(): ArrayList<Debt> {
        val list = ArrayList<Debt>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Debts ORDER BY ID DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Debt(
                    cursor.getInt(cursor.getColumnIndexOrThrow("ID")),
                    cursor.getString(cursor.getColumnIndexOrThrow("Name")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("Amount")),
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
        val cursor = db.rawQuery("SELECT SUM(Amount) as Total FROM Debts", null)
        if (cursor.moveToFirst()) {
            total = cursor.getInt(cursor.getColumnIndexOrThrow("Total"))
        }
        cursor.close()
        db.close()
        return total
    }
}