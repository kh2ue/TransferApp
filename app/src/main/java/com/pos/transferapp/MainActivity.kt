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
    private var pendingUssdCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        // استخدام Casting الصريح لتفادي أي أخطاء بالترجمة
        val inputName = findViewById(R.id.input_name) as EditText
        val inputPhone = findViewById(R.id.input_phone) as EditText
        val btnSave = findViewById(R.id.btn_save) as Button
        val recyclerView = findViewById(R.id.recycler_customers) as RecyclerView
        
        val navDebt = findViewById(R.id.nav_debt) as View
        val navSettings = findViewById(R.id.nav_settings) as View

        navDebt.setOnClickListener {
            Toast.makeText(this, "شاشة الديون (قيد البرمجة للخطوة القادمة)", Toast.LENGTH_SHORT).show()
        }

        navSettings.setOnClickListener {
            Toast.makeText(this, "شاشة الإعدادات (قيد البرمجة)", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        loadCustomers()

        adapter = CustomerAdapter(customerList) { selectedCustomer ->
            showTransferDialog(selectedCustomer)
        }
        recyclerView.adapter = adapter

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val phone = inputPhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                if (dbHelper.addCustomer(name, phone)) {
                    Toast.makeText(this, "تم إضافة الزبون بنجاح", Toast.LENGTH_SHORT).show()
                    inputName.text.clear()
                    inputPhone.text.clear()
                    loadCustomers()
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(this, "حدث خطأ", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال الاسم والرقم", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCustomers() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
    }

    private fun showTransferDialog(customer: Customer) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transfer, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val tvTitle = dialogView.findViewById(R.id.tv_dialog_title) as TextView
        val inputAmount = dialogView.findViewById(R.id.input_amount) as EditText
        val inputPin = dialogView.findViewById(R.id.input_pin) as EditText
        val btnConfirm = dialogView.findViewById(R.id.btn_confirm_transfer) as Button

        tvTitle.text = "تحويل لـ ${customer.name}"

        btnConfirm.setOnClickListener {
            val amount = inputAmount.text.toString().trim()
            val pin = inputPin.text.toString().trim()

            if (amount.isNotEmpty() && pin.isNotEmpty()) {
                val ussd = "*150*${customer.phone}*$amount*$pin"
                pendingUssdCode = ussd + Uri.encode("#")
                dialog.dismiss()
                checkPermissionAndCall()
            } else {
                Toast.makeText(this, "الرجاء إدخال المبلغ والرمز", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else {
            executeUSSD()
        }
    }

    private fun executeUSSD() {
        if (pendingUssdCode.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$pendingUssdCode")
            startActivity(intent)
            pendingUssdCode = ""
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                executeUSSD()
            } else {
                Toast.makeText(this, "يحتاج صلاحية الاتصال", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class CustomerAdapter(
    private val customers: List<Customer>,
    private val onItemClick: (Customer) -> Unit
) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvPhone = view.findViewById(R.id.tv_customer_phone) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        holder.tvPhone.text = customer.phone
        if (customer.name.isNotEmpty()) {
            holder.tvInitial.text = customer.name.take(1)
        }
        holder.itemView.setOnClickListener { onItemClick(customer) }
    }

    override fun getItemCount() = customers.size
}