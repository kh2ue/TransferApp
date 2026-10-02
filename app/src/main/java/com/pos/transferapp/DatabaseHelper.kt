package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// تعريف هيكل بيانات الزبون
data class Customer(val id: Int, val name: String)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        onCreate(db)
    }

    fun addCustomer(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("Name", name)
        val result = db.insert("Customers", null, values)
        db.close()
        return result != -1L
    }

    // الدالة الجديدة لجلب كل الزبائن من قاعدة البيانات (الأحدث أولاً)
    fun getAllCustomers(): ArrayList {
        val list = ArrayList()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Customers ORDER BY ID DESC", null)
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("Name"))
                list.add(Customer(id, name))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }
}