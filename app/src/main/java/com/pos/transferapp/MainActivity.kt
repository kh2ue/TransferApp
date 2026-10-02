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
    var customerList = ArrayList<Customer>()
    
    private val CALL_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val inputName: EditText = findViewById(R.id.input_name)
        val btnSave: Button = findViewById(R.id.btn_save)
        val recyclerView: RecyclerView = findViewById(R.id.recycler_customers)

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadCustomers()
        
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

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else {
            executeUSSD()
        }
    }

    private fun executeUSSD() {
        val ussdCode = "*100" + Uri.encode("#")
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$ussdCode")
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                executeUSSD()
            } else {
                Toast.makeText(this, "عذراً، التطبيق يحتاج صلاحية الاتصال", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class CustomerAdapter(
    private val customers: List<Customer>,
    private val onItemClick: (Customer) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_customer_name)
        val tvInitial: TextView = view.findViewById(R.id.tv_initial)
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