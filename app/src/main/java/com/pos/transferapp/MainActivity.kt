package com.pos.transferapp
import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    lateinit var dbHelper: DatabaseHelper
    lateinit var customerAdapter: CustomerAdapter
    lateinit var transactionAdapter: TransactionAdapter
    private lateinit var companyLogAdapter: CompanyLogAdapter
    private lateinit var companyLogCompanySpinner: Spinner
    private lateinit var companyLogFilterSpinner: Spinner
    var customerList = ArrayList<Customer>()
    var transactionList = ArrayList<Transaction>()
    lateinit var autoAdapterTransfer: ArrayAdapter<String>
    lateinit var autoAdapterTrans: ArrayAdapter<String>
    var customerNamesList = ArrayList<String>()
    var currentQuickAmounts = ArrayList<ServiceCategory>()
    var isCustListVisible = false
    var isTransListVisible = false
    var filterFromDate = ""
    var filterToDate = ""
    private val CALL_REQUEST_CODE = 123
    private var pendingUssdCode = ""
    private var backupShareInProgress = false
    var waitingForTransferConfirm = false
    private var transferConfirmationDialogVisible = false
    private var restoreTransferConfirmation = false
    var lastTransName = ""
    var lastTransAmount = 0
    var lastTransPrice = 0
    var lastTransNet = ""
    var executingPendingId = -1
    var lastServiceType = ""
    val amountKeys = arrayOf("syr_amounts", "mtn_amounts", "cham_amounts", "syr_bill_amounts", "mtn_bill_amounts")
    val servicesArray = arrayOf("رصيد Syriatel", "رصيد MTN", "شام كاش", "فاتورة Syriatel", "فاتورة MTN")
    val transServicesArray = arrayOf("دفعة عامة", "رصيد Syriatel", "رصيد MTN", "شام كاش", "فاتورة Syriatel", "فاتورة MTN")
    val filterServicesArray = arrayOf("جميع الخدمات", "دفعة عامة", "رصيد Syriatel", "رصيد MTN", "شام كاش", "فاتورة Syriatel", "فاتورة MTN")

    private fun getCurrentDateString(): String { return SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.US).format(Date()) }
    override fun onStop() {
        super.onStop()
        if (backupShareInProgress) return
        try {
            DatabaseBackup.createBackup(this)
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Automatic database backup failed on activity stop", error)
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "Automatic database backup access was denied", error)
        }
    }
    private fun shareBackup() {
        val backupDB = try {
            DatabaseBackup.createBackup(this)
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Could not create database backup for sharing", error)
            Toast.makeText(this, "تعذر إنشاء نسخة احتياطية: ${error.message}", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", backupDB)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            backupShareInProgress = true
            startActivity(Intent.createChooser(shareIntent, "إرسال النسخة الاحتياطية عبر..."))
        } catch (error: ActivityNotFoundException) {
            backupShareInProgress = false
            Toast.makeText(this, "لا يوجد تطبيق لمشاركة النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        } catch (error: IllegalArgumentException) {
            backupShareInProgress = false
            Log.e("DatabaseBackup", "Could not create backup sharing URI", error)
            Toast.makeText(this, "تعذر تجهيز النسخة الاحتياطية للمشاركة", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            backupShareInProgress = false
            Log.e("DatabaseBackup", "Could not access the backup file for sharing", error)
            Toast.makeText(this, "تعذر الوصول إلى النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        if (initialPrefs.getBoolean("dark_mode", false)) { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) } else { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            pendingUssdCode = state.getString("pending_ussd_code", "")
            waitingForTransferConfirm = state.getBoolean("waiting_for_transfer_confirm", false)
            transferConfirmationDialogVisible = state.getBoolean("transfer_confirmation_dialog_visible", false)
            restoreTransferConfirmation = transferConfirmationDialogVisible
            lastTransName = state.getString("last_trans_name", "")
            lastTransAmount = state.getInt("last_trans_amount", 0)
            lastTransPrice = state.getInt("last_trans_price", 0)
            lastTransNet = state.getString("last_trans_net", "")
            lastServiceType = state.getString("last_service_type", "")
            executingPendingId = state.getInt("executing_pending_id", -1)
        }
        setContentView(R.layout.activity_main)
        dbHelper = DatabaseHelper(this)
        migrateLegacyCategories(initialPrefs)
        DatabaseBackup.scheduleDaily(this)
        val tvMainTitle = findViewById(R.id.tv_main_title) as TextView
        val layoutTransfer = findViewById(R.id.layout_transfer) as View
        val layoutCustomers = findViewById(R.id.layout_customers) as View
        val layoutDebt = findViewById(R.id.layout_debt) as View
        val layoutCompanies = findViewById(R.id.layout_companies) as View
        val layoutSettings = findViewById(R.id.layout_settings) as View
        val navTransfer = findViewById(R.id.nav_transfer) as ViewGroup
        val navCustomers = findViewById(R.id.nav_customers) as ViewGroup
        val navDebt = findViewById(R.id.nav_debt) as ViewGroup
        val navCompanies = findViewById(R.id.nav_companies) as ViewGroup
        val navSettings = findViewById(R.id.nav_settings) as ViewGroup

        companyLogCompanySpinner = findViewById(R.id.spinner_company_log_company)
        companyLogFilterSpinner = findViewById(R.id.spinner_company_log_filter)
        val companyLogRecycler = findViewById<RecyclerView>(R.id.recycler_company_log)
        companyLogAdapter = CompanyLogAdapter(emptyList())
        companyLogRecycler.layoutManager = LinearLayoutManager(this)
        companyLogRecycler.adapter = companyLogAdapter
        companyLogCompanySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            servicesArray.toList()
        )
        companyLogFilterSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("الكل", "شحن رصيد", "دفعات")
        )
        companyLogCompanySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshCompanyLog()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        companyLogFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshCompanyLog()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val companyLogContent = findViewById<View>(R.id.layout_company_log_content)
        val companyLogToggleIcon = findViewById<TextView>(R.id.tv_company_log_toggle_icon)
        findViewById<View>(R.id.btn_toggle_company_log).setOnClickListener {
            val expanded = companyLogContent.visibility != View.VISIBLE
            companyLogContent.visibility = if (expanded) View.VISIBLE else View.GONE
            companyLogToggleIcon.text = if (expanded) "▲" else "▼"
        }

        fun updateNavUI(selected: String) {
            val primaryColor = ContextCompat.getColor(this@MainActivity, R.color.colorPrimary)
            val grayColor = ContextCompat.getColor(this@MainActivity, R.color.colorTextSecondary)
            (navTransfer.getChildAt(0) as ImageView).setColorFilter(if (selected == "transfer") primaryColor else grayColor, PorterDuff.Mode.SRC_IN)
            (navTransfer.getChildAt(1) as TextView).setTextColor(if (selected == "transfer") primaryColor else grayColor)
            (navCustomers.getChildAt(0) as ImageView).setColorFilter(if (selected == "customers") primaryColor else grayColor, PorterDuff.Mode.SRC_IN)
            (navCustomers.getChildAt(1) as TextView).setTextColor(if (selected == "customers") primaryColor else grayColor)
            (navDebt.getChildAt(0) as ImageView).setColorFilter(if (selected == "debt") primaryColor else grayColor, PorterDuff.Mode.SRC_IN)
            (navDebt.getChildAt(1) as TextView).setTextColor(if (selected == "debt") primaryColor else grayColor)
            (navCompanies.getChildAt(0) as ImageView).setColorFilter(if (selected == "companies") primaryColor else grayColor, PorterDuff.Mode.SRC_IN)
            (navCompanies.getChildAt(1) as TextView).setTextColor(if (selected == "companies") primaryColor else grayColor)
            (navSettings.getChildAt(0) as ImageView).setColorFilter(if (selected == "settings") primaryColor else grayColor, PorterDuff.Mode.SRC_IN)
            (navSettings.getChildAt(1) as TextView).setTextColor(if (selected == "settings") primaryColor else grayColor)
        }

        navTransfer.setOnClickListener { layoutTransfer.visibility = View.VISIBLE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "تحويل"; updateNavUI("transfer") }
        navCustomers.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.VISIBLE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "إدارة الزبائن"; updateNavUI("customers") }
        navDebt.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.VISIBLE; layoutCompanies.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الحسابات والدفعات"; updateNavUI("debt") }
        navCompanies.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.VISIBLE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الشركات والموردين"; updateNavUI("companies"); val recyclerCompanies = findViewById<RecyclerView>(R.id.recycler_companies); recyclerCompanies.layoutManager = LinearLayoutManager(this@MainActivity); recyclerCompanies.adapter = CompanyAdapter(servicesArray.toList(), dbHelper, this@MainActivity); refreshCompanyLog() }
        navSettings.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutSettings.visibility = View.VISIBLE; tvMainTitle.text = "الإعدادات"; updateNavUI("settings") }

        val inputTransferCustomer = findViewById(R.id.input_transfer_customer) as AutoCompleteTextView
        val spinnerServiceType = findViewById<Spinner>(R.id.spinner_service_type)
        val spinnerQuickAmounts = findViewById(R.id.spinner_quick_amounts) as Spinner
        val inputTransferAmount = findViewById(R.id.input_transfer_amount) as EditText
        val inputTransferPrice = findViewById(R.id.input_transfer_price) as EditText
        val tvBalanceWarning = findViewById<TextView>(R.id.tv_balance_warning)
        val layoutDirectActions = findViewById<View>(R.id.layout_direct_actions)
        val btnDoneDebt = findViewById<Button>(R.id.btn_done_debt)
        val btnShowPending = findViewById(R.id.btn_show_pending) as Button
        val btnAddPending = findViewById<Button>(R.id.btn_add_pending)
        val spinnerTransService = findViewById<Spinner>(R.id.spinner_trans_service)
        val spinnerFilterService = findViewById<Spinner>(R.id.spinner_filter_service)

        spinnerServiceType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, servicesArray)
        spinnerTransService.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, transServicesArray)
        spinnerFilterService.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterServicesArray)

        inputTransferCustomer.setOnTouchListener { _, _ -> inputTransferCustomer.showDropDown(); false }

        fun validateTransferAmount() {
            val amountStr = inputTransferAmount.text.toString().trim()
            val amount = amountStr.toLongOrNull() ?: 0L
            val selectedService = spinnerServiceType.selectedItem?.toString()
            if (selectedService == null) {
                tvBalanceWarning.visibility = View.GONE
                btnDoneDebt.isEnabled = true
                btnDoneDebt.alpha = 1.0f
                btnAddPending.isEnabled = true
                btnAddPending.alpha = 1.0f
                return
            }
            val available = dbHelper.getCompanyAvailableBalance(selectedService)
            if (amountStr.isNotEmpty() && amount > available) {
                tvBalanceWarning.visibility = View.VISIBLE
                tvBalanceWarning.text = "لا يمكن التحويل لعدم وجود رصيد كافي. الرصيد المتبقي هو: " + NumberFormat.getNumberInstance(Locale.US).format(available) + " (" + selectedService + ")"
                btnDoneDebt.isEnabled = false; btnDoneDebt.alpha = 0.5f
                btnAddPending.isEnabled = true; btnAddPending.alpha = 1.0f
            } else {
                tvBalanceWarning.visibility = View.GONE
                btnDoneDebt.isEnabled = true; btnDoneDebt.alpha = 1.0f
                btnAddPending.isEnabled = true; btnAddPending.alpha = 1.0f
            }
        }

        inputTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateTransferAmount() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fun updateSpinner(serviceIdx: Int) {
            if (serviceIdx !in servicesArray.indices) return
            updateQuickCategorySpinner(serviceIdx)
        }

        fun hasCategoryPriceConflict(serviceName: String, amount: Long, price: Long): Boolean {
            val savedCategory = dbHelper.getCategoriesByService(serviceName)
                .firstOrNull { it.amount == amount }
            return savedCategory != null && savedCategory.price != price
        }

        inputTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val amount = s?.toString()?.trim()?.toLongOrNull()
                val serviceName = spinnerServiceType.selectedItem?.toString()
                val category = if (amount != null && serviceName != null) {
                    dbHelper.getCategoriesByService(serviceName).firstOrNull { it.amount == amount }
                } else {
                    null
                }
                val suggestedPrice = category?.price?.toString().orEmpty()
                if (inputTransferPrice.text.toString() != suggestedPrice) {
                    inputTransferPrice.text.clear()
                    if (suggestedPrice.isNotEmpty()) {
                        inputTransferPrice.setText(suggestedPrice)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        spinnerServiceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSpinner(position); inputTransferAmount.setText(""); inputTransferPrice.setText(""); validateTransferAmount()
                layoutDirectActions.visibility = View.VISIBLE
                btnDoneDebt.text = "تحويل / تنفيذ"
                btnAddPending.text = "حفظ في المعلقات"
                btnAddPending.visibility = View.VISIBLE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerQuickAmounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position - 1 in currentQuickAmounts.indices) {
                    val category = currentQuickAmounts[position - 1]
                    inputTransferAmount.setText(category.amount.toString())
                    inputTransferPrice.setText(category.price.toString())
                }
                validateTransferAmount()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fun applyTransactionFilters() {
            var filtered = transactionList.toList()
            val q = findViewById<AutoCompleteTextView>(R.id.input_search_trans).text.toString().lowercase()
            val filterSvcIdx = spinnerFilterService.selectedItemPosition
            if (q.isNotEmpty()) filtered = filtered.filter { it.customerName.lowercase().contains(q) }
            if (filterFromDate.isNotEmpty()) filtered = filtered.filter { it.date.take(10) >= filterFromDate }
            if (filterToDate.isNotEmpty()) filtered = filtered.filter { it.date.take(10) <= filterToDate }
            if (filterSvcIdx > 0) {
                val targetSvc = filterServicesArray[filterSvcIdx]
                filtered = filtered.filter { it.serviceType == targetSvc || (targetSvc == "دفعة عامة" && it.serviceType.isEmpty() && it.type == 2) }
            }
            transactionAdapter.transactions = filtered; transactionAdapter.notifyDataSetChanged()
            val hasSearch = q.isNotEmpty() || filterFromDate.isNotEmpty() || filterToDate.isNotEmpty() || filterSvcIdx > 0
            findViewById<RecyclerView>(R.id.recycler_transactions).visibility = if (hasSearch || isTransListVisible) View.VISIBLE else View.GONE
            val tvActiveDateFilter = findViewById<TextView>(R.id.tv_active_date_filter)
            if (filterFromDate.isEmpty() && filterToDate.isEmpty()) { tvActiveDateFilter.visibility = View.GONE } else { tvActiveDateFilter.visibility = View.VISIBLE; tvActiveDateFilter.text = "تاريخ: " + (if(filterFromDate.isEmpty()) "البداية" else filterFromDate) + " إلى " + (if(filterToDate.isEmpty()) "النهاية" else filterToDate) }
        }
        
        spinnerFilterService.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { applyTransactionFilters() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnDoneDebt.setOnClickListener {
            val selectedName = inputTransferCustomer.text.toString().trim()
            val amountStr = inputTransferAmount.text.toString().trim()
            val priceStr = inputTransferPrice.text.toString().trim()
            val svcIdx = spinnerServiceType.selectedItemPosition
            if (svcIdx !in servicesArray.indices) {
                Toast.makeText(this, "اختر فئة صحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val customer = customerList.find { it.name == selectedName }
            if (customer == null) { Toast.makeText(this, "اختر زبون مسجل", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (amountStr.isEmpty() || priceStr.isEmpty()) { Toast.makeText(this, "أدخل الرصيد والسعر", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val amount = amountStr.toLongOrNull()
            val price = priceStr.toLongOrNull()
            if (amount == null || price == null || amount <= 0L || price <= 0L || amount > Int.MAX_VALUE || price > Int.MAX_VALUE) {
                Toast.makeText(this, "أدخل رصيداً وسعراً صحيحين", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serviceName = servicesArray[svcIdx]
            if (hasCategoryPriceConflict(serviceName, amount, price)) {
                Toast.makeText(
                    this,
                    "هذه الفئة مسجلة مسبقاً. لا يمكن تعديل سعرها من هنا، يرجى التعديل من إدارة الفئات في الإعدادات",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val available = dbHelper.getCompanyAvailableBalance(serviceName)
            if (amount > available) {
                Toast.makeText(this, "لا يمكن التنفيذ؛ رصيد الشركة الحالي غير كافٍ", Toast.LENGTH_LONG).show()
                validateTransferAmount()
                return@setOnClickListener
            }

            if (svcIdx > 1) {
                val note = "تحويل $amount ($serviceName)"
                if (dbHelper.addTransaction(selectedName, price.toInt(), 1, note, getCurrentDateString(), serviceName, amount.toInt())) {
                    rememberQuickCategory(serviceName, amount, price)
                    Toast.makeText(this, "تم قيد $price ل.س كدين", Toast.LENGTH_SHORT).show()
                    resetTransferUI()
                    loadAllData()
                } else {
                    Toast.makeText(this, "تعذر تسجيل الحركة", Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }

            val isSyr = svcIdx == 0
            val targetPhone = if (isSyr) customer.phoneSyriatel else customer.phoneMtn
            if (targetPhone.isEmpty()) { Toast.makeText(this, "لا يوجد رقم للشبكة", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val pin = if(isSyr) prefs.getString("syr_pin", "0000") else prefs.getString("mtn_pin", "0000")
            val codeTemplate = if(isSyr) prefs.getString("syr_code", "*150*رقم*مبلغ*رمز#") else prefs.getString("mtn_code", "*150*رقم*مبلغ*رمز#")
            val safePin = if (pin.isNullOrEmpty()) "0000" else pin
            val safeTemplate = if (codeTemplate.isNullOrEmpty()) "*150*رقم*مبلغ*رمز#" else codeTemplate
            var ussd = safeTemplate.replace("رقم", targetPhone).replace("مبلغ", amountStr).replace("رمز", safePin)
            if (ussd.endsWith("#")) ussd = ussd.dropLast(1)
            pendingUssdCode = ussd + Uri.encode("#")
            lastTransName = selectedName; lastTransAmount = amountStr.toIntOrNull() ?: 0; lastTransPrice = priceStr.toIntOrNull() ?: 0; lastTransNet = if(isSyr) "Syr" else "MTN"
            lastServiceType = servicesArray[svcIdx]
            executingPendingId = -1; checkPermissionAndCall()
        }

        btnAddPending.setOnClickListener {
            val selectedName = inputTransferCustomer.text.toString().trim()
            val amountStr = inputTransferAmount.text.toString().trim()
            val priceStr = inputTransferPrice.text.toString().trim()
            val svcIdx = spinnerServiceType.selectedItemPosition
            if (svcIdx !in servicesArray.indices) {
                Toast.makeText(this, "اختر فئة صحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serviceName = servicesArray[svcIdx]
            val customer = customerList.find { it.name == selectedName }
            if (customer == null) { Toast.makeText(this, "اختر زبون مسجل", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (amountStr.isEmpty() || priceStr.isEmpty()) { Toast.makeText(this, "أدخل الرصيد والسعر", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val priceValue = priceStr.toLongOrNull()
            val amountValue = amountStr.toLongOrNull()
            if (priceValue == null || amountValue == null ||
                priceValue <= 0L || amountValue <= 0L ||
                priceValue > Int.MAX_VALUE || amountValue > Int.MAX_VALUE
            ) {
                Toast.makeText(this, "أدخل رصيداً وسعراً صحيحين", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasCategoryPriceConflict(serviceName, amountValue, priceValue)) {
                Toast.makeText(
                    this,
                    "هذه الفئة مسجلة مسبقاً. لا يمكن تعديل سعرها من هنا، يرجى التعديل من إدارة الفئات في الإعدادات",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            val price = priceValue.toInt()
            val amount = amountValue.toInt()
            val net = "Direct"
            if (dbHelper.addPending(selectedName, net, amount, price, getCurrentDateString(), serviceName, amount)) { rememberQuickCategory(serviceName, amount.toLong(), price.toLong()); Toast.makeText(this, "تم الحفظ في المعلقات", Toast.LENGTH_SHORT).show(); resetTransferUI(); loadAllData() }
            else { Toast.makeText(this, "تعذر حفظ الطلب؛ تحقق من الرصيد والسعر", Toast.LENGTH_LONG).show() }
        }

        btnShowPending.setOnClickListener { showPendingDialog() }
        val btnToggleCustomers = findViewById(R.id.btn_toggle_customers) as Button
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        val inputSearchCust = findViewById(R.id.input_search_cust) as EditText
        val btnToggleSearchCust = findViewById(R.id.btn_toggle_search_cust) as ImageView
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList, dbHelper, onEdit = { cust -> showEditCustomerDialog(cust) }, onDelete = { cust -> AlertDialog.Builder(this).setTitle("حذف").setMessage("تأكيد الحذف؟").setPositiveButton("نعم") { _, _ -> if (dbHelper.deleteCustomer(cust.id)) loadAllData() }.setNegativeButton("إلغاء", null).show() }, onPdf = { cust -> showStatementDateDialog(cust) })
        recyclerCustomers.adapter = customerAdapter
        btnToggleCustomers.setOnClickListener { isCustListVisible = !isCustListVisible; btnToggleCustomers.text = if (isCustListVisible) "الزبائن المسجلين ▲" else "الزبائن المسجلين ▼"; recyclerCustomers.visibility = if (isCustListVisible || inputSearchCust.text.isNotEmpty()) View.VISIBLE else View.GONE }
        btnToggleSearchCust.setOnClickListener { inputSearchCust.visibility = if (inputSearchCust.visibility == View.VISIBLE) { inputSearchCust.setText(""); View.GONE } else View.VISIBLE }
        inputSearchCust.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { val q = s.toString().lowercase(); customerAdapter.customers = if (q.isEmpty()) customerList else customerList.filter { it.name.lowercase().contains(q) }; customerAdapter.notifyDataSetChanged(); recyclerCustomers.visibility = if (q.isNotEmpty() || isCustListVisible) View.VISIBLE else View.GONE }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        findViewById<Button>(R.id.btn_save_customer).setOnClickListener {
            val name = (findViewById(R.id.input_cust_name) as EditText).text.toString().trim(); val syriatel = (findViewById(R.id.input_cust_syriatel) as EditText).text.toString().trim(); val mtn = (findViewById(R.id.input_cust_mtn) as EditText).text.toString().trim()
            if (name.isNotEmpty() && (syriatel.isNotEmpty() || mtn.isNotEmpty())) { if (dbHelper.addCustomer(name, syriatel, mtn)) { (findViewById(R.id.input_cust_name) as EditText).text.clear(); (findViewById(R.id.input_cust_syriatel) as EditText).text.clear(); (findViewById(R.id.input_cust_mtn) as EditText).text.clear(); loadAllData() } } else { Toast.makeText(this, "أدخل الاسم ورقم", Toast.LENGTH_SHORT).show() }
        }
        val btnToggleSummary = findViewById(R.id.btn_toggle_summary) as Button
        val layoutSummaryCards = findViewById(R.id.layout_summary_cards) as View
        btnToggleSummary.setOnClickListener { val isVisible = layoutSummaryCards.visibility == View.VISIBLE; layoutSummaryCards.visibility = if (isVisible) View.GONE else View.VISIBLE; btnToggleSummary.text = if (isVisible) "ملخص الحسابات ▼" else "ملخص الحسابات ▲" }
        val btnToggleRecords = findViewById(R.id.btn_toggle_records) as Button
        val recyclerTransactions = findViewById(R.id.recycler_transactions) as RecyclerView
        val inputSearchTrans = findViewById<AutoCompleteTextView>(R.id.input_search_trans)
        val recordsContent = findViewById<ViewGroup>(R.id.layout_records_content)
        val btnFilterDate = findViewById(R.id.btn_filter_date) as Button
        val btnToggleSearchTrans = findViewById(R.id.btn_toggle_search_trans) as ImageView
        val layoutSearchTrans = findViewById(R.id.layout_search_trans) as View
        recyclerTransactions.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(transactionList, onEdit = { trans -> showEditTransactionDialog(trans) }, onDelete = { trans -> AlertDialog.Builder(this).setTitle("حذف").setMessage("تأكيد الحذف؟").setPositiveButton("نعم") { _, _ -> if (dbHelper.deleteTransaction(trans.id)) loadAllData() }.setNegativeButton("إلغاء", null).show() })
        recyclerTransactions.adapter = transactionAdapter
        btnToggleRecords.setOnClickListener {
            isTransListVisible = !isTransListVisible
            recordsContent.visibility = if (isTransListVisible) View.VISIBLE else View.GONE
            btnToggleRecords.text = if (isTransListVisible) "السجلات ▲" else "السجلات ▼"
            if (isTransListVisible) {
                applyTransactionFilters()
            } else {
                inputSearchTrans.text.clear()
                filterFromDate = ""
                filterToDate = ""
                spinnerFilterService.setSelection(0)
                layoutSearchTrans.visibility = View.GONE
                findViewById<TextView>(R.id.tv_active_date_filter).visibility = View.GONE
                applyTransactionFilters()
                inputSearchTrans.clearFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(inputSearchTrans.windowToken, 0)
            }
        }
        btnToggleSearchTrans.setOnClickListener {
            layoutSearchTrans.visibility =
                if (layoutSearchTrans.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (layoutSearchTrans.visibility == View.GONE) {
                inputSearchTrans.text.clear()
                filterFromDate = ""
                filterToDate = ""
                spinnerFilterService.setSelection(0)
                applyTransactionFilters()
            } else {
                inputSearchTrans.requestFocus()
            }
        }
        inputSearchTrans.addTextChangedListener(object : TextWatcher { override fun afterTextChanged(s: Editable?) { applyTransactionFilters() }; override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} })
        btnFilterDate.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null); val dialog = AlertDialog.Builder(this).setView(view).create(); val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title); tvTitle.text = "فلترة حسب التاريخ"; val btnFrom = view.findViewById(R.id.btn_pick_from) as Button; val btnTo = view.findViewById(R.id.btn_pick_to) as Button; var tempFrom = filterFromDate; var tempTo = filterToDate; if (tempFrom.isNotEmpty()) btnFrom.text = "من تاريخ: " + tempFrom; if (tempTo.isNotEmpty()) btnTo.text = "إلى تاريخ: " + tempTo
            val cal = Calendar.getInstance(); btnFrom.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempFrom = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnFrom.text = "من تاريخ: " + tempFrom }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }; btnTo.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempTo = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnTo.text = "إلى تاريخ: " + tempTo }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }
            (view.findViewById(R.id.btn_clear_dates) as Button).setOnClickListener { filterFromDate = ""; filterToDate = ""; applyTransactionFilters(); dialog.dismiss() }; (view.findViewById(R.id.btn_apply_dates) as Button).setOnClickListener { filterFromDate = tempFrom; filterToDate = tempTo; applyTransactionFilters(); dialog.dismiss() }; dialog.show()
        }
        val inputTransCustomer = findViewById(R.id.input_trans_customer) as AutoCompleteTextView; val inputTransAmount = findViewById(R.id.input_trans_amount) as EditText; val inputTransNote = findViewById(R.id.input_trans_note) as EditText; val btnSaveTrans = findViewById(R.id.btn_save_trans) as Button
        inputTransCustomer.setOnTouchListener { _, _ -> inputTransCustomer.showDropDown(); false }
        btnSaveTrans.setOnClickListener {
            val name = inputTransCustomer.text.toString().trim(); val amountStr = inputTransAmount.text.toString().trim(); val svcName = transServicesArray[spinnerTransService.selectedItemPosition]
            if (name.isNotEmpty() && amountStr.isNotEmpty()) { if (dbHelper.addTransaction(name, amountStr.toIntOrNull() ?: 0, 2, inputTransNote.text.toString().trim(), getCurrentDateString(), svcName)) { inputTransCustomer.text.clear(); inputTransAmount.text.clear(); inputTransNote.text.clear(); loadAllData(); Toast.makeText(this, "تم تسجيل الدفعة بنجاح", Toast.LENGTH_SHORT).show() } } else { Toast.makeText(this, "أدخل الاسم والمبلغ", Toast.LENGTH_SHORT).show() }
        }
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val inputSyrPin = findViewById<EditText>(R.id.input_settings_syr_pin)
        val inputSyrCode = findViewById<EditText>(R.id.input_settings_syr_code)
        val inputMtnPin = findViewById<EditText>(R.id.input_settings_mtn_pin)
        val inputMtnCode = findViewById<EditText>(R.id.input_settings_mtn_code)
        inputSyrPin.setText(prefs.getString("syr_pin", ""))
        inputSyrCode.setText(prefs.getString("syr_code", "*150*رقم*مبلغ*رمز#"))
        inputMtnPin.setText(prefs.getString("mtn_pin", ""))
        inputMtnCode.setText(prefs.getString("mtn_code", "*150*رقم*مبلغ*رمز#"))
        fun saveSettingOnChange(input: EditText, key: String) {
            input.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    prefs.edit().putString(key, s?.toString().orEmpty()).apply()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        saveSettingOnChange(inputSyrPin, "syr_pin")
        saveSettingOnChange(inputSyrCode, "syr_code")
        saveSettingOnChange(inputMtnPin, "mtn_pin")
        saveSettingOnChange(inputMtnCode, "mtn_code")
        findViewById<Button>(R.id.btn_manage_categories).setOnClickListener {
            showManageCategoriesDialog { updateSpinner(spinnerServiceType.selectedItemPosition) }
        }
        findViewById<Button>(R.id.btn_share_backup).setOnClickListener { shareBackup() }
        val switchDarkModeId = resources.getIdentifier("switch_dark_mode", "id", packageName)
        if (switchDarkModeId != 0) {
            val switchDarkMode = findViewById<Switch>(switchDarkModeId); switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
            switchDarkMode.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("dark_mode", isChecked).apply(); if (isChecked) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
        }
        loadAllData(); updateSpinner(0); navTransfer.performClick()
    }

    private fun migrateLegacyCategories(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean("service_categories_migrated", false)) return
        val defaultValues = arrayOf(
            "1000:1300, 2000:2600",
            "1000:1250, 1500:1900",
            "",
            "",
            ""
        )
        servicesArray.forEachIndexed { index, serviceName ->
            if (dbHelper.getCategoriesByService(serviceName).isEmpty()) {
                val legacy = prefs.getString(amountKeys[index], defaultValues[index]).orEmpty()
                legacy.split(",").forEach { entry ->
                    val parts = entry.trim().split(":")
                    val amount = parts.getOrNull(0)?.trim()?.toLongOrNull()
                    val price = parts.getOrNull(1)?.trim()?.toLongOrNull()
                    if (amount != null && price != null && amount > 0L && price > 0L) {
                        dbHelper.addCategory(serviceName, amount, price)
                    }
                }
            }
        }
        prefs.edit().putBoolean("service_categories_migrated", true).apply()
    }

    private fun updateQuickCategorySpinner(serviceIndex: Int) {
        if (serviceIndex !in servicesArray.indices) return
        currentQuickAmounts = ArrayList(dbHelper.getCategoriesByService(servicesArray[serviceIndex]))
        val displayList = ArrayList<String>().apply { add("اختر الفئة...") }
        currentQuickAmounts.forEach { category ->
            displayList.add(
                "الرصيد: ${NumberFormat.getNumberInstance(Locale.US).format(category.amount)} | السعر: ${NumberFormat.getNumberInstance(Locale.US).format(category.price)} ل.س"
            )
        }
        findViewById<Spinner>(R.id.spinner_quick_amounts).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)
    }

    private fun rememberQuickCategory(serviceName: String, amount: Long, price: Long) {
        if (amount <= 0L || price <= 0L) return
        val existingCategory = dbHelper.getCategoriesByService(serviceName).firstOrNull { it.amount == amount }
        val saved = if (existingCategory == null) {
            dbHelper.addCategory(serviceName, amount, price)
        } else {
            dbHelper.updateCategory(existingCategory.id, serviceName, amount, price)
        }
        if (!saved) return

        val serviceSpinner = findViewById<Spinner>(R.id.spinner_service_type)
        if (serviceSpinner.selectedItem?.toString() == serviceName) {
            updateQuickCategorySpinner(serviceSpinner.selectedItemPosition)
        }
    }

    private fun resetTransferUI() {
        findViewById<EditText>(R.id.input_transfer_amount).text.clear()
        findViewById<EditText>(R.id.input_transfer_price).text.clear()
        findViewById<Spinner>(R.id.spinner_quick_amounts).setSelection(0)
    }

    private fun showManageCategoriesDialog(onCategoriesChanged: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_categories, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val serviceSpinner = view.findViewById<Spinner>(R.id.spinner_category_service)
        val inputAmount = view.findViewById<EditText>(R.id.input_category_amount)
        val inputPrice = view.findViewById<EditText>(R.id.input_category_price)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_categories)
        val serviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, servicesArray)
        serviceSpinner.adapter = serviceAdapter
        recycler.layoutManager = LinearLayoutManager(this)
        var refreshCategories: () -> Unit = {}
        val categoryAdapter = CategoryAdapter(
            emptyList(),
            onEdit = { category ->
                val editLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(24, 8, 24, 0)
                }
                val editAmount = EditText(this).apply {
                    hint = "الرصيد / الكمية"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setText(category.amount.toString())
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorTextPrimary))
                }
                val editPrice = EditText(this).apply {
                    hint = "السعر"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    setText(category.price.toString())
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorTextPrimary))
                }
                editLayout.addView(editAmount)
                editLayout.addView(editPrice)
                val editDialog = AlertDialog.Builder(this)
                    .setTitle("تعديل الفئة")
                    .setView(editLayout)
                    .setPositiveButton("حفظ", null)
                    .setNegativeButton("إلغاء", null)
                    .create()
                editDialog.setOnShowListener {
                    editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val amount = editAmount.text.toString().trim().toLongOrNull()
                        val price = editPrice.text.toString().trim().toLongOrNull()
                        if (amount == null || price == null || amount <= 0L || price <= 0L) {
                            Toast.makeText(this, "أدخل رصيداً وسعراً صحيحين", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (!dbHelper.updateCategory(category.id, category.serviceName, amount, price)) {
                            Toast.makeText(this, "تعذر تعديل الفئة", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        refreshCategories()
                        onCategoriesChanged()
                        editDialog.dismiss()
                    }
                }
                editDialog.show()
            },
            onDelete = { category ->
                AlertDialog.Builder(this)
                    .setTitle("حذف الفئة")
                    .setMessage("هل تريد حذف هذه الفئة؟")
                    .setPositiveButton("حذف") { _, _ ->
                        if (dbHelper.deleteCategory(category.id)) {
                            refreshCategories()
                            onCategoriesChanged()
                        } else {
                            Toast.makeText(this, "تعذر حذف الفئة", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        )
        recycler.adapter = categoryAdapter

        refreshCategories = {
            val serviceName = serviceSpinner.selectedItem as? String
            if (serviceName != null) {
                categoryAdapter.categories = dbHelper.getCategoriesByService(serviceName)
                categoryAdapter.notifyDataSetChanged()
            }
        }

        serviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, selectedView: View?, position: Int, id: Long) {
                refreshCategories()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        view.findViewById<Button>(R.id.btn_category_add).setOnClickListener {
            val serviceName = serviceSpinner.selectedItem as? String ?: return@setOnClickListener
            val amount = inputAmount.text.toString().trim().toLongOrNull()
            val price = inputPrice.text.toString().trim().toLongOrNull()
            if (amount == null || price == null || amount <= 0L || price <= 0L) {
                Toast.makeText(this, "أدخل رصيداً وسعراً صحيحين", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!dbHelper.addCategory(serviceName, amount, price)) {
                Toast.makeText(this, "تعذر إضافة الفئة", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            inputAmount.text.clear()
            inputPrice.text.clear()
            refreshCategories()
            onCategoriesChanged()
        }
        dialog.show()
    }
    fun showCompanyChargeDialog(compName: String, onComplete: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_company_charge, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tv_charge_title).text = "شحن رصيد: " + compName
        val inpAmount = view.findViewById<EditText>(R.id.input_charge_amount)
        val inpCost = view.findViewById<EditText>(R.id.input_charge_cost)
        val inpNote = view.findViewById<EditText>(R.id.input_charge_note)
        view.findViewById<Button>(R.id.btn_save_charge).setOnClickListener {
            val amount = inpAmount.text.toString().toIntOrNull() ?: 0
            val cost = inpCost.text.toString().toIntOrNull() ?: 0
            if (amount <= 0 || cost <= 0) {
                Toast.makeText(this, "أدخل كمية وتكلفة صحيحتين أكبر من صفر", Toast.LENGTH_SHORT).show()
            } else if (dbHelper.addCompanyPurchase(compName, amount, cost, inpNote.text.toString().trim(), getCurrentDateString())) {
                Toast.makeText(this, "تم الشحن بنجاح", Toast.LENGTH_SHORT).show()
                onComplete()
                refreshCompanyLog()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "تعذر حفظ عملية الشحن", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }
    fun showCompanyPayDialog(compName: String, onComplete: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_company_payment, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tv_payment_title).text = "تسديد دفعة: " + compName
        val inpAmount = view.findViewById<EditText>(R.id.input_payment_amount)
        val inpNote = view.findViewById<EditText>(R.id.input_payment_note)
        view.findViewById<Button>(R.id.btn_save_payment).setOnClickListener {
            val amount = inpAmount.text.toString().toIntOrNull() ?: 0
            if (amount <= 0) {
                Toast.makeText(this, "أدخل مبلغاً أكبر من صفر", Toast.LENGTH_SHORT).show()
            } else if (dbHelper.addCompanyPayment(compName, amount, inpNote.text.toString().trim(), getCurrentDateString())) {
                Toast.makeText(this, "تم التسديد بنجاح", Toast.LENGTH_SHORT).show()
                onComplete()
                refreshCompanyLog()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "تعذر حفظ الدفعة", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }
    fun showCompanyReportDialog(compName: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_company_report, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tv_report_title).text = "تقرير المبيعات: " + compName
        val btnFrom = view.findViewById<Button>(R.id.btn_rep_from)
        val btnTo = view.findViewById<Button>(R.id.btn_rep_to)
        var fromDate = ""
        var toDate = ""
        val cal = Calendar.getInstance()
        btnFrom.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                fromDate = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnFrom.text = "من تاريخ: " + fromDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnTo.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                toDate = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnTo.text = "إلى تاريخ: " + toDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        val resultsLayout = view.findViewById<View>(R.id.layout_rep_results)
        val creditView = view.findViewById<TextView>(R.id.tv_rep_credit)
        val financialView = view.findViewById<TextView>(R.id.tv_rep_financial)
        val countView = view.findViewById<TextView>(R.id.tv_rep_count)
        view.findViewById<Button>(R.id.btn_rep_calc).setOnClickListener {
            val report = dbHelper.getCompanySalesReport(compName, fromDate, toDate)
            creditView.text = "إجمالي الرصيد المحول: " + NumberFormat.getNumberInstance(Locale.US).format(report.first)
            financialView.text = "إجمالي الدين المالي: " + NumberFormat.getNumberInstance(Locale.US).format(report.second) + " ل.س"
            countView.text = "عدد العمليات: " + dbHelper.getCompanySalesCount(compName, fromDate, toDate)
            resultsLayout.visibility = View.VISIBLE
        }
        view.findViewById<Button>(R.id.btn_rep_export_pdf).setOnClickListener {
            exportCompanyReportToPDF(compName, fromDate, toDate)
        }
        view.findViewById<Button>(R.id.btn_rep_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
    private fun exportCompanyReportToPDF(companyName: String, fromDate: String, toDate: String) {
        val entries = dbHelper.getCompanyDetailedReport(companyName, fromDate, toDate)
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 45f
            val right = pageWidth - margin
            val numberFormat = NumberFormat.getNumberInstance(Locale.US)
            val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = Color.BLACK
                textAlign = Paint.Align.RIGHT
            }
            val boldPaint = Paint(regularPaint).apply { isFakeBoldText = true }
            val titlePaint = Paint(boldPaint).apply {
                textSize = 20f
                color = Color.rgb(15, 76, 92)
            }
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                strokeWidth = 0.7f
            }
            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = 0f

            fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
                if (text.isEmpty()) return listOf("")
                val lines = ArrayList<String>()
                var line = StringBuilder()
                for (word in text.split(Regex("\\s+"))) {
                    val candidate = if (line.isEmpty()) word else "${line} $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        line = StringBuilder(candidate)
                    } else {
                        if (line.isNotEmpty()) {
                            lines.add(line.toString())
                            line = StringBuilder()
                        }
                        if (paint.measureText(word) <= maxWidth) {
                            line.append(word)
                        } else {
                            var fragment = StringBuilder()
                            for (character in word) {
                                if (paint.measureText("$fragment$character") > maxWidth && fragment.isNotEmpty()) {
                                    lines.add(fragment.toString())
                                    fragment = StringBuilder()
                                }
                                fragment.append(character)
                            }
                            line = fragment
                        }
                    }
                }
                if (line.isNotEmpty()) lines.add(line.toString())
                return lines.ifEmpty { listOf("") }
            }

            fun drawPageHeader() {
                y = 44f
                canvas.drawText("السجل التفصيلي للشركة", right, y, titlePaint)
                y += 26f
                canvas.drawText("الشركة: $companyName", right, y, boldPaint)
                y += 20f
                val period = if (fromDate.isEmpty() && toDate.isEmpty()) {
                    "كافة التواريخ"
                } else {
                    "${fromDate.ifEmpty { "البداية" }} - ${toDate.ifEmpty { "النهاية" }}"
                }
                canvas.drawText("الفترة: $period", right, y, regularPaint)
                y += 28f
                canvas.drawRect(margin, y - 16f, right, y + 8f, Paint().apply { color = Color.rgb(235, 238, 240) })
                boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText("التاريخ", 550f, y, boldPaint)
                canvas.drawText("العملية", 450f, y, boldPaint)
                canvas.drawText("المبلغ / الكمية", 318f, y, boldPaint)
                canvas.drawText("التفاصيل والملاحظة", 185f, y, boldPaint)
                y += 14f
                canvas.drawLine(margin, y, right, y, gridPaint)
                y += 18f
                boldPaint.color = Color.BLACK
            }

            fun startNextPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPageHeader()
            }

            drawPageHeader()
            var purchaseCost = 0L
            var purchaseQuantity = 0L
            var payments = 0L
            var salesValue = 0L
            var salesQuantity = 0L
            for (entry in entries) {
                val amountLines = when (entry.type) {
                    "شحن رصيد" -> {
                        purchaseCost += entry.amount
                        purchaseQuantity += entry.quantity
                        listOf(
                            "التكلفة: ${numberFormat.format(entry.amount)} ل.س",
                            "الكمية: ${numberFormat.format(entry.quantity)}"
                        )
                    }
                    "دفعة للشركة" -> {
                        payments += entry.amount
                        listOf("المدفوع: ${numberFormat.format(entry.amount)} ل.س")
                    }
                    else -> {
                        salesValue += entry.amount
                        salesQuantity += entry.quantity
                        listOf(
                            "القيمة: ${numberFormat.format(entry.amount)} ل.س",
                            "الكمية: ${numberFormat.format(entry.quantity)}"
                        )
                    }
                }
                val noteLines = wrapText(entry.note.trim(), regularPaint, 132f)
                val rowHeight = maxOf(amountLines.size, noteLines.size, 1) * 14f + 12f
                if (y + rowHeight > 770f) startNextPage()
                val firstBaseline = y + 10f
                canvas.drawText(entry.date.take(10), 550f, firstBaseline, regularPaint)
                canvas.drawText(entry.type, 450f, firstBaseline, regularPaint)
                amountLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 318f, firstBaseline + index * 14f, regularPaint)
                }
                noteLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 185f, firstBaseline + index * 14f, regularPaint)
                }
                y += rowHeight
                canvas.drawLine(margin, y - 3f, right, y - 3f, gridPaint)
            }
            if (entries.isEmpty()) {
                regularPaint.color = Color.GRAY
                canvas.drawText("لا توجد حركات ضمن الفترة المحددة", right, y + 12f, regularPaint)
                regularPaint.color = Color.BLACK
                y += 26f
            }

            if (y > 680f) startNextPage()
            val summaryRows = listOf(
                "إجمالي مشتريات الرصيد: ${numberFormat.format(purchaseQuantity)} كمية | ${numberFormat.format(purchaseCost)} ل.س تكلفة",
                "إجمالي دفعات الشركة: ${numberFormat.format(payments)} ل.س",
                "إجمالي مبيعات الزبائن: ${numberFormat.format(salesQuantity)} كمية | ${numberFormat.format(salesValue)} ل.س",
                "الدين المتبقي للشركة: ${numberFormat.format(purchaseCost - payments)} ل.س"
            )
            val summaryTop = y + 12f
            val summaryBottom = summaryTop + summaryRows.size * 23f + 16f
            canvas.drawRect(margin, summaryTop, right, summaryBottom, Paint().apply {
                color = Color.rgb(247, 248, 249)
                style = Paint.Style.FILL
            })
            canvas.drawRect(margin, summaryTop, right, summaryBottom, Paint().apply {
                color = Color.rgb(15, 76, 92)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            boldPaint.textSize = 11f
            boldPaint.color = Color.BLACK
            summaryRows.forEachIndexed { index, summary ->
                val baseline = summaryTop + 21f + index * 23f
                if (index == summaryRows.lastIndex) boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText(summary, right - 10f, baseline, boldPaint)
            }
            pdfDocument.finishPage(page)

            val outputDirectory = getExternalFilesDir(null)
                ?: throw IOException("تعذر الوصول إلى مجلد حفظ التقرير")
            val safeCompanyName = companyName.replace("[^\\p{L}\\p{N}_-]".toRegex(), "_").take(40).ifBlank { "company" }
            val pdfFile = File(outputDirectory, "Company_Report_${safeCompanyName}.pdf")
            FileOutputStream(pdfFile).use { output -> pdfDocument.writeTo(output) }
            val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة تقرير الشركة"))
        } catch (error: IOException) {
            Log.e("CompanyReport", "Could not generate company report PDF", error)
            Toast.makeText(this, "تعذر إنشاء تقرير PDF: ${error.message}", Toast.LENGTH_LONG).show()
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "لا يوجد تطبيق لمشاركة ملف PDF", Toast.LENGTH_LONG).show()
        } catch (error: IllegalArgumentException) {
            Log.e("CompanyReport", "Could not create PDF sharing URI", error)
            Toast.makeText(this, "تعذر تجهيز تقرير PDF للمشاركة", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e("CompanyReport", "Could not access company report PDF for sharing", error)
            Toast.makeText(this, "تعذر الوصول إلى ملف تقرير PDF", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }
    private fun showStatementDateDialog(customer: Customer) { val view = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null); val dialog = AlertDialog.Builder(this).setView(view).create(); val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title); tvTitle.text = "كشف حساب: " + customer.name; val btnFrom = view.findViewById(R.id.btn_pick_from) as Button; val btnTo = view.findViewById(R.id.btn_pick_to) as Button; var tempFrom = ""; var tempTo = ""; val cal = Calendar.getInstance(); btnFrom.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempFrom = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnFrom.text = "من تاريخ: " + tempFrom }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }; btnTo.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempTo = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnTo.text = "إلى تاريخ: " + tempTo }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }; (view.findViewById(R.id.btn_clear_dates) as Button).setOnClickListener { generateAndSharePDF(customer, "", ""); dialog.dismiss() }; (view.findViewById(R.id.btn_apply_dates) as Button).setOnClickListener { generateAndSharePDF(customer, tempFrom, tempTo); dialog.dismiss() }; dialog.show() }
    private fun refreshCompanyLog() {
        if (!::companyLogAdapter.isInitialized || !::companyLogCompanySpinner.isInitialized || !::companyLogFilterSpinner.isInitialized) return
        val companyName = companyLogCompanySpinner.selectedItem as? String ?: return
        val filterType = companyLogFilterSpinner.selectedItem as? String ?: "الكل"
        val entries = dbHelper.getCompanyLog(companyName, filterType)
        companyLogAdapter.entries = entries
        companyLogAdapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tv_company_log_empty).visibility =
            if (entries.isEmpty()) View.VISIBLE else View.GONE
    }
    private fun generateAndSharePDF(customer: Customer, fromDate: String, toDate: String) {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 45f
            val right = pageWidth - margin
            val numberFormat = NumberFormat.getNumberInstance(Locale.US)
            val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = Color.BLACK
                textAlign = Paint.Align.RIGHT
            }
            val boldPaint = Paint(regularPaint).apply { isFakeBoldText = true }
            val titlePaint = Paint(boldPaint).apply {
                textSize = 20f
                color = Color.rgb(15, 76, 92)
            }
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY
                strokeWidth = 0.7f
            }
            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = 0f

            fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
                if (text.isEmpty()) return listOf("")
                val lines = ArrayList<String>()
                var line = StringBuilder()
                for (word in text.split(Regex("\\s+"))) {
                    val candidate = if (line.isEmpty()) word else "${line} $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        line = StringBuilder(candidate)
                    } else {
                        if (line.isNotEmpty()) {
                            lines.add(line.toString())
                            line = StringBuilder()
                        }
                        if (paint.measureText(word) <= maxWidth) {
                            line.append(word)
                        } else {
                            var fragment = StringBuilder()
                            for (character in word) {
                                if (paint.measureText("$fragment$character") > maxWidth && fragment.isNotEmpty()) {
                                    lines.add(fragment.toString())
                                    fragment = StringBuilder()
                                }
                                fragment.append(character)
                            }
                            line = fragment
                        }
                    }
                }
                if (line.isNotEmpty()) lines.add(line.toString())
                return lines.ifEmpty { listOf("") }
            }

            fun drawTableHeader() {
                canvas.drawRect(margin, y - 16f, right, y + 8f, Paint().apply { color = Color.rgb(235, 238, 240) })
                boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText("التاريخ", 550f, y, boldPaint)
                canvas.drawText("النوع", 450f, y, boldPaint)
                canvas.drawText("المبلغ (ل.س)", 350f, y, boldPaint)
                canvas.drawText("الفئة والتفاصيل", 185f, y, boldPaint)
                y += 14f
                canvas.drawLine(margin, y, right, y, gridPaint)
                y += 18f
                boldPaint.color = Color.BLACK
            }

            fun drawPageHeader() {
                y = 42f
                canvas.drawText("كشف حساب زبون", right, y, titlePaint)
                y += 26f
                canvas.drawText("اسم الزبون: ${customer.name}", right, y, boldPaint)
                y += 20f
                val dateText = if (fromDate.isNotEmpty() || toDate.isNotEmpty()) {
                    "الفترة: ${fromDate.ifEmpty { "البداية" }} إلى ${toDate.ifEmpty { "النهاية" }}"
                } else {
                    "الفترة: جميع الحركات السابقة"
                }
                canvas.drawText(dateText, right, y, regularPaint)
                y += 30f
                drawTableHeader()
            }

            fun startNextPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPageHeader()
            }

            drawPageHeader()
            var filtered = dbHelper.getAllTransactions().filter { it.customerName == customer.name }.sortedBy { it.id }
            if (fromDate.isNotEmpty()) filtered = filtered.filter { it.date.take(10) >= fromDate }
            if (toDate.isNotEmpty()) filtered = filtered.filter { it.date.take(10) <= toDate }

            var totalDebt = 0L
            var totalPay = 0L
            for (transaction in filtered) {
                if (transaction.type == 1) totalDebt += transaction.amount.toLong() else totalPay += transaction.amount.toLong()
                val typeLabel = if (transaction.type == 1) "دين" else "دفعة"
                val serviceLabel = transaction.serviceType.ifBlank { if (transaction.type == 2) "دفعة عامة" else "رصيد" }
                val details = "[$serviceLabel]" + transaction.note.trim().takeIf { it.isNotEmpty() }?.let { " - $it" }.orEmpty()
                val detailLines = wrapText(details, regularPaint, 130f)
                val rowHeight = detailLines.size * 14f + 12f
                if (y + rowHeight > 755f) startNextPage()

                val firstBaseline = y + 10f
                regularPaint.color = Color.DKGRAY
                canvas.drawText(transaction.date.take(10), 550f, firstBaseline, regularPaint)
                boldPaint.color = if (transaction.type == 1) Color.RED else Color.rgb(56, 142, 60)
                canvas.drawText(typeLabel, 450f, firstBaseline, boldPaint)
                regularPaint.color = Color.BLACK
                canvas.drawText(numberFormat.format(transaction.amount.toLong()), 350f, firstBaseline, regularPaint)
                detailLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 185f, firstBaseline + index * 14f, regularPaint)
                }
                y += rowHeight
                canvas.drawLine(margin, y - 3f, right, y - 3f, gridPaint)
            }
            if (filtered.isEmpty()) {
                regularPaint.color = Color.GRAY
                canvas.drawText("لا توجد حركات ضمن الفترة المحددة", right, y + 12f, regularPaint)
                regularPaint.color = Color.BLACK
                y += 26f
            }

            if (y > 660f) startNextPage()
            val net = totalDebt - totalPay
            val summaryTop = y + 12f
            val summaryHeight = 3 * 27f + 18f
            canvas.drawRect(margin, summaryTop, right, summaryTop + summaryHeight, Paint().apply {
                color = Color.rgb(247, 248, 249)
                style = Paint.Style.FILL
            })
            canvas.drawRect(margin, summaryTop, right, summaryTop + summaryHeight, Paint().apply {
                color = Color.rgb(15, 76, 92)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            })
            boldPaint.textSize = 12f
            boldPaint.color = Color.RED
            canvas.drawText("إجمالي الديون: ${numberFormat.format(totalDebt)} ل.س", right - 12f, summaryTop + 23f, boldPaint)
            boldPaint.color = Color.rgb(56, 142, 60)
            canvas.drawText("إجمالي الدفعات: ${numberFormat.format(totalPay)} ل.س", right - 12f, summaryTop + 50f, boldPaint)
            boldPaint.textSize = 14f
            boldPaint.color = if (net > 0) Color.RED else Color.rgb(0, 121, 107)
            val netLabel = when {
                net > 0 -> "الرصيد المتبقي لنا"
                net < 0 -> "الرصيد المتبقي للزبون"
                else -> "الحساب متوازن"
            }
            canvas.drawText("$netLabel: ${numberFormat.format(kotlin.math.abs(net))} ل.س", right - 12f, summaryTop + 77f, boldPaint)
            pdfDocument.finishPage(page)

            val backupDir = getExternalFilesDir(null)
            if (backupDir == null) {
                Toast.makeText(this, "تعذر الوصول إلى مجلد حفظ كشف الحساب", Toast.LENGTH_LONG).show()
                return
            }
            val safeName = customer.name.replace(" ", "_")
            val pdfFile = File(backupDir, "Statement_" + safeName + ".pdf")
            FileOutputStream(pdfFile).use { output -> pdfDocument.writeTo(output) }
            val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "إرسال كشف الحساب إلى الواتساب..."))
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تصميم الـ PDF: " + e.message, Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }
    override fun onResume() {
        super.onResume()
        backupShareInProgress = false
        if (waitingForTransferConfirm) {
            waitingForTransferConfirm = false
            showTransferConfirmDialog()
        } else if (restoreTransferConfirmation) {
            restoreTransferConfirmation = false
            showTransferConfirmDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_ussd_code", pendingUssdCode)
        outState.putBoolean("waiting_for_transfer_confirm", waitingForTransferConfirm)
        outState.putBoolean("transfer_confirmation_dialog_visible", transferConfirmationDialogVisible)
        outState.putString("last_trans_name", lastTransName)
        outState.putInt("last_trans_amount", lastTransAmount)
        outState.putInt("last_trans_price", lastTransPrice)
        outState.putString("last_trans_net", lastTransNet)
        outState.putString("last_service_type", lastServiceType)
        outState.putInt("executing_pending_id", executingPendingId)
        super.onSaveInstanceState(outState)
    }

    private fun showTransferConfirmDialog() {
        transferConfirmationDialogVisible = true
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_transfer_confirm, null)
        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        var operationResolved = false
        val tvMessage = view.findViewById<TextView>(R.id.tv_confirm_message)
        tvMessage.text = "هل تمت حوالة " + lastTransAmount + " رصيد لـ " + lastTransName + " بنجاح؟"

        fun saveAsPending(): Boolean {
            if (operationResolved) return true

            val saved = executingPendingId != -1 ||
                dbHelper.addPending(
                    lastTransName,
                    lastTransNet,
                    lastTransAmount,
                    lastTransPrice,
                    getCurrentDateString(),
                    lastServiceType,
                    lastTransAmount
                )
            if (!saved) {
                Toast.makeText(this, "تعذر حفظ العملية في المعلقات؛ لم يتم فقدان بياناتها", Toast.LENGTH_LONG).show()
                return false
            }

            rememberQuickCategory(lastServiceType, lastTransAmount.toLong(), lastTransPrice.toLong())
            operationResolved = true
            executingPendingId = -1
            transferConfirmationDialogVisible = false
            resetTransferUI()
            loadAllData()
            Toast.makeText(this, "تم حفظ العملية في المعلقات للمراجعة لاحقاً", Toast.LENGTH_LONG).show()
            return true
        }

        dialog.setOnCancelListener {
            if (!operationResolved && !saveAsPending()) {
                dialog.window?.decorView?.post { showTransferConfirmDialog() }
            }
        }

        view.findViewById<Button>(R.id.btn_confirm_yes).setOnClickListener {
            val note = "تحويل " + lastTransAmount + " رصيد (" + lastServiceType + ")"
            val saved = if (executingPendingId != -1) {
                dbHelper.completePendingRequest(
                    executingPendingId,
                    lastTransName,
                    lastTransPrice,
                    note,
                    getCurrentDateString(),
                    lastServiceType,
                    lastTransAmount
                )
            } else {
                dbHelper.addTransaction(lastTransName, lastTransPrice, 1, note, getCurrentDateString(), lastServiceType, lastTransAmount)
            }
            if (!saved) {
                Toast.makeText(this, "تعذر حفظ الحركة؛ لم يتم حذف الطلب المعلق", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            rememberQuickCategory(lastServiceType, lastTransAmount.toLong(), lastTransPrice.toLong())
            operationResolved = true
            Toast.makeText(this, "تم قيد " + lastTransPrice + " ل.س كدين", Toast.LENGTH_SHORT).show()
            resetTransferUI()
            executingPendingId = -1
            transferConfirmationDialogVisible = false
            loadAllData()
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btn_confirm_later).setOnClickListener {
            if (!saveAsPending()) {
                return@setOnClickListener
            }
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btn_confirm_cancel).setOnClickListener {
            dialog.cancel()
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
    }
    private fun showPendingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pending, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_pending)
        recycler.layoutManager = LinearLayoutManager(this)

        fun refreshPending() {
            val list = dbHelper.getAllPending()
            if (list.isEmpty()) {
                dialog.dismiss()
                Toast.makeText(this, "لا يوجد طلبات معلقة", Toast.LENGTH_SHORT).show()
            }
            recycler.adapter = PendingAdapter(list, execute@{ pending ->
                val customer = customerList.find { it.name == pending.customerName }
                if (customer == null) {
                    Toast.makeText(this, "الزبون محذوف!", Toast.LENGTH_SHORT).show()
                    return@execute
                }

                val serviceName = pending.serviceType.ifBlank {
                    if (pending.network == "Syr") "رصيد Syriatel" else "رصيد MTN"
                }
                val availableBalance = dbHelper.getCompanyAvailableBalance(serviceName)
                if (pending.serviceAmount.toLong() > availableBalance) {
                    Toast.makeText(
                        this,
                        "لا يمكنك تنفيذ هذا الطلب. رصيد الشركة الحالي غير كافٍ.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@execute
                }

                val isUSSD = pending.serviceType == "رصيد Syriatel" ||
                    pending.serviceType == "رصيد MTN" || pending.serviceType.isEmpty()
                if (isUSSD) {
                    val isSyr = pending.serviceType == "رصيد Syriatel" || pending.network == "Syr"
                    val targetPhone = if (isSyr) customer.phoneSyriatel else customer.phoneMtn
                    if (targetPhone.isEmpty()) {
                        Toast.makeText(this, "رقم الزبون غير موجود", Toast.LENGTH_SHORT).show()
                        return@execute
                    }
                    val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                    val pin = if (isSyr) prefs.getString("syr_pin", "0000") else prefs.getString("mtn_pin", "0000")
                    val codeTemplate = if (isSyr) prefs.getString("syr_code", "*150*رقم*مبلغ*رمز#") else prefs.getString("mtn_code", "*150*رقم*مبلغ*رمز#")
                    val safePin = if (pin.isNullOrEmpty()) "0000" else pin
                    val safeTemplate = if (codeTemplate.isNullOrEmpty()) "*150*رقم*مبلغ*رمز#" else codeTemplate
                    var ussd = safeTemplate.replace("رقم", targetPhone).replace("مبلغ", pending.serviceAmount.toString()).replace("رمز", safePin)
                    if (ussd.endsWith("#")) ussd = ussd.dropLast(1)
                    pendingUssdCode = ussd + Uri.encode("#")
                    lastTransName = pending.customerName
                    lastTransAmount = pending.serviceAmount
                    lastTransPrice = pending.price
                    lastTransNet = pending.network
                    lastServiceType = if (pending.serviceType.isNotEmpty()) pending.serviceType else if (isSyr) "رصيد Syriatel" else "رصيد MTN"
                    executingPendingId = pending.id
                    checkPermissionAndCall()
                    dialog.dismiss()
                } else {
                    val note = "تحويل " + pending.serviceAmount + " (" + pending.serviceType + ")"
                    if (!dbHelper.completePendingRequest(
                            pending.id,
                            pending.customerName,
                            pending.price,
                            note,
                            getCurrentDateString(),
                            pending.serviceType,
                            pending.serviceAmount
                        )
                    ) {
                        Toast.makeText(this, "تعذر حفظ الحركة؛ بقي الطلب في المعلقات", Toast.LENGTH_LONG).show()
                        return@execute
                    }
                    Toast.makeText(this, "تم قيد " + pending.price + " ل.س كدين", Toast.LENGTH_SHORT).show()
                    loadAllData()
                    refreshPending()
                    dialog.dismiss()
                }
            }, { pending ->
                AlertDialog.Builder(this)
                    .setTitle("تأكيد الحذف")
                    .setMessage("هل تريد بالتأكيد حذف هذا الطلب المعلق؟")
                    .setPositiveButton("حذف") { _, _ ->
                        dbHelper.deletePending(pending.id)
                        loadAllData()
                        refreshPending()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            })
        }
        refreshPending()
        dialog.show()
    }
    private fun showEditCustomerDialog(customer: Customer) { val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_cust, null); val dialog = AlertDialog.Builder(this).setView(view).create(); val inputName = view.findViewById(R.id.edit_cust_name) as EditText; val inputSyr = view.findViewById(R.id.edit_cust_syr) as EditText; val inputMtn = view.findViewById(R.id.edit_cust_mtn) as EditText; inputName.setText(customer.name); inputSyr.setText(customer.phoneSyriatel); inputMtn.setText(customer.phoneMtn); (view.findViewById(R.id.btn_update_cust) as Button).setOnClickListener { val n = inputName.text.toString().trim(); val s = inputSyr.text.toString().trim(); val m = inputMtn.text.toString().trim(); if (n.isNotEmpty() && (s.isNotEmpty() || m.isNotEmpty())) { if (dbHelper.updateCustomer(customer.id, customer.name, n, s, m)) { Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show(); loadAllData(); dialog.dismiss() } } else { Toast.makeText(this, "البيانات غير مكتملة", Toast.LENGTH_SHORT).show() } }; dialog.show() }
    private fun showEditTransactionDialog(trans: Transaction) { val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_trans, null); val dialog = AlertDialog.Builder(this).setView(view).create(); (view.findViewById(R.id.tv_edit_trans_name) as TextView).text = "الزبون: " + trans.customerName; val tvTypeLabel = view.findViewById<TextView>(R.id.tv_edit_trans_type_label); if (trans.type == 1) { tvTypeLabel.text = "نوع الحركة: دين (تم من التحويل)"; tvTypeLabel.setTextColor(Color.parseColor("#D32F2F")) } else { tvTypeLabel.text = "نوع الحركة: دفعة (تسديد)"; tvTypeLabel.setTextColor(Color.parseColor("#388E3C")) }; val inputAmount = view.findViewById(R.id.edit_trans_amount) as EditText; val inputNote = view.findViewById(R.id.edit_trans_note) as EditText; inputAmount.setText(trans.amount.toString()); inputNote.setText(trans.note); (view.findViewById(R.id.btn_update_trans) as Button).setOnClickListener { val a = inputAmount.text.toString().trim().toIntOrNull() ?: 0; if (a > 0) { if (dbHelper.updateTransaction(trans.id, a, trans.type, inputNote.text.toString().trim(), trans.serviceType)) { Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show(); loadAllData(); dialog.dismiss() } } else { Toast.makeText(this, "أدخل مبلغ صحيح", Toast.LENGTH_SHORT).show() } }; dialog.show() }
    private fun loadAllData() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
        val qCust = (findViewById(R.id.input_search_cust) as EditText).text.toString().lowercase()
        customerAdapter.customers = if (qCust.isEmpty()) customerList else customerList.filter { it.name.lowercase().contains(qCust) }
        customerAdapter.notifyDataSetChanged()
        val recyclerCustomers = findViewById<RecyclerView>(R.id.recycler_customers)
        recyclerCustomers.visibility = if (qCust.isNotEmpty() || isCustListVisible) View.VISIBLE else View.GONE
        customerNamesList.clear()
        customerNamesList.addAll(customerList.map { it.name })
        autoAdapterTransfer = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        (findViewById(R.id.input_transfer_customer) as AutoCompleteTextView).setAdapter(autoAdapterTransfer)
        autoAdapterTrans = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        (findViewById(R.id.input_trans_customer) as AutoCompleteTextView).setAdapter(autoAdapterTrans)
        val inputSearchTrans = findViewById<AutoCompleteTextView>(R.id.input_search_trans)
        inputSearchTrans.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList.toList())
        )
        inputSearchTrans.threshold = 1
        transactionList.clear()
        transactionList.addAll(dbHelper.getAllTransactions())
        val todayStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        val todayTrans = transactionList.filter { it.date.startsWith(todayStr) }
        val todayDebt: Long = todayTrans.filter { it.type == 1 }.sumOf { it.amount.toLong() }
        val todayPayment: Long = todayTrans.filter { it.type == 2 }.sumOf { it.amount.toLong() }
        (findViewById(R.id.tv_today_debt) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(todayDebt) + " ل.س"
        (findViewById(R.id.tv_today_payment) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(todayPayment) + " ل.س"
        (findViewById(R.id.tv_total_debt) as TextView).text = NumberFormat.getNumberInstance(Locale.US).format(dbHelper.getTotalDebt()) + " ل.س"
        val pendingCount = dbHelper.getAllPending().size
        (findViewById(R.id.btn_show_pending) as Button).text = "الطلبات المعلقة ($pendingCount)"
        val qTrans = inputSearchTrans.text.toString().lowercase()
        var filteredTrans = transactionList.toList()
        val filterSvcIdx = findViewById<Spinner>(R.id.spinner_filter_service).selectedItemPosition
        if (qTrans.isNotEmpty()) filteredTrans = filteredTrans.filter { it.customerName.lowercase().contains(qTrans) }
        if (filterFromDate.isNotEmpty()) filteredTrans = filteredTrans.filter { it.date.take(10) >= filterFromDate }
        if (filterToDate.isNotEmpty()) filteredTrans = filteredTrans.filter { it.date.take(10) <= filterToDate }
        if (filterSvcIdx > 0) {
            val targetSvc = filterServicesArray[filterSvcIdx]
            filteredTrans = filteredTrans.filter {
                it.serviceType == targetSvc || (targetSvc == "دفعة عامة" && it.serviceType.isEmpty() && it.type == 2)
            }
        }
        transactionAdapter.transactions = filteredTrans
        transactionAdapter.notifyDataSetChanged()
        val hasSearch = qTrans.isNotEmpty() || filterFromDate.isNotEmpty() || filterToDate.isNotEmpty() || filterSvcIdx > 0
        val recyclerTransactions = findViewById<RecyclerView>(R.id.recycler_transactions)
        recyclerTransactions.visibility = if (hasSearch || isTransListVisible) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recycler_companies).adapter?.notifyDataSetChanged()
    }
    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else {
            executeUSSD()
        }
    }

    private fun executeUSSD() {
        if (pendingUssdCode.isEmpty()) {
            Toast.makeText(this, "لا يوجد رمز تحويل جاهز للتنفيذ", Toast.LENGTH_LONG).show()
            return
        }

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$pendingUssdCode")
        }
        try {
            startActivity(callIntent)
            pendingUssdCode = ""
            waitingForTransferConfirm = true
        } catch (e: ActivityNotFoundException) {
            pendingUssdCode = ""
            executingPendingId = -1
            Toast.makeText(this, "تعذر العثور على تطبيق لإجراء الاتصال", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            pendingUssdCode = ""
            executingPendingId = -1
            Toast.makeText(this, "لم يُسمح بإجراء الاتصال؛ تحقق من إذن الاتصال", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            pendingUssdCode = ""
            executingPendingId = -1
            Toast.makeText(this, "رمز التحويل غير صالح", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CALL_REQUEST_CODE) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            executeUSSD()
        } else {
            pendingUssdCode = ""
            executingPendingId = -1
            Toast.makeText(this, "تم رفض إذن الاتصال؛ لم يتم تنفيذ التحويل", Toast.LENGTH_LONG).show()
        }
    }
}

