package com.pos.transferapp

import android.Manifest
import android.content.Context
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
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var dbHelper: DatabaseHelper
    lateinit var customerAdapter: CustomerAdapter
    lateinit var debtAdapter: DebtAdapter
    var customerList = ArrayList<Customer>()
    var debtList = ArrayList<Debt>()

    private val CALL_REQUEST_CODE = 123
    private var pendingUssdCode = ""
    lateinit var tvTotalDebt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val tvMainTitle = findViewById(R.id.tv_main_title) as TextView
        val layoutHome = findViewById(R.id.layout_home) as View
        val layoutDebt = findViewById(R.id.layout_debt) as View
        val layoutSettings = findViewById(R.id.layout_settings) as View

        val navHome = findViewById(R.id.nav_home) as View
        val navDebt = findViewById(R.id.nav_debt) as View
        val navSettings = findViewById(R.id.nav_settings) as View

        navHome.setOnClickListener {
            layoutHome.visibility = View.VISIBLE
            layoutDebt.visibility = View.GONE
            layoutSettings.visibility = View.GONE
            tvMainTitle.text = "إدارة الحوالات"
        }
        navDebt.setOnClickListener {
            layoutHome.visibility = View.GONE
            layoutDebt.visibility = View.VISIBLE
            layoutSettings.visibility = View.GONE
            tvMainTitle.text = "إدارة الديون"
        }
        navSettings.setOnClickListener {
            layoutHome.visibility = View.GONE
            layoutDebt.visibility = View.GONE
            layoutSettings.visibility = View.VISIBLE
            tvMainTitle.text = "الإعدادات"
        }

        // الرئيسية (الزبائن)
        val inputName = findViewById(R.id.input_name) as EditText
        val inputPhone = findViewById(R.id.input_phone) as EditText
        val btnSave = findViewById(R.id.btn_save) as Button
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList) { selectedCustomer ->
            showTransferDialog(selectedCustomer)
        }
        recyclerCustomers.adapter = customerAdapter

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val phone = inputPhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                if (dbHelper.addCustomer(name, phone)) {
                    Toast.makeText(this, "تم إضافة الزبون", Toast.LENGTH_SHORT).show()
                    inputName.text.clear()
                    inputPhone.text.clear()
                    loadData()
                }
            } else {
                Toast.makeText(this, "أدخل الاسم والرقم", Toast.LENGTH_SHORT).show()
            }
        }

        // الديون
        tvTotalDebt = findViewById(R.id.tv_total_debt) as TextView
        val btnShowAddDebt = findViewById(R.id.btn_show_add_debt) as Button
        val recyclerDebts = findViewById(R.id.recycler_debts) as RecyclerView
        
        recyclerDebts.layoutManager = LinearLayoutManager(this)
        debtAdapter = DebtAdapter(debtList)
        recyclerDebts.adapter = debtAdapter

        btnShowAddDebt.setOnClickListener {
            showAddDebtDialog()
        }

        // الإعدادات (SharedPreferences)
        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val inputSettingsPin = findViewById(R.id.input_settings_pin) as EditText
        val btnSaveSettings = findViewById(R.id.btn_save_settings) as Button

        // جلب الرمز السري المحفوظ وعرضه بالخانة
        inputSettingsPin.setText(sharedPref.getString("default_pin", ""))

        btnSaveSettings.setOnClickListener {
            val pin = inputSettingsPin.text.toString().trim()
            sharedPref.edit().putString("default_pin", pin).apply()
            Toast.makeText(this, "تم حفظ الرمز السري بنجاح!", Toast.LENGTH_SHORT).show()
        }

        loadData()
    }

    private fun loadData() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
        customerAdapter.notifyDataSetChanged()

        debtList.clear()
        debtList.addAll(dbHelper.getAllDebts())
        debtAdapter.notifyDataSetChanged()

        val total = dbHelper.getTotalDebt()
        val formattedTotal = NumberFormat.getNumberInstance(Locale.US).format(total)
        tvTotalDebt.text = "$formattedTotal ل.س"
    }

    private fun showAddDebtDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_debt, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val inputName = dialogView.findViewById(R.id.input_debt_name) as EditText
        val inputAmount = dialogView.findViewById(R.id.input_debt_amount) as EditText
        val inputNote = dialogView.findViewById(R.id.input_debt_note) as EditText
        val btnConfirm = dialogView.findViewById(R.id.btn_confirm_debt) as Button

        btnConfirm.setOnClickListener {
            val name = inputName.text.toString().trim()
            val amountStr = inputAmount.text.toString().trim()
            val note = inputNote.text.toString().trim()

            if (name.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                if (dbHelper.addDebt(name, amount, note)) {
                    Toast.makeText(this, "تم حفظ الدين", Toast.LENGTH_SHORT).show()
                    loadData()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "أدخل الاسم والمبلغ", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
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
        
        // تعبئة الرمز السري تلقائياً من الإعدادات
        val sharedPref = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val savedPin = sharedPref.getString("default_pin", "")
        inputPin.setText(savedPin)

        btnConfirm.setOnClickListener {
            val amount = inputAmount.text.toString().trim()
            val pin = inputPin.text.toString().trim()
            if (amount.isNotEmpty() && pin.isNotEmpty()) {
                val ussd = "*150*${customer.phone}*$amount*$pin"
                pendingUssdCode = ussd + Uri.encode("#")
                dialog.dismiss()
                checkPermissionAndCall()
            } else {
                Toast.makeText(this, "أدخل المبلغ والرمز", Toast.LENGTH_SHORT).show()
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
        if (requestCode == CALL_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            executeUSSD()
        }
    }
}

class CustomerAdapter(private val customers: List<Customer>, private val onItemClick: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvPhone = view.findViewById(R.id.tv_customer_phone) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        holder.tvPhone.text = customer.phone
        if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1)
        holder.itemView.setOnClickListener { onItemClick(customer) }
    }
    override fun getItemCount() = customers.size
}

class DebtAdapter(private val debts: List<Debt>) : RecyclerView.Adapter<DebtAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_debt_name) as TextView
        val tvNote = view.findViewById(R.id.tv_debt_note) as TextView
        val tvAmount = view.findViewById(R.id.tv_debt_amount) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_debt, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val debt = debts[position]
        holder.tvName.text = debt.name
        holder.tvNote.text = debt.note
        val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(debt.amount)
        holder.tvAmount.text = "$formattedAmount ل.س"
    }
    override fun getItemCount() = debts.size
}