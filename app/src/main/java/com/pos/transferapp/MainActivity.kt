package com.pos.transferapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var dbHelper: DatabaseHelper
    lateinit var customerAdapter: CustomerAdapter
    lateinit var transactionAdapter: TransactionAdapter
    var customerList = ArrayList<Customer>()
    var transactionList = ArrayList<Transaction>()
    lateinit var autoAdapterTransfer: ArrayAdapter<String>
    lateinit var autoAdapterTrans: ArrayAdapter<String>
    var customerNamesList = ArrayList<String>()
    var currentQuickAmounts = ArrayList<Pair<String, String>>()
    
    private val CALL_REQUEST_CODE = 123
    private var pendingUssdCode = ""
    lateinit var tvTotalDebt: TextView

    // متغيرات لتتبع حالة التحويل وانتظار التأكيد
    var waitingForTransferConfirm = false
    var lastTransName = ""
    var lastTransAmount = 0
    var lastTransPrice = 0
    var lastTransNet = ""
    var executingPendingId = -1

    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.US)
        return sdf.format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dbHelper = DatabaseHelper(this)

        val tvMainTitle = findViewById(R.id.tv_main_title) as TextView
        val layoutTransfer = findViewById(R.id.layout_transfer) as View
        val layoutCustomers = findViewById(R.id.layout_customers) as View
        val layoutDebt = findViewById(R.id.layout_debt) as View
        val layoutSettings = findViewById(R.id.layout_settings) as View

        val navTransfer = findViewById(R.id.nav_transfer) as ViewGroup
        val navCustomers = findViewById(R.id.nav_customers) as ViewGroup
        val navDebt = findViewById(R.id.nav_debt) as ViewGroup
        val navSettings = findViewById(R.id.nav_settings) as ViewGroup

        fun updateNavUI(selected: String) {
            val orange = Color.parseColor("#FF6B00"); val gray = Color.parseColor("#888888")
            (navTransfer.getChildAt(0) as ImageView).setColorFilter(if (selected == "transfer") orange else gray, PorterDuff.Mode.SRC_IN)
            (navTransfer.getChildAt(1) as TextView).setTextColor(if (selected == "transfer") orange else gray)
            (navCustomers.getChildAt(0) as ImageView).setColorFilter(if (selected == "customers") orange else gray, PorterDuff.Mode.SRC_IN)
            (navCustomers.getChildAt(1) as TextView).setTextColor(if (selected == "customers") orange else gray)
            (navDebt.getChildAt(0) as ImageView).setColorFilter(if (selected == "debt") orange else gray, PorterDuff.Mode.SRC_IN)
            (navDebt.getChildAt(1) as TextView).setTextColor(if (selected == "debt") orange else gray)
            (navSettings.getChildAt(0) as ImageView).setColorFilter(if (selected == "settings") orange else gray, PorterDuff.Mode.SRC_IN)
            (navSettings.getChildAt(1) as TextView).setTextColor(if (selected == "settings") orange else gray)
        }

        navTransfer.setOnClickListener { layoutTransfer.visibility = View.VISIBLE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "التحويل السريع"; updateNavUI("transfer") }
        navCustomers.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.VISIBLE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "إدارة الزبائن"; updateNavUI("customers") }
        navDebt.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.VISIBLE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الحسابات والدفعات"; updateNavUI("debt") }
        navSettings.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.VISIBLE; tvMainTitle.text = "الإعدادات"; updateNavUI("settings") }

        // ================= 1. التحويل =================
        val inputTransferCustomer = findViewById(R.id.input_transfer_customer) as AutoCompleteTextView
        val rbSyr = findViewById(R.id.rb_syr) as RadioButton
        val rbMtn = findViewById(R.id.rb_mtn) as RadioButton
        val spinnerQuickAmounts = findViewById(R.id.spinner_quick_amounts) as Spinner
        val inputTransferAmount = findViewById(R.id.input_transfer_amount) as EditText
        val inputTransferPrice = findViewById(R.id.input_transfer_price) as EditText
        val btnExecuteTransfer = findViewById(R.id.btn_execute_transfer) as Button
        val btnShowPending = findViewById(R.id.btn_show_pending) as Button

        inputTransferCustomer.setOnClickListener { inputTransferCustomer.showDropDown() }
        autoAdapterTransfer = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        inputTransferCustomer.setAdapter(autoAdapterTransfer)

        fun updateSpinner(isSyr: Boolean) {
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val raw = if (isSyr) prefs.getString("syr_amounts", "1000:1300, 2000:2600") else prefs.getString("mtn_amounts", "1000:1300, 2000:2600")
            currentQuickAmounts = ArrayList()
            raw?.split(",")?.forEach { item ->
                val parts = item.trim().split(":")
                if (parts.size >= 2) currentQuickAmounts.add(Pair(parts[0].trim(), parts[1].trim()))
                else if (parts.size == 1 && parts[0].trim().isNotEmpty()) currentQuickAmounts.add(Pair(parts[0].trim(), parts[0].trim()))
            }
            val displayList = ArrayList<String>().apply { add("اختر الفئة...") }
            currentQuickAmounts.forEach { displayList.add("مبلغ: " + it.first + " رصيد | التكلفة: " + it.second + " ل.س") }
            spinnerQuickAmounts.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)
            inputTransferAmount.setText(""); inputTransferPrice.setText("")
        }

        rbSyr.setOnCheckedChangeListener { _, isChecked -> if(isChecked) updateSpinner(true) }
        rbMtn.setOnCheckedChangeListener { _, isChecked -> if(isChecked) updateSpinner(false) }

        spinnerQuickAmounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    inputTransferAmount.setText(currentQuickAmounts[position - 1].first)
                    inputTransferPrice.setText(currentQuickAmounts[position - 1].second)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnExecuteTransfer.setOnClickListener {
            val selectedName = inputTransferCustomer.text.toString().trim()
            val amountStr = inputTransferAmount.text.toString().trim()
            val priceStr = inputTransferPrice.text.toString().trim()
            val isSyr = rbSyr.isChecked
            val customer = customerList.find { it.name == selectedName }
            if (customer == null) { Toast.makeText(this, "اختر زبون مسجل", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val targetPhone = if (isSyr) customer.phoneSyriatel else customer.phoneMtn
            if (targetPhone.isEmpty()) { Toast.makeText(this, "لا يوجد رقم للشبكة المطلوبة", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            if (amountStr.isEmpty() || priceStr.isEmpty()) { Toast.makeText(this, "أدخل الرصيد والسعر", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val finalPin = prefs.getString("default_pin", "")?.takeIf { it.isNotEmpty() } ?: "0000"
            
            // حفظ البيانات لانتظار التأكيد
            lastTransName = selectedName
            lastTransAmount = amountStr.toIntOrNull() ?: 0
            lastTransPrice = priceStr.toIntOrNull() ?: 0
            lastTransNet = if(isSyr) "Syr" else "MTN"
            executingPendingId = -1 // عملية جديدة وليست من المعلقات
            waitingForTransferConfirm = true

            pendingUssdCode = "*150*" + targetPhone + "*" + lastTransAmount + "*" + finalPin + Uri.encode("#")
            checkPermissionAndCall()
        }

        btnShowPending.setOnClickListener { showPendingDialog() }

        // ================= الأقسام الأخرى (مختصرة للوضوح) =================
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList, dbHelper, 
        onEdit = { cust -> showEditCustomerDialog(cust) },
        onDelete = { cust ->
            AlertDialog.Builder(this).setTitle("حذف الزبون").setMessage("تأكيد الحذف؟").setPositiveButton("حذف") { _, _ ->
                if (dbHelper.deleteCustomer(cust.id)) { loadAllData() }
            }.setNegativeButton("إلغاء", null).show()
        })
        recyclerCustomers.adapter = customerAdapter

        findViewById<Button>(R.id.btn_save_customer).setOnClickListener {
            val name = (findViewById(R.id.input_cust_name) as EditText).text.toString().trim()
            val syriatel = (findViewById(R.id.input_cust_syriatel) as EditText).text.toString().trim()
            val mtn = (findViewById(R.id.input_cust_mtn) as EditText).text.toString().trim()
            if (name.isNotEmpty() && (syriatel.isNotEmpty() || mtn.isNotEmpty())) {
                if (dbHelper.addCustomer(name, syriatel, mtn)) {
                    (findViewById(R.id.input_cust_name) as EditText).text.clear(); (findViewById(R.id.input_cust_syriatel) as EditText).text.clear(); (findViewById(R.id.input_cust_mtn) as EditText).text.clear()
                    loadAllData()
                }
            } else { Toast.makeText(this, "أدخل الاسم ورقم واحد", Toast.LENGTH_SHORT).show() }
        }

        tvTotalDebt = findViewById(R.id.tv_total_debt) as TextView
        val inputTransCustomer = findViewById(R.id.input_trans_customer) as AutoCompleteTextView
        val rbDebt = findViewById(R.id.rb_debt) as RadioButton
        val inputTransAmount = findViewById(R.id.input_trans_amount) as EditText
        val inputTransNote = findViewById(R.id.input_trans_note) as EditText
        val btnSaveTrans = findViewById(R.id.btn_save_trans) as Button
        val recyclerTransactions = findViewById(R.id.recycler_transactions) as RecyclerView

        inputTransCustomer.setOnClickListener { inputTransCustomer.showDropDown() }
        autoAdapterTrans = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        inputTransCustomer.setAdapter(autoAdapterTrans)

        recyclerTransactions.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(transactionList, 
        onEdit = { trans -> showEditTransactionDialog(trans) },
        onDelete = { trans ->
            AlertDialog.Builder(this).setTitle("حذف الحركة").setMessage("تأكيد الحذف؟").setPositiveButton("حذف") { _, _ ->
                if (dbHelper.deleteTransaction(trans.id)) { loadAllData() }
            }.setNegativeButton("إلغاء", null).show()
        })
        recyclerTransactions.adapter = transactionAdapter

        btnSaveTrans.setOnClickListener {
            val name = inputTransCustomer.text.toString().trim()
            val amountStr = inputTransAmount.text.toString().trim()
            if (name.isNotEmpty() && amountStr.isNotEmpty()) {
                if (dbHelper.addTransaction(name, amountStr.toIntOrNull() ?: 0, if (rbDebt.isChecked) 1 else 2, inputTransNote.text.toString().trim(), getCurrentDateString())) {
                    inputTransCustomer.text.clear(); inputTransAmount.text.clear(); inputTransNote.text.clear(); loadAllData()
                }
            } else { Toast.makeText(this, "أدخل الاسم والمبلغ", Toast.LENGTH_SHORT).show() }
        }

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val inputSettingsPin = findViewById(R.id.input_settings_pin) as EditText
        val inputSettingsSyrAmounts = findViewById(R.id.input_settings_syr_amounts) as EditText
        val inputSettingsMtnAmounts = findViewById(R.id.input_settings_mtn_amounts) as EditText
        inputSettingsPin.setText(prefs.getString("default_pin", ""))
        inputSettingsSyrAmounts.setText(prefs.getString("syr_amounts", "1000:1300, 2000:2600, 5000:6500"))
        inputSettingsMtnAmounts.setText(prefs.getString("mtn_amounts", "1000:1250, 2000:2500, 5000:6250"))

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            prefs.edit().putString("default_pin", inputSettingsPin.text.toString().trim()).putString("syr_amounts", inputSettingsSyrAmounts.text.toString().trim()).putString("mtn_amounts", inputSettingsMtnAmounts.text.toString().trim()).apply()
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show(); updateSpinner(rbSyr.isChecked)
        }

        loadAllData()
        updateSpinner(true)
        navTransfer.performClick()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForTransferConfirm) {
            waitingForTransferConfirm = false
            showTransferConfirmDialog()
        }
    }

    private fun showTransferConfirmDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("تأكيد الحوالة")
        builder.setMessage("هل تمت حوالة " + lastTransAmount + " رصيد لـ " + lastTransName + " بنجاح؟")
        builder.setCancelable(false)
        builder.setPositiveButton("نعم (تسجيل دين)") { _, _ ->
            dbHelper.addTransaction(lastTransName, lastTransPrice, 1, "تحويل " + lastTransAmount + " رصيد (" + lastTransNet + ")", getCurrentDateString())
            if (executingPendingId != -1) { dbHelper.deletePending(executingPendingId) }
            Toast.makeText(this, "تم قيد " + lastTransPrice + " ل.س كدين", Toast.LENGTH_SHORT).show()
            executingPendingId = -1; loadAllData()
        }
        builder.setNeutralButton("تأجيل (للطلبات المعلقة)") { _, _ ->
            if (executingPendingId == -1) {
                dbHelper.addPending(lastTransName, lastTransNet, lastTransAmount, lastTransPrice, getCurrentDateString())
                Toast.makeText(this, "تم الحفظ في المعلقات", Toast.LENGTH_SHORT).show()
            } else { Toast.makeText(this, "تم إبقاء الطلب معلقاً", Toast.LENGTH_SHORT).show() }
            executingPendingId = -1; loadAllData()
        }
        builder.setNegativeButton("لا (إلغاء)") { _, _ -> executingPendingId = -1 }
        builder.show()
    }

    private fun showPendingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pending, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val recycler = view.findViewById(R.id.recycler_pending) as RecyclerView
        recycler.layoutManager = LinearLayoutManager(this)
        
        fun refreshPending() {
            val list = dbHelper.getAllPending()
            if (list.isEmpty()) { dialog.dismiss(); Toast.makeText(this, "لا يوجد طلبات معلقة", Toast.LENGTH_SHORT).show() }
            recycler.adapter = PendingAdapter(list, { pending -> 
                // زر تنفيذ
                val customer = customerList.find { it.name == pending.customerName }
                if (customer != null) {
                    val targetPhone = if (pending.network == "Syr") customer.phoneSyriatel else customer.phoneMtn
                    if (targetPhone.isNotEmpty()) {
                        lastTransName = pending.customerName; lastTransAmount = pending.amount; lastTransPrice = pending.price; lastTransNet = pending.network
                        executingPendingId = pending.id; waitingForTransferConfirm = true
                        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        val finalPin = prefs.getString("default_pin", "")?.takeIf { it.isNotEmpty() } ?: "0000"
                        pendingUssdCode = "*150*" + targetPhone + "*" + pending.amount + "*" + finalPin + Uri.encode("#")
                        checkPermissionAndCall()
                        dialog.dismiss()
                    } else { Toast.makeText(this, "رقم الزبون غير موجود", Toast.LENGTH_SHORT).show() }
                } else { Toast.makeText(this, "الزبون محذوف!", Toast.LENGTH_SHORT).show() }
            }, { pending -> 
                // زر حذف
                dbHelper.deletePending(pending.id); loadAllData(); refreshPending()
            })
        }
        refreshPending()
        dialog.show()
    }

    // دوال التعديل المختصرة للوضوح...
    private fun showEditCustomerDialog(customer: Customer) { /* نفس الكود السابق */ }
    private fun showEditTransactionDialog(trans: Transaction) { /* نفس الكود السابق */ }

    private fun loadAllData() {
        customerList.clear(); customerList.addAll(dbHelper.getAllCustomers()); customerAdapter.notifyDataSetChanged()
        customerNamesList.clear(); customerNamesList.addAll(customerList.map { it.name })
        autoAdapterTransfer.notifyDataSetChanged(); autoAdapterTrans.notifyDataSetChanged()
        transactionList.clear(); transactionList.addAll(dbHelper.getAllTransactions()); transactionAdapter.notifyDataSetChanged()
        tvTotalDebt.text = NumberFormat.getNumberInstance(Locale.US).format(dbHelper.getTotalDebt()) + " ل.س"
        
        val pendingCount = dbHelper.getAllPending().size
        (findViewById(R.id.btn_show_pending) as Button).text = "الطلبات المعلقة (" + pendingCount + ")"
    }

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else { executeUSSD() }
    }
    private fun executeUSSD() {
        if (pendingUssdCode.isNotEmpty()) { startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$pendingUssdCode") }); pendingUssdCode = "" }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { executeUSSD() }
    }
}

// Adapters
class PendingAdapter(private val items: List<PendingRequest>, private val onExecute: (PendingRequest) -> Unit, private val onDelete: (PendingRequest) -> Unit) : RecyclerView.Adapter<PendingAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_pend_name) as TextView
        val tvDetails = view.findViewById(R.id.tv_pend_details) as TextView
        val tvDate = view.findViewById(R.id.tv_pend_date) as TextView
        val btnExecute = view.findViewById(R.id.btn_pend_execute) as Button
        val btnDelete = view.findViewById(R.id.btn_pend_delete) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.customerName
        holder.tvDetails.text = "رصيد: " + item.amount + " | تكلفة: " + item.price + " (" + item.network + ")"
        holder.tvDate.text = item.date
        holder.btnExecute.setOnClickListener { onExecute(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
    override fun getItemCount() = items.size
}

class CustomerAdapter(private val customers: List<Customer>, private val dbHelper: DatabaseHelper, private val onEdit: (Customer) -> Unit, private val onDelete: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvSyriatel = view.findViewById(R.id.tv_syriatel) as TextView
        val tvMtn = view.findViewById(R.id.tv_mtn) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
        val tvBalance = view.findViewById(R.id.tv_customer_balance) as TextView
        val btnEdit = view.findViewById(R.id.btn_edit_cust) as ImageView
        val btnDelete = view.findViewById(R.id.btn_delete_cust) as ImageView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "Syr: " + customer.phoneSyriatel else "Syr: -"
        holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: " + customer.phoneMtn else "MTN: -"
        if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1)
        val balance = dbHelper.getCustomerBalance(customer.name)
        holder.tvBalance.text = NumberFormat.getNumberInstance(Locale.US).format(balance)
        holder.tvBalance.setTextColor(if(balance < 0) Color.parseColor("#388E3C") else Color.parseColor("#FF6B00"))
        holder.btnEdit.setOnClickListener { onEdit(customer) }; holder.btnDelete.setOnClickListener { onDelete(customer) }
    }
    override fun getItemCount() = customers.size
}

class TransactionAdapter(private val transactions: List<Transaction>, private val onEdit: (Transaction) -> Unit, private val onDelete: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_trans_name) as TextView
        val tvNote = view.findViewById(R.id.tv_trans_note) as TextView
        val tvDate = view.findViewById(R.id.tv_trans_date) as TextView
        val tvAmount = view.findViewById(R.id.tv_trans_amount) as TextView
        val tvType = view.findViewById(R.id.tv_trans_type) as TextView
        val btnEdit = view.findViewById(R.id.btn_edit_trans) as ImageView
        val btnDelete = view.findViewById(R.id.btn_delete_trans) as ImageView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trans = transactions[position]
        holder.tvName.text = trans.customerName; holder.tvNote.text = trans.note; holder.tvDate.text = trans.date
        holder.tvAmount.text = NumberFormat.getNumberInstance(Locale.US).format(trans.amount)
        if (trans.type == 1) { holder.tvType.text = "دين"; holder.tvType.setTextColor(Color.parseColor("#D32F2F")); holder.tvAmount.setTextColor(Color.parseColor("#D32F2F")) } 
        else { holder.tvType.text = "دفعة"; holder.tvType.setTextColor(Color.parseColor("#388E3C")); holder.tvAmount.setTextColor(Color.parseColor("#388E3C")) }
        holder.btnEdit.setOnClickListener { onEdit(trans) }; holder.btnDelete.setOnClickListener { onDelete(trans) }
    }
    override fun getItemCount() = transactions.size
}