class CategoryAdapter(
    var categories: List<ServiceCategory>,
    private val onEdit: (ServiceCategory) -> Unit,
    private val onDelete: (ServiceCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val amount = view.findViewById<TextView>(R.id.tv_category_amount)
        val price = view.findViewById<TextView>(R.id.tv_category_price)
        val edit = view.findViewById<ImageView>(R.id.btn_category_edit)
        val delete = view.findViewById<ImageView>(R.id.btn_category_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.layout_category_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        val numberFormat = NumberFormat.getNumberInstance(Locale.US)
        holder.amount.text = "الرصيد: ${numberFormat.format(category.amount)}"
        holder.price.text = "السعر: ${numberFormat.format(category.price)} ل.س"
        holder.edit.setOnClickListener { onEdit(category) }
        holder.delete.setOnClickListener { onDelete(category) }
    }

    override fun getItemCount(): Int = categories.size
}

class PendingAdapter(var items: List<PendingRequest>, private val onExecute: (PendingRequest) -> Unit, private val onDelete: (PendingRequest) -> Unit) : RecyclerView.Adapter<PendingAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName = view.findViewById(R.id.tv_pend_name) as TextView; val tvDetails = view.findViewById(R.id.tv_pend_details) as TextView; val tvDate = view.findViewById(R.id.tv_pend_date) as TextView; val btnExecute = view.findViewById(R.id.btn_pend_execute) as Button; val btnDelete = view.findViewById(R.id.btn_pend_delete) as TextView }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pending, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { val item = items[position]; holder.tvName.text = item.customerName; val svc = if(item.serviceType.isNotEmpty()) item.serviceType else item.network; holder.tvDetails.text = "رصيد: " + item.amount + " | تكلفة: " + item.price + " (" + svc + ")"; holder.tvDate.text = item.date; holder.btnExecute.setOnClickListener { onExecute(item) }; holder.btnDelete.setOnClickListener { onDelete(item) } }
    override fun getItemCount() = items.size
}

