package com.pos.transferapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    
    lateinit var dbHelper: DatabaseHelper
    lateinit var adapter: CustomerAdapter
    var customerList = ArrayList()
    
    // كود تعريفي لطلب الصلاحية
    private val CALL_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val inputName = findViewById(R.id.input_name) as EditText
        val btnSave = findViewById(R.id.btn_save) as Button
        val recyclerView = findViewById(R.id.recycler_customers) as RecyclerView

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadCustomers()
        
        // عند الضغط على اسم الزبون من القائمة
        adapter = CustomerAdapter(customerList) { selectedCustomer ->
            showTestDialog(selectedCustomer)
        }
        recyclerView.adapter = adapter

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            if (name.isNotEmpty()) {
                if (dbHelper.addCustomer(name)) {
                    Toast.makeText(this, "تم إضافة الزبون بنجاح", Toast.LENGTH_SHORT).show()
                    inputName.text.clear()
                    loadCustomers()
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "حدث خطأ أثناء الإضافة", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال اسم الزبون", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCustomers() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
    }

    // ظهور نافذة منبثقة للتأكيد
    private fun showTestDialog(customer: Customer) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("تجربة الـ USSD")
        builder.setMessage("هل تريد تنفيذ كود الاستعلام (*100#) كتجربة للزبون: ${customer.name}؟")
        builder.setPositiveButton("تنفيذ") { _, _ ->
            checkPermissionAndCall()
        }
        builder.setNegativeButton("إلغاء", null)
        builder.show()
    }

    // التحقق من الصلاحية قبل الاتصال
    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // إذا مافي صلاحية، اطلبها من المستخدم
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else {
            // إذا في صلاحية، نفذ فوراً
            executeUSSD()
        }
    }

    // التنفيذ الفعلي للكود
    private fun executeUSSD() {
        // تحويل المربع لـ %23 ضروري جداً
        val ussdCode = "*100" + Uri.encode("#")
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$ussdCode")
        startActivity(intent)
    }

    // استقبال نتيجة طلب الصلاحية (هل وافق المستخدم أم رفض؟)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // المستخدم وافق، نفذ الكود
                executeUSSD()
            } else {
                Toast.makeText(this, "عذراً، التطبيق يحتاج صلاحية الاتصال لتنفيذ الحوالة", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// كلاس الزبون والمحول (Adapter) 
data class Customer(val id: Int, val name: String)

class CustomerAdapter(
    private val customers: List,
    private val onItemClick: (Customer) -> Unit
) : RecyclerView.Adapter() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        if (customer.name.isNotEmpty()) {
            holder.tvInitial.text = customer.name.take(1)
        }
        holder.itemView.setOnClickListener { onItemClick(customer) }
    }

    override fun getItemCount() = customers.size
}