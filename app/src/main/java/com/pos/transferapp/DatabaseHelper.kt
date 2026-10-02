package com.pos.transferapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "TransferApp.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // إنشاء جدول الزبائن
        val createTable = "CREATE TABLE Customers (ID INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Customers")
        onCreate(db)
    }

    // دالة لإضافة زبون جديد
    fun addCustomer(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put("Name", name)
        
        val result = db.insert("Customers", null, values)
        db.close()
        // إذا كانت النتيجة -1 يعني فشل الإضافة، غير هيك نجح
        return result != -1L
    }
}