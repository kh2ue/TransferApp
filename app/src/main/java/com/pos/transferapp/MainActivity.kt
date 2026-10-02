package com.pos.transferapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    // تعريف قاعدة البيانات
    lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // تهيئة قاعدة البيانات
        dbHelper = DatabaseHelper(this)

        // جلب العناصر من الواجهة (الخانة والزر)
        val inputName = findViewById(R.id.input_name)
        val btnSave = findViewById(R.id.btn_save)

        // برمجة زر الإضافة
        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()

            if (name.isNotEmpty()) {
                val isInserted = dbHelper.addCustomer(name)
                if (isInserted) {
                    // رسالة نجاح تظهر بأسفل الشاشة
                    Toast.makeText(this, "تم إضافة الزبون بنجاح!", Toast.LENGTH_SHORT).show()
                    // تفريغ الخانة بعد الإضافة
                    inputName.text.clear()
                } else {
                    Toast.makeText(this, "حدث خطأ أثناء الإضافة", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال اسم الزبون", Toast.LENGTH_SHORT).show()
            }
        }
    }
}