class CustomerAdapter(var customers: List<Customer>, private val dbHelper: DatabaseHelper, private val onEdit: (Customer) -> Unit, private val onDelete: (Customer) -> Unit, private val onPdf: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName = view.findViewById(R.id.tv_customer_name) as TextView; val tvSyriatel = view.findViewById(R.id.tv_syriatel) as TextView; val tvMtn = view.findViewById(R.id.tv_mtn) as TextView; val tvInitial = view.findViewById(R.id.tv_initial) as TextView; val tvBalance = view.findViewById(R.id.tv_customer_balance) as TextView; val btnEdit = view.findViewById(R.id.btn_edit_cust) as ImageView; val btnDelete = view.findViewById(R.id.btn_delete_cust) as ImageView; val btnPdf = view.findViewById(R.id.btn_pdf_cust) as ImageView }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { val customer = customers[position]; holder.tvName.text = customer.name; holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "SYRIATEL: " + customer.phoneSyriatel else "SYRIATEL: -"; holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: " + customer.phoneMtn else "MTN: -"; if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1); val balance = dbHelper.getCustomerBalance(customer.name); holder.tvBalance.text = NumberFormat.getNumberInstance(Locale.US).format(balance); val cColor = if(balance < 0) Color.parseColor("#388E3C") else ContextCompat.getColor(holder.itemView.context, R.color.colorPrimary); holder.tvBalance.setTextColor(cColor); holder.btnEdit.setOnClickListener { onEdit(customer) }; holder.btnDelete.setOnClickListener { onDelete(customer) }; holder.btnPdf.setOnClickListener { onPdf(customer) } }
    override fun getItemCount() = customers.size
}

class TransactionAdapter(var transactions: List<Transaction>, private val onEdit: (Transaction) -> Unit, private val onDelete: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName = view.findViewById(R.id.tv_trans_name) as TextView; val tvNote = view.findViewById(R.id.tv_trans_note) as TextView; val tvDate = view.findViewById(R.id.tv_trans_date) as TextView; val tvAmount = view.findViewById(R.id.tv_trans_amount) as TextView; val tvType = view.findViewById(R.id.tv_trans_type) as TextView; val tvService = view.findViewById(R.id.tv_trans_service) as TextView; val btnEdit = view.findViewById(R.id.btn_edit_trans) as ImageView; val btnDelete = view.findViewById(R.id.btn_delete_trans) as ImageView }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { val trans = transactions[position]; holder.tvName.text = trans.customerName; holder.tvNote.text = trans.note; holder.tvDate.text = trans.date; holder.tvAmount.text = NumberFormat.getNumberInstance(Locale.US).format(trans.amount); val svcStr = if (trans.serviceType.isNotEmpty()) trans.serviceType else if (trans.type == 2) "دفعة عامة" else "رصيد"; holder.tvService.text = svcStr; if (trans.type == 1) { holder.tvType.text = "دين"; holder.tvType.setTextColor(Color.parseColor("#D32F2F")); holder.tvAmount.setTextColor(Color.parseColor("#D32F2F")) } else { holder.tvType.text = "دفعة"; holder.tvType.setTextColor(Color.parseColor("#388E3C")); holder.tvAmount.setTextColor(Color.parseColor("#388E3C")) }; holder.btnEdit.setOnClickListener { onEdit(trans) }; holder.btnDelete.setOnClickListener { onDelete(trans) } }
    override fun getItemCount() = transactions.size
}

class CompanyAdapter(var companies: List<String>, private val dbHelper: DatabaseHelper, private val context: Context) : RecyclerView.Adapter<CompanyAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName = view.findViewById<TextView>(R.id.tv_comp_name); val btnToggle = view.findViewById<Button>(R.id.btn_comp_toggle); val layoutDetails = view.findViewById<View>(R.id.layout_comp_details); val tvBalance = view.findViewById<TextView>(R.id.tv_comp_balance); val tvDebt = view.findViewById<TextView>(R.id.tv_comp_debt); val btnCharge = view.findViewById<Button>(R.id.btn_comp_charge); val btnPay = view.findViewById<Button>(R.id.btn_comp_pay); val btnReport = view.findViewById<Button>(R.id.btn_comp_report) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_company, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comp = companies[position]; holder.tvName.text = comp; var isExpanded = false
        fun updateNumbers() { val avail = dbHelper.getCompanyAvailableBalance(comp); val debt = dbHelper.getCompanyFinancialDebt(comp); holder.tvBalance.text = NumberFormat.getNumberInstance(Locale.US).format(avail); holder.tvDebt.text = NumberFormat.getNumberInstance(Locale.US).format(debt) + " ل.س" }
        holder.btnToggle.setOnClickListener { isExpanded = !isExpanded; holder.layoutDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE; holder.btnToggle.text = if (isExpanded) "إخفاء التفاصيل" else "عرض التفاصيل والأرصدة"; if (isExpanded) updateNumbers() }
        holder.btnCharge.setOnClickListener { if(context is MainActivity) context.showCompanyChargeDialog(comp) { updateNumbers() } }
        holder.btnPay.setOnClickListener { if(context is MainActivity) context.showCompanyPayDialog(comp) { updateNumbers() } }
        holder.btnReport.setOnClickListener { if(context is MainActivity) context.showCompanyReportDialog(comp) }
    }
    override fun getItemCount() = companies.size
}

class CompanyLogAdapter(var entries: List<CompanyLogEntry>) : RecyclerView.Adapter<CompanyLogAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_company_log_type)
        val tvAmount: TextView = view.findViewById(R.id.tv_company_log_amount)
        val tvNote: TextView = view.findViewById(R.id.tv_company_log_note)
        val tvDate: TextView = view.findViewById(R.id.tv_company_log_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.layout_company_log_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvType.text = entry.type
        holder.tvAmount.text = if (entry.type == "شحن رصيد") {
            "كمية الرصيد: ${NumberFormat.getNumberInstance(Locale.US).format(entry.quantity)} | التكلفة: ${NumberFormat.getNumberInstance(Locale.US).format(entry.amount)} ل.س"
        } else {
            "المبلغ المدفوع: ${NumberFormat.getNumberInstance(Locale.US).format(entry.amount)} ل.س"
        }
        holder.tvNote.text = entry.note.ifBlank { "بدون ملاحظة" }
        holder.tvDate.text = entry.date
    }

    override fun getItemCount() = entries.size
}