package com.pos.transferapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
    
    var filterFromDate = ""
    var filterToDate = ""
    
    private val CALL_REQUEST_CODE = 123
    private var pendingUssdCode = ""

    var waitingForTransferConfirm = false
    var lastTransName = ""
    var lastTransAmount = 0
    var lastTransPrice = 0
    var lastTransNet = ""
    var executingPendingId = -1

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.US).format(Date())
    }

    // --- النسخ الاحتياطي التلقائي عند الخروج ---
    override fun onStop() {
        super.onStop()
        autoBackupDatabase()
    }

    private fun autoBackupDatabase() {
        try {
            val currentDB = getDatabasePath("TransferApp.db")
            val backupDir = getExternalFilesDir(null)
            if (backupDir != null && currentDB.exists()) {
                val backupDB = File(backupDir, "TransferApp_Backup.db")
                FileInputStream(currentDB).use { src ->
                    FileOutputStream(backupDB).use { dst ->
                        src.copyTo(dst)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareBackup() {
        try {
            autoBackupDatabase() // أخذ نسخة حديثة قبل المشاركة
            val backupDir = getExternalFilesDir(null)
            val backupDB = File(backupDir, "TransferApp_Backup.db")
            if (backupDB.exists()) {
                val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", backupDB)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "إرسال النسخة الاحتياطية عبر..."))
            } else {
                Toast.makeText(this, "لم يتم العثور على ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ أثناء المشاركة: " + e.message, Toast.LENGTH_SHORT).show()
        }
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
            if (targetPhone.isEmpty()) { Toast.makeText(this, "لا يوجد رقم للشبكة", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            if (amountStr.isEmpty() || priceStr.isEmpty()) { Toast.makeText(this, "أدخل الرصيد والسعر", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val finalPin = prefs.getString("default_pin", "")?.takeIf { it.isNotEmpty() } ?: "0000"
            
            lastTransName = selectedName; lastTransAmount = amountStr.toIntOrNull() ?: 0; lastTransPrice = priceStr.toIntOrNull() ?: 0; lastTransNet = if(isSyr) "Syr" else "MTN"
            executingPendingId = -1; waitingForTransferConfirm = true
            pendingUssdCode = "*150*" + targetPhone + "*" + lastTransAmount + "*" + finalPin + Uri.encode("#")
            checkPermissionAndCall()
        }

        btnShowPending.setOnClickListener { showPendingDialog() }

        // ================= 2. الزبائن =================
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList, dbHelper, onEdit = { cust -> showEditCustomerDialog(cust) }, onDelete = { cust ->
            AlertDialog.Builder(this).setTitle("حذف").setMessage("تأكيد الحذف؟").setPositiveButton("نعم") { _, _ -> if (dbHelper.deleteCustomer(cust.id)) loadAllData() }.setNegativeButton("إلغاء", null).show()
        })
        recyclerCustomers.adapter = customerAdapter

        val inputSearchCust = findViewById(R.id.input_search_cust) as EditText
        inputSearchCust.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().lowercase()
                customerAdapter.customers = if (q.isEmpty()) customerList else customerList.filter { it.name.lowercase().contains(q) }
                customerAdapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<Button>(R.id.btn_save_customer).setOnClickListener {
            val name = (findViewById(R.id.input_cust_name) as EditText).text.toString().trim()
            val syriatel = (findViewById(R.id.input_cust_syriatel) as EditText).text.toString().trim()
            val mtn = (findViewById(R.id.input_cust_mtn) as EditText).text.toString().trim()
            if (name.isNotEmpty() && (syriatel.isNotEmpty() || mtn.isNotEmpty())) {
                if (dbHelper.addCustomer(name, syriatel, mtn)) {
                    (findViewById(R.id.input_cust_name) as EditText).text.clear(); (findViewById(R.id.input_cust_syriatel) as EditText).text.clear(); (findViewById(R.id.input_cust_mtn) as EditText).text.clear()
                    loadAllData(); inputSearchCust.setText("")
                }
            } else { Toast.makeText(this, "أدخل الاسم ورقم", Toast.LENGTH_SHORT).show() }
        }

        // ================= 3. الحسابات =================
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
        transactionAdapter = TransactionAdapter(transactionList, onEdit = { trans -> showEditTransactionDialog(trans) }, onDelete = { trans ->
            AlertDialog.Builder(this).setTitle("حذف").setMessage("تأكيد الحذف؟").setPositiveButton("نعم") { _, _ -> if (dbHelper.deleteTransaction(trans.id)) loadAllData() }.setNegativeButton("إلغاء", null).show()
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

        val inputSearchTrans = findViewById(R.id.input_search_trans) as EditText
        val btnFilterDate = findViewById(R.id.btn_filter_date) as Button
        val tvActiveDateFilter = findViewById(R.id.tv_active_date_filter) as TextView

        fun applyTransactionFilters() {
            var filtered = transactionList.toList()
            val q = inputSearchTrans.text.toString().lowercase()
            if (q.isNotEmpty()) filtered = filtered.filter { it.customerName.lowercase().contains(q) }
            
            if (filterFromDate.isNotEmpty()) filtered = filtered.filter { it.date.substring(0, 10) >= filterFromDate }
            if (filterToDate.isNotEmpty()) filtered = filtered.filter { it.date.substring(0, 10) <= filterToDate }

            transactionAdapter.transactions = filtered
            transactionAdapter.notifyDataSetChanged()
            
            if (filterFromDate.isEmpty() && filterToDate.isEmpty()) { tvActiveDateFilter.visibility = View.GONE } 
            else { 
                tvActiveDateFilter.visibility = View.VISIBLE
                tvActiveDateFilter.text = "تاريخ: " + (if(filterFromDate.isEmpty()) "البداية" else filterFromDate) + " إلى " + (if(filterToDate.isEmpty()) "النهاية" else filterToDate)
            }
        }

        inputSearchTrans.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyTransactionFilters() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnFilterDate.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null)
            val dialog = AlertDialog.Builder(this).setView(view).create()
            val btnFrom = view.findViewById(R.id.btn_pick_from) as Button
            val btnTo = view.findViewById(R.id.btn_pick_to) as Button
            var tempFrom = filterFromDate; var tempTo = filterToDate
            if (tempFrom.isNotEmpty()) btnFrom.text = "من تاريخ: " + tempFrom
            if (tempTo.isNotEmpty()) btnTo.text = "إلى تاريخ: " + tempTo

            val cal = Calendar.getInstance()
            btnFrom.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempFrom = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnFrom.text = "من تاريخ: " + tempFrom }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
            btnTo.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempTo = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnTo.text = "إلى تاريخ: " + tempTo }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
            (view.findViewById(R.id.btn_clear_dates) as Button).setOnClickListener { filterFromDate = ""; filterToDate = ""; applyTransactionFilters(); dialog.dismiss() }
            (view.findViewById(R.id.btn_apply_dates) as Button).setOnClickListener { filterFromDate = tempFrom; filterToDate = tempTo; applyTransactionFilters(); dialog.dismiss() }
            dialog.show()
        }

        // ================= 4. الإعدادات والنسخ الاحتياطي =================
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val inputSettingsPin = findViewById(R.id.input_settings_pin) as EditText
        val inputSettingsSyrAmounts = findViewById(R.id.input_settings_syr_amounts) as EditText
        val inputSettingsMtnAmounts = findViewById(R.id.input_settings_mtn_amounts) as EditText
        val btnShareBackup = findViewById(R.id.btn_share_backup) as Button

        inputSettingsPin.setText(prefs.getString("default_pin", ""))
        inputSettingsSyrAmounts.setText(prefs.getString("syr_amounts", "1000:1300, 2000:2600, 5000:6500"))
        inputSettingsMtnAmounts.setText(prefs.getString("mtn_amounts", "1000:1250, 2000:2500, 5000:6250"))

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            prefs.edit().putString("default_pin", inputSettingsPin.text.toString().trim()).putString("syr_amounts", inputSettingsSyrAmounts.text.toString().trim()).putString("mtn_amounts", inputSettingsMtnAmounts.text.toString().trim()).apply()
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show(); updateSpinner(rbSyr.isChecked)
        }

        // تشغيل المشاركة اليدوية
        btnShareBackup.setOnClickListener {
            shareBackup()
        }

        loadAllData()
        updateSpinner(true)
        navTransfer.performClick()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForTransferConfirm) { waitingForTransferConfirm = false; showTransferConfirmDialog() }
    }

    private fun showTransferConfirmDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("تأكيد الحوالة").setMessage("هل تمت حوالة " + lastTransAmount + " رصيد لـ " + lastTransName + " بنجاح؟").setCancelable(false)
        builder.setPositiveButton("نعم (تسجيل دين)") { _, _ ->
            dbHelper.addTransaction(lastTransName, lastTransPrice, 1, "تحويل " + lastTransAmount + " رصيد (" + lastTransNet + ")", getCurrentDateString())
            if (executingPendingId != -1) { dbHelper.deletePending(executingPendingId) }
            Toast.makeText(this, "تم قيد " + lastTransPrice + " ل.س كدين", Toast.LENGTH_SHORT).show()
            executingPendingId = -1; loadAllData()
        }
        builder.setNeutralButton("تأجيل (معلقات)") { _, _ ->
            if (executingPendingId == -1) { dbHelper.addPending(lastTransName, lastTransNet, lastTransAmount, lastTransPrice, getCurrentDateString()); Toast.makeText(this, "حُفظ بالمعلقات", Toast.LENGTH_SHORT).show() }
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
            if (list.isEmpty()) { dialog.dismiss(); Toast.makeText(this, "لا يوجد طلبات", Toast.LENGTH_SHORT).show() }
            recycler.adapter = PendingAdapter(list, { pending -> 
                val customer = customerList.find { it.name == pending.customerName }
                if (customer != null) {
                    val targetPhone = if (pending.network == "Syr") customer.phoneSyriatel else customer.phoneMtn
                    if (targetPhone.isNotEmpty()) {
                        lastTransName = pending.customerName; lastTransAmount = pending.amount; lastTransPrice = pending.price; lastTransNet = pending.network
                        executingPendingId = pending.id; waitingForTransferConfirm = true
                        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        val finalPin = prefs.getString("default_pin", "")?.takeIf { it.isNotEmpty() } ?: "0000"
                        pendingUssdCode = "*150*" + targetPhone + "*" + pending.amount + "*" + finalPin + Uri.encode("#")
                        checkPermissionAndCall(); dialog.dismiss()
                    } else { Toast.makeText(this, "رقم الزبون غير موجود", Toast.LENGTH_SHORT).show() }
                } else { Toast.makeText(this, "الزبون محذوف!", Toast.LENGTH_SHORT).show() }
            }, { pending -> dbHelper.deletePending(pending.id); loadAllData(); refreshPending() })
        }
        refreshPending(); dialog.show()
    }

    private fun showEditCustomerDialog(customer: Customer) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_cust, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val inputName = view.findViewById(R.id.edit_cust_name) as EditText
        val inputSyr = view.findViewById(R.id.edit_cust_syr) as EditText
        val inputMtn = view.findViewById(R.id.edit_cust_mtn) as EditText
        inputName.setText(customer.name); inputSyr.setText(customer.phoneSyriatel); inputMtn.setText(customer.phoneMtn)
        (view.findViewById(R.id.btn_update_cust) as Button).setOnClickListener {
            val n = inputName.text.toString().trim(); val s = inputSyr.text.toString().trim(); val m = inputMtn.text.toString().trim()
            if (n.isNotEmpty() && (s.isNotEmpty() || m.isNotEmpty())) {
                if (dbHelper.updateCustomer(customer.id, customer.name, n, s, m)) { Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show(); loadAllData(); dialog.dismiss() }
            } else { Toast.makeText(this, "البيانات غير مكتملة", Toast.LENGTH_SHORT).show() }
        }
        dialog.show()
    }

    private fun showEditTransactionDialog(trans: Transaction) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_trans, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        (view.findViewById(R.id.tv_edit_trans_name) as TextView).text = "الزبون: " + trans.customerName
        val rbDebt = view.findViewById(R.id.rb_edit_debt) as RadioButton
        val rbPayment = view.findViewById(R.id.rb_edit_payment) as RadioButton
        if (trans.type == 1) rbDebt.isChecked = true else rbPayment.isChecked = true
        val inputAmount = view.findViewById(R.id.edit_trans_amount) as EditText
        val inputNote = view.findViewById(R.id.edit_trans_note) as EditText
        inputAmount.setText(trans.amount.toString()); inputNote.setText(trans.note)
        (view.findViewById(R.id.btn_update_trans) as Button).setOnClickListener {
            val a = inputAmount.text.toString().trim().toIntOrNull() ?: 0
            if (a > 0) {
                if (dbHelper.updateTransaction(trans.id, a, if (rbDebt.isChecked) 1 else 2, inputNote.text.toString().trim())) { Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show(); loadAllData(); dialog.dismiss() }
            } else { Toast.makeText(this, "أدخل مبلغ صحيح", Toast.LENGTH_SHORT).show() }
        }
        dialog.show()
    }

    private fun loadAllData() {
        customerList.clear(); customerList.addAll(dbHelper.getAllCustomers())
        val qCust = (findViewById(R.id.input_search_cust) as EditText).text.toString().lowercase()
        customerAdapter.customers = if(qCust.isEmpty()) customerList else customerList.filter { it.name.lowercase().contains(qCust) }
        customerAdapter.notifyDataSetChanged()

        customerNamesList.clear(); customerNamesList.addAll(customerList.map { it.name })
        autoAdapterTransfer.notifyDataSetChanged(); autoAdapterTrans.notifyDataSetChanged()

        transactionList.clear(); transactionList.addAll(dbHelper.getAllTransactions())
        
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        val todayTrans = transactionList.filter { it.date.startsWith(todayStr) }
        val todayDebt = todayTrans.filter { it.type == 1 }.sumOf { it.amount }
        val todayPayment = todayTrans.filter { it.type == 2 }.sumOf { it.amount }
        (findViewById(R.id.tv_today_debt) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(todayDebt) + " ل.س"
        (findViewById(R.id.tv_today_payment) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(todayPayment) + " ل.س"
        
        (findViewById(R.id.tv_total_debt) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(dbHelper.getTotalDebt()) + " ل.س"
        val pendingCount = dbHelper.getAllPending().size
        (findViewById(R.id.btn_show_pending) as Button).text = "الطلبات المعلقة (" + pendingCount + ")"

        val qTrans = (findViewById(R.id.input_search_trans) as EditText).text.toString().lowercase()
        var filteredTrans = transactionList.toList()
        if (qTrans.isNotEmpty()) filteredTrans = filteredTrans.filter { it.customerName.lowercase().contains(qTrans) }
        if (filterFromDate.isNotEmpty()) filteredTrans = filteredTrans.filter { it.date.substring(0, 10) >= filterFromDate }
        if (filterToDate.isNotEmpty()) filteredTrans = filteredTrans.filter { it.date.substring(0, 10) <= filterToDate }
        transactionAdapter.transactions = filteredTrans
        transactionAdapter.notifyDataSetChanged()
    }

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
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

class PendingAdapter(var items: List<PendingRequest>, private val onExecute: (PendingRequest) -> Unit, private val onDelete: (PendingRequest) -> Unit) : RecyclerView.Adapter<PendingAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_pend_name) as TextView; val tvDetails = view.findViewById(R.id.tv_pend_details) as TextView; val tvDate = view.findViewById(R.id.tv_pend_date) as TextView; val btnExecute = view.findViewById(R.id.btn_pend_execute) as Button; val btnDelete = view.findViewById(R.id.btn_pend_delete) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.customerName; holder.tvDetails.text = "رصيد: " + item.amount + " | تكلفة: " + item.price + " (" + item.network + ")"
        holder.tvDate.text = item.date; holder.btnExecute.setOnClickListener { onExecute(item) }; holder.btnDelete.setOnClickListener { onDelete(item) }
    }
    override fun getItemCount() = items.size
}

class CustomerAdapter(var customers: List<Customer>, private val dbHelper: DatabaseHelper, private val onEdit: (Customer) -> Unit, private val onDelete: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView; val tvSyriatel = view.findViewById(R.id.tv_syriatel) as TextView; val tvMtn = view.findViewById(R.id.tv_mtn) as TextView; val tvInitial = view.findViewById(R.id.tv_initial) as TextView; val tvBalance = view.findViewById(R.id.tv_customer_balance) as TextView; val btnEdit = view.findViewById(R.id.btn_edit_cust) as ImageView; val btnDelete = view.findViewById(R.id.btn_delete_cust) as ImageView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name; holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "Syr: " + customer.phoneSyriatel else "Syr: -"; holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: " + customer.phoneMtn else "MTN: -"
        if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1)
        val balance = dbHelper.getCustomerBalance(customer.name)
        holder.tvBalance.text = NumberFormat.getNumberInstance(Locale.US).format(balance)
        holder.tvBalance.setTextColor(if(balance < 0) Color.parseColor("#388E3C") else Color.parseColor("#FF6B00"))
        holder.btnEdit.setOnClickListener { onEdit(customer) }; holder.btnDelete.setOnClickListener { onDelete(customer) }
    }
    override fun getItemCount() = customers.size
}

class TransactionAdapter(var transactions: List<Transaction>, private val onEdit: (Transaction) -> Unit, private val onDelete: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_trans_name) as TextView; val tvNote = view.findViewById(R.id.tv_trans_note) as TextView; val tvDate = view.findViewById(R.id.tv_trans_date) as TextView; val tvAmount = view.findViewById(R.id.tv_trans_amount) as TextView; val tvType = view.findViewById(R.id.tv_trans_type) as TextView; val btnEdit = view.findViewById(R.id.btn_edit_trans) as ImageView; val btnDelete = view.findViewById(R.id.btn_delete_trans) as ImageView
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