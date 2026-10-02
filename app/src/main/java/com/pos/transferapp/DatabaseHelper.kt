package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Int, val name: String, val phone: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT, Phone TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        onCreate(db)
    }
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
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("Name"))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow("Phone"))
                list.add(Customer(id, name, phone))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}