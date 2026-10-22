package com.pos.transferapp
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.*
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
    private lateinit var radioGroupAdjustment: RadioGroup
    private lateinit var inputAdjustmentAmount: EditText
    private lateinit var radioGroupPaymentMethod: RadioGroup
    private lateinit var layoutCashbox: View
    private lateinit var navCashbox: ViewGroup
    private lateinit var tvCashProfits: TextView
    private lateinit var tvCashDeposits: TextView
    private lateinit var tvCashExpenses: TextView
    private lateinit var tvCashNet: TextView
    private lateinit var btnAddExpense: Button
    private lateinit var btnAddCapital: Button
    private lateinit var rvCashTransactions: RecyclerView
    private val blurEngines = ArrayList<RenderScriptBlur>()
    lateinit var dbHelper: DatabaseHelper
    lateinit var customerAdapter: CustomerAdapter
    lateinit var transactionAdapter: TransactionAdapter
    private lateinit var companyLogAdapter: CompanyLogAdapter
    private lateinit var companyLogCompanySpinner: Spinner
    private lateinit var companyLogFilterSpinner: Spinner
    private lateinit var transferCompanySpinner: Spinner
    var customerList = ArrayList<Customer>()
    var transactionList = ArrayList<Transaction>()
    lateinit var autoAdapterTransfer: ArrayAdapter<String>
    lateinit var autoAdapterTrans: ArrayAdapter<String>
    var customerNamesList = ArrayList<String>()
    var isCustListVisible = false
    var isTransListVisible = false
    var filterFromDate = ""
    var filterToDate = ""
    private var backupShareInProgress = false
    private var backupOperationInProgress = false
    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) confirmAndRestoreFromUri(uri)
    }
    val filterServicesArray = arrayOf("جميع الخدمات", "دفعة عامة", "رصيد Syriatel", "رصيد MTN", "شام كاش", "فاتورة Syriatel", "فاتورة MTN")

    private fun getCurrentDateString(): String { return SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.US).format(Date()) }

    override fun onStart() {
        super.onStart()
        if (backupShareInProgress || backupOperationInProgress || !::dbHelper.isInitialized) return
        try {
            DatabaseBackup.backupIfDue(this, dbHelper.writableDatabase)
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Daily database backup failed", error)
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "Daily database backup was denied", error)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!backupOperationInProgress && ::dbHelper.isInitialized) {
            try {
                dbHelper.flushToDisk()
            } catch (error: Exception) {
                Log.e("DatabaseBackup", "Could not flush the database to disk", error)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (backupShareInProgress || backupOperationInProgress) return
        try {
            if (::dbHelper.isInitialized) dbHelper.flushToDisk()
        } catch (error: Exception) {
            Log.e("DatabaseBackup", "Could not flush the database before backup", error)
        }
        try {
            val openDatabase = if (::dbHelper.isInitialized) dbHelper.writableDatabase else null
            DatabaseBackup.createBackup(this, openDatabase)
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Automatic database backup failed on activity stop", error)
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "Automatic database backup access was denied", error)
        }
    }

    override fun onDestroy() {
        blurEngines.forEach { engine ->
            try {
                engine.destroy()
            } catch (error: Exception) {
                Log.e("Blur", "Could not release blur", error)
            }
        }
        blurEngines.clear()
        super.onDestroy()
    }
    private fun shareBackup() {
        val backupDB = try {
            DatabaseBackup.createBackup(this, if (::dbHelper.isInitialized) dbHelper.writableDatabase else null)
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

    private fun confirmRestoreLocalBackup() {
        val backupFile = DatabaseBackup.getBackupFile(this)
        if (backupFile == null) {
            Toast.makeText(this, "لا توجد نسخة احتياطية محفوظة على الجهاز", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("استعادة النسخة الاحتياطية")
            .setMessage("سيتم استبدال جميع البيانات الحالية بالنسخة المحفوظة على الجهاز. هل تريد المتابعة؟")
            .setPositiveButton("استعادة") { _, _ -> restoreBackupFromLocalFile(backupFile) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmAndRestoreFromUri(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("استعادة من ملف")
            .setMessage("سيتم استبدال جميع البيانات الحالية بمحتوى الملف المحدد. هل تريد المتابعة؟")
            .setPositiveButton("استعادة") { _, _ -> restoreBackupFromUri(uri) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun restoreBackupFromLocalFile(backupFile: File) {
        backupOperationInProgress = true
        try {
            if (::dbHelper.isInitialized) dbHelper.close()
            DatabaseBackup.restoreFromFile(this, backupFile)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            refreshCompanyLog()
            Toast.makeText(this, "تمت استعادة النسخة الاحتياطية بنجاح", Toast.LENGTH_LONG).show()
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Local restore failed", error)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            Toast.makeText(this, "تعذر الاستعادة: ${error.message}", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "Local restore denied", error)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            Toast.makeText(this, "تعذر الوصول إلى ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        } finally {
            backupOperationInProgress = false
        }
    }

    private fun restoreBackupFromUri(uri: Uri) {
        backupOperationInProgress = true
        try {
            if (::dbHelper.isInitialized) dbHelper.close()
            DatabaseBackup.restoreFromUri(this, uri)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            refreshCompanyLog()
            Toast.makeText(this, "تمت استعادة النسخة الاحتياطية من الملف بنجاح", Toast.LENGTH_LONG).show()
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "URI restore failed", error)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            Toast.makeText(this, "تعذر الاستعادة: ${error.message}", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "URI restore denied", error)
            dbHelper = DatabaseHelper(this)
            loadAllData()
            Toast.makeText(this, "تعذر الوصول إلى ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        } finally {
            backupOperationInProgress = false
        }
    }

    private fun confirmDeleteCustomer(customer: Customer) {
        val counts = dbHelper.getCustomerRelatedCounts(customer.name)
        val message = buildString {
            append("تأكيد حذف الزبون \"${customer.name}\"؟")
            if (counts.first > 0 || counts.second > 0) {
                append("\n\nسيتم أيضاً حذف ")
                if (counts.first > 0) append("${counts.first} حركة مالية")
                if (counts.first > 0 && counts.second > 0) append(" و")
                if (counts.second > 0) append("${counts.second} طلب معلق")
                append(".")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("حذف الزبون")
            .setMessage(message)
            .setPositiveButton("حذف") { _, _ ->
                if (dbHelper.deleteCustomer(customer.id, customer.name)) {
                    loadAllData()
                    Toast.makeText(this, "تم حذف الزبون والبيانات المرتبطة", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "تعذر حذف الزبون", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        radioGroupAdjustment = findViewById(R.id.radio_group_adjustment)
        inputAdjustmentAmount = findViewById(R.id.input_adjustment_amount)
        radioGroupPaymentMethod = findViewById(R.id.radio_group_payment_method)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background)
        val blurRoot = window.decorView as ViewGroup
        listOf(
            findViewById<BlurView>(R.id.header_blur_view),
            findViewById<BlurView>(R.id.transfer_blur_view)
        ).forEach { blurView ->
            val engine = RenderScriptBlur(this)
            blurEngines.add(engine)
            blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND)
            blurView.clipToOutline = true
            blurView.setupWith(blurRoot, engine)
                .setBlurRadius(18f)
        }
        dbHelper = DatabaseHelper(this)
        try {
            DatabaseBackup.backupIfDue(this, dbHelper.writableDatabase)
        } catch (error: IOException) {
            Log.e("DatabaseBackup", "Daily database backup failed", error)
        } catch (error: SecurityException) {
            Log.e("DatabaseBackup", "Daily database backup was denied", error)
        }
        DatabaseBackup.scheduleDaily(this)
        val tvMainTitle = findViewById(R.id.tv_main_title) as TextView
        val layoutTransfer = findViewById(R.id.layout_transfer) as View
        val layoutCustomers = findViewById(R.id.layout_customers) as View
        val layoutDebt = findViewById(R.id.layout_debt) as View
        val layoutCompanies = findViewById(R.id.layout_companies) as View
        layoutCashbox = findViewById(R.id.layout_cashbox)
        navCashbox = findViewById(R.id.nav_cashbox)
        tvCashProfits = findViewById(R.id.tv_cash_profits)
        tvCashDeposits = findViewById(R.id.tv_cash_deposits)
        tvCashExpenses = findViewById(R.id.tv_cash_expenses)
        tvCashNet = findViewById(R.id.tv_cash_net)
        btnAddExpense = findViewById(R.id.btn_add_expense)
        btnAddCapital = findViewById(R.id.btn_add_capital)
        rvCashTransactions = findViewById(R.id.rv_cash_transactions)
        val layoutSettings = findViewById(R.id.layout_settings) as View
        val navTransfer = findViewById(R.id.nav_transfer) as ViewGroup
        val navCustomers = findViewById(R.id.nav_customers) as ViewGroup
        val navDebt = findViewById(R.id.nav_debt) as ViewGroup
        val navCompanies = findViewById(R.id.nav_companies) as ViewGroup
        val navSettings = findViewById(R.id.nav_settings) as ViewGroup

        val cashTransactionAdapter = CashTransactionAdapter(emptyList())
        rvCashTransactions.layoutManager = LinearLayoutManager(this)
        rvCashTransactions.adapter = cashTransactionAdapter
        var cashboxFilterApplied = false
        var cashboxFromDate = ""
        var cashboxToDate = ""
        var shownCashTransactions = emptyList<CashTransaction>()
        var shownCashSummary = CashboxSummary(0.0, 0.0, 0.0, 0.0)
        val btnCashFrom = findViewById<Button>(R.id.btn_cash_from)
        val btnCashTo = findViewById<Button>(R.id.btn_cash_to)
        var pendingCashFrom = ""
        var pendingCashTo = ""
        val cashCalendar = Calendar.getInstance()
        fun refreshCashbox() {
            val fromDate = if (cashboxFilterApplied) cashboxFromDate else ""
            val toDate = if (cashboxFilterApplied) cashboxToDate else ""
            val cashTransactions = dbHelper.getCashTransactions(fromDate, toDate)
            val summary = dbHelper.getCashboxSummary(fromDate, toDate)
            shownCashTransactions = cashTransactions
            shownCashSummary = summary
            cashTransactionAdapter.items = cashTransactions
            cashTransactionAdapter.notifyDataSetChanged()
            val numberFormat = NumberFormat.getNumberInstance(Locale.US)
            tvCashDeposits.text = "الإيداعات: ${numberFormat.format(summary.deposits)} ل.س"
            tvCashExpenses.text = "المصروفات: ${numberFormat.format(summary.expenses)} ل.س"
            if (cashboxFilterApplied) {
                tvCashProfits.text = "الأرباح: ${numberFormat.format(summary.profits)} ل.س"
                tvCashNet.text = "الصافي النقدي: ${numberFormat.format(summary.netCash)} ل.س"
            } else {
                tvCashProfits.text = "الأرباح: ***"
                tvCashNet.text = "الصافي النقدي: ***"
            }
        }
        btnCashFrom.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                pendingCashFrom = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnCashFrom.text = "من: $pendingCashFrom"
            }, cashCalendar.get(Calendar.YEAR), cashCalendar.get(Calendar.MONTH), cashCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnCashTo.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                pendingCashTo = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnCashTo.text = "إلى: $pendingCashTo"
            }, cashCalendar.get(Calendar.YEAR), cashCalendar.get(Calendar.MONTH), cashCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        findViewById<Button>(R.id.btn_cash_apply).setOnClickListener {
            cashboxFromDate = pendingCashFrom
            cashboxToDate = pendingCashTo
            cashboxFilterApplied = true
            refreshCashbox()
        }
        findViewById<Button>(R.id.btn_export_cash_pdf).setOnClickListener {
            val fromDate = if (cashboxFilterApplied) cashboxFromDate else ""
            val toDate = if (cashboxFilterApplied) cashboxToDate else ""
            exportCashboxPdf(fromDate, toDate, shownCashTransactions, shownCashSummary)
        }
        fun showCashboxEntryDialog(type: Int) {
            val expenseView = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null)
            val expenseDialog = AlertDialog.Builder(this).setView(expenseView).create()
            val inputExpenseAmount = expenseView.findViewById<EditText>(R.id.input_expense_amount)
            val inputExpenseNote = expenseView.findViewById<EditText>(R.id.input_expense_note)
            inputExpenseAmount.clearZeroOnFocus()
            expenseView.findViewById<TextView>(R.id.tv_expense_title).text =
                if (type == 1) "إيداع" else "صرف"
            expenseView.findViewById<Button>(R.id.btn_cancel_expense).setOnClickListener {
                expenseDialog.dismiss()
            }
            expenseView.findViewById<Button>(R.id.btn_save_expense).setOnClickListener {
                val amount = inputExpenseAmount.text.toString().trim().toDoubleOrNull()
                if (amount == null || !amount.isFinite() || amount <= 0.0) {
                    Toast.makeText(this, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!dbHelper.addCashTransaction(
                        type = type,
                        amount = amount,
                        note = inputExpenseNote.text.toString().trim(),
                        date = getCurrentDateString()
                    )
                ) {
                    Toast.makeText(this, "تعذر حفظ العملية", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                refreshCashbox()
                expenseDialog.dismiss()
                Toast.makeText(
                    this,
                    if (type == 1) "تم حفظ الإيداع" else "تم حفظ المصروف",
                    Toast.LENGTH_SHORT
                ).show()
            }
            expenseDialog.show()
        }
        btnAddExpense.setOnClickListener { showCashboxEntryDialog(type = 0) }
        btnAddCapital.setOnClickListener { showCashboxEntryDialog(type = 1) }
        refreshCashbox()

        companyLogCompanySpinner = findViewById(R.id.spinner_company_log_company)
        companyLogFilterSpinner = findViewById(R.id.spinner_company_log_filter)
        val companyLogRecycler = findViewById<RecyclerView>(R.id.recycler_company_log)
        companyLogAdapter = CompanyLogAdapter(emptyList())
        companyLogRecycler.layoutManager = LinearLayoutManager(this)
        companyLogRecycler.adapter = companyLogAdapter
        companyLogCompanySpinner.adapter = createNeonSpinnerAdapter(dbHelper.getAllCompanies()) { it.name }
        companyLogFilterSpinner.adapter = createNeonSpinnerAdapter(listOf("الكل", "شحن رصيد", "دفعات", "مبيعات"))
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
        findViewById<Button>(R.id.btn_add_company).setOnClickListener {
            showCompanyDialog()
        }
        val btnManageOfficials = findViewById<Button>(R.id.btn_manage_officials)
        btnManageOfficials.setOnClickListener {
            showManageOfficialsDialog()
        }
        findViewById<Button>(R.id.btn_official_report).setOnClickListener {
            showOfficialPickerDialog()
        }

        fun updateNavUI(selected: String) {
            navTransfer.isSelected = selected == "transfer"
            navCustomers.isSelected = selected == "customers"
            navDebt.isSelected = selected == "debt"
            navCompanies.isSelected = selected == "companies"
            navCashbox.isSelected = selected == "cashbox"
            navSettings.isSelected = selected == "settings"
            listOf(navTransfer, navCustomers, navDebt, navCompanies, navCashbox, navSettings)
                .forEach { it.refreshDrawableState() }
        }

        navTransfer.setOnClickListener { layoutTransfer.visibility = View.VISIBLE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutCashbox.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "تحويل"; updateNavUI("transfer"); refreshTransferCompanies() }
        navCustomers.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.VISIBLE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutCashbox.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "إدارة الزبائن"; updateNavUI("customers") }
        navDebt.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.VISIBLE; layoutCompanies.visibility = View.GONE; layoutCashbox.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الحسابات والدفعات"; updateNavUI("debt") }
        navCompanies.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.VISIBLE; layoutCashbox.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الشركات والموردين"; updateNavUI("companies"); refreshCompanies() }
        navCashbox.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutCashbox.visibility = View.VISIBLE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الصندوق"; updateNavUI("cashbox"); refreshCashbox() }
        navSettings.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutCompanies.visibility = View.GONE; layoutCashbox.visibility = View.GONE; layoutSettings.visibility = View.VISIBLE; tvMainTitle.text = "الإعدادات"; updateNavUI("settings") }

        val inputTransferCustomer = findViewById(R.id.input_transfer_customer) as AutoCompleteTextView
        transferCompanySpinner = findViewById(R.id.spinner_service_type)
        val spinnerServiceType = transferCompanySpinner
        val inputTransferAmount = findViewById(R.id.input_transfer_amount) as EditText
        val inputTransferPrice = findViewById(R.id.input_transfer_price) as EditText
        val tvBalanceWarning = findViewById<TextView>(R.id.tv_balance_warning)
        val layoutDirectActions = findViewById<View>(R.id.layout_direct_actions)
        val btnDoneDebt = findViewById<Button>(R.id.btn_done_debt)
        val btnShowPending = findViewById(R.id.btn_show_pending) as Button
        val btnAddPending = findViewById<Button>(R.id.btn_add_pending)
        val spinnerFilterService = findViewById<Spinner>(R.id.spinner_filter_service)

        refreshTransferCompanies()
        spinnerFilterService.adapter = createNeonSpinnerAdapter(filterServicesArray.toList())

        inputTransferCustomer.setOnTouchListener { _, _ -> inputTransferCustomer.showDropDown(); false }

        fun updateSuggestedPrice() {
            val amount = inputTransferAmount.text.toString().trim().toLongOrNull()
            val company = spinnerServiceType.selectedItem as? Company
            if (amount == null || amount <= 0L || company == null || !company.sellPrice.isFinite()) {
                if (inputTransferPrice.text.isNotEmpty()) inputTransferPrice.text.clear()
                return
            }
            val price = amount.toDouble() * company.sellPrice
            val rounded = kotlin.math.round(price)
            val text = if (kotlin.math.abs(price - rounded) < 0.001) {
                rounded.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", price)
            }
            if (inputTransferPrice.text.toString() != text) {
                inputTransferPrice.setText(text)
            }
        }

        fun validateTransferAmount() {
            val amountStr = inputTransferAmount.text.toString().trim()
            val amount = amountStr.toLongOrNull() ?: 0L
            val selectedCompany = spinnerServiceType.selectedItem as? Company
            if (selectedCompany == null) {
                tvBalanceWarning.visibility = View.GONE
                btnDoneDebt.isEnabled = false
                btnDoneDebt.alpha = 0.5f
                btnAddPending.isEnabled = false
                btnAddPending.alpha = 0.5f
                return
            }
            val available = dbHelper.getCompanyAvailableBalance(selectedCompany.id, selectedCompany.name)
            if (amountStr.isNotEmpty() && amount > available) {
                tvBalanceWarning.visibility = View.VISIBLE
                tvBalanceWarning.text = "لا يمكن التحويل لعدم وجود رصيد كافي. الرصيد المتبقي هو: " + NumberFormat.getNumberInstance(Locale.US).format(available) + " (" + selectedCompany.name + ")"
                btnDoneDebt.isEnabled = false; btnDoneDebt.alpha = 0.5f
                btnAddPending.isEnabled = true; btnAddPending.alpha = 1.0f
            } else {
                tvBalanceWarning.visibility = View.GONE
                btnDoneDebt.isEnabled = true; btnDoneDebt.alpha = 1.0f
                btnAddPending.isEnabled = true; btnAddPending.alpha = 1.0f
            }
        }

        inputTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSuggestedPrice()
                validateTransferAmount()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        spinnerServiceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                inputAdjustmentAmount.text.clear()
                updateSuggestedPrice()
                validateTransferAmount()
                layoutDirectActions.visibility = View.VISIBLE
                btnDoneDebt.text = getString(R.string.execute_transfer)
                btnAddPending.text = getString(R.string.save_pending)
                btnAddPending.visibility = View.VISIBLE
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
            val company = spinnerServiceType.selectedItem as? Company
            if (company == null) {
                Toast.makeText(this, "اختر شركة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val customer = customerList.find { it.name == selectedName }
            if (customer == null) { Toast.makeText(this, "اختر زبون مسجل", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val amount = amountStr.toLongOrNull()
            if (amount == null || amount <= 0L || amount > Int.MAX_VALUE) {
                Toast.makeText(this, "أدخل رصيداً صحيحاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val adjustmentText = inputAdjustmentAmount.text.toString().trim()
            val adjustmentAmount = if (adjustmentText.isEmpty()) 0.0 else adjustmentText.toDoubleOrNull()
            if (adjustmentAmount == null || !adjustmentAmount.isFinite() || adjustmentAmount < 0.0) {
                Toast.makeText(this, "أدخل قيمة تعديل صحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!company.buyPrice.isFinite() || !company.sellPrice.isFinite() ||
                company.buyPrice < 0.0 || company.sellPrice < 0.0
            ) {
                Toast.makeText(this, "أسعار الشركة غير صالحة", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val totalBuyPrice = amount * company.buyPrice
            val baseSellPrice = amount * company.sellPrice
            val discountAddition = when (radioGroupAdjustment.checkedRadioButtonId) {
                R.id.radio_discount -> -adjustmentAmount
                R.id.radio_addition -> adjustmentAmount
                else -> 0.0
            }
            val finalPrice = when (radioGroupAdjustment.checkedRadioButtonId) {
                R.id.radio_discount -> baseSellPrice - adjustmentAmount
                R.id.radio_addition -> baseSellPrice + adjustmentAmount
                else -> baseSellPrice
            }
            val profit = finalPrice - totalBuyPrice
            if (!finalPrice.isFinite() || finalPrice <= 0.0 || finalPrice > Int.MAX_VALUE ||
                finalPrice % 1.0 != 0.0 || !profit.isFinite()
            ) {
                Toast.makeText(this, "السعر النهائي يجب أن يكون مبلغاً صحيحاً موجباً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val available = dbHelper.getCompanyAvailableBalance(company.id, company.name)
            if (amount > available) {
                Toast.makeText(this, "لا يمكن التنفيذ؛ رصيد الشركة الحالي غير كافٍ", Toast.LENGTH_LONG).show()
                validateTransferAmount()
                return@setOnClickListener
            }

            val transaction = Transaction(
                id = 0,
                customerName = selectedName,
                amount = finalPrice.toInt(),
                type = 1,
                note = "تحويل $amount (${company.name})",
                date = getCurrentDateString(),
                serviceType = company.name,
                serviceAmount = amount.toInt(),
                discountAddition = discountAddition,
                profit = profit,
                paymentMethod = 0
            )
            if (dbHelper.addTransaction(transaction)) {
                Toast.makeText(this, "تم قيد ${finalPrice.toInt()} ل.س كدين", Toast.LENGTH_SHORT).show()
                resetTransferUI()
                loadAllData()
            } else {
                Toast.makeText(this, "تعذر تسجيل الحركة", Toast.LENGTH_LONG).show()
            }
        }

        btnAddPending.setOnClickListener {
            val selectedName = inputTransferCustomer.text.toString().trim()
            val amountStr = inputTransferAmount.text.toString().trim()
            val priceStr = inputTransferPrice.text.toString().trim()
            val company = spinnerServiceType.selectedItem as? Company
            if (company == null) {
                Toast.makeText(this, "اختر شركة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serviceName = company.name
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
            val price = priceValue.toInt()
            val amount = amountValue.toInt()
            val net = "Direct"
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val dueDate = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                if (dbHelper.addPending(selectedName, net, amount, price, getCurrentDateString(), serviceName, amount, dueDate)) {
                    Toast.makeText(this, R.string.saved_to_pending, Toast.LENGTH_SHORT).show()
                    resetTransferUI()
                    loadAllData()
                } else {
                    Toast.makeText(this, "تعذر حفظ الطلب؛ تحقق من الرصيد والسعر", Toast.LENGTH_LONG).show()
                }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnShowPending.setOnClickListener { showPendingDialog() }
        val btnToggleCustomers = findViewById(R.id.btn_toggle_customers) as Button
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        val inputSearchCust = findViewById(R.id.input_search_cust) as EditText
        val btnToggleSearchCust = findViewById(R.id.btn_toggle_search_cust) as ImageView
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList, dbHelper, onEdit = { cust -> showEditCustomerDialog(cust) }, onDelete = { cust -> confirmDeleteCustomer(cust) }, onPdf = { cust -> showStatementDateDialog(cust) })
        recyclerCustomers.adapter = customerAdapter
        btnToggleCustomers.setOnClickListener { isCustListVisible = !isCustListVisible; btnToggleCustomers.text = if (isCustListVisible) "الزبائن المسجلين ▲" else "الزبائن المسجلين ▼"; recyclerCustomers.visibility = if (isCustListVisible || inputSearchCust.text.isNotEmpty()) View.VISIBLE else View.GONE }
        btnToggleSearchCust.setOnClickListener { inputSearchCust.visibility = if (inputSearchCust.visibility == View.VISIBLE) { inputSearchCust.setText(""); View.GONE } else View.VISIBLE }
        inputSearchCust.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { val q = s.toString().lowercase(); customerAdapter.customers = if (q.isEmpty()) customerList else customerList.filter { it.name.lowercase().contains(q) }; customerAdapter.notifyDataSetChanged(); recyclerCustomers.visibility = if (q.isNotEmpty() || isCustListVisible) View.VISIBLE else View.GONE }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        findViewById<EditText>(R.id.input_initial_debt).clearZeroOnFocus()
        findViewById<Button>(R.id.btn_save_customer).setOnClickListener {
            val name = (findViewById(R.id.input_cust_name) as EditText).text.toString().trim()
            val syriatel = (findViewById(R.id.input_cust_syriatel) as EditText).text.toString().trim()
            val mtn = (findViewById(R.id.input_cust_mtn) as EditText).text.toString().trim()
            val initialDebtInput = (findViewById(R.id.input_initial_debt) as EditText).text.toString().trim()
            val parsedInitialDebt = initialDebtInput.toDoubleOrNull()
            if (name.isEmpty() || (syriatel.isEmpty() && mtn.isEmpty())) {
                Toast.makeText(this, "أدخل الاسم ورقم", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (initialDebtInput.isNotEmpty() &&
                (parsedInitialDebt == null || !parsedInitialDebt.isFinite() || parsedInitialDebt < 0.0)
            ) {
                Toast.makeText(this, "أدخل ديناً ابتدائياً صحيحاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val initialDebt = parsedInitialDebt ?: 0.0
            if (dbHelper.isCustomerExists(name)) {
                Toast.makeText(this, "اسم الزبون موجود مسبقاً", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val customer = Customer(
                id = 0,
                name = name,
                phoneSyriatel = syriatel,
                phoneMtn = mtn,
                initialDebt = initialDebt
            )
            if (dbHelper.addCustomer(customer)) {
                (findViewById(R.id.input_cust_name) as EditText).text.clear()
                (findViewById(R.id.input_cust_syriatel) as EditText).text.clear()
                (findViewById(R.id.input_cust_mtn) as EditText).text.clear()
                (findViewById(R.id.input_initial_debt) as EditText).text.clear()
                loadAllData()
                Toast.makeText(this, "تم حفظ الزبون", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "تعذر حفظ الزبون", Toast.LENGTH_LONG).show()
            }
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
        transactionAdapter = TransactionAdapter(transactionList, onEdit = { trans -> showEditTransactionDialog(trans) }, onDelete = { trans ->
            AlertDialog.Builder(this)
                .setTitle("حذف")
                .setMessage("تأكيد حذف هذه الحركة؟")
                .setPositiveButton("نعم") { _, _ ->
                    if (dbHelper.deleteTransaction(trans.id)) {
                        loadAllData()
                        refreshCompanyLog()
                    } else {
                        Toast.makeText(this, "تعذر حذف الحركة", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        })
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
            val name = inputTransCustomer.text.toString().trim()
            val amountStr = inputTransAmount.text.toString().trim()
            val amount = amountStr.toIntOrNull()
            val paymentMethod = when (radioGroupPaymentMethod.checkedRadioButtonId) {
                R.id.radio_cash -> 1
                R.id.radio_sham_cash -> 2
                else -> 0
            }
            if (name.isEmpty() || amount == null || amount <= 0) {
                Toast.makeText(this, "أدخل اسماً ومبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dbHelper.addTransaction(
                    customerName = name,
                    amount = amount,
                    type = 2,
                    note = inputTransNote.text.toString().trim(),
                    date = getCurrentDateString(),
                    serviceType = "",
                    paymentMethod = paymentMethod
                )) {
                inputTransCustomer.text.clear()
                inputTransAmount.text.clear()
                inputTransNote.text.clear()
                loadAllData()
                Toast.makeText(this, "تم تسجيل الدفعة بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "تعذر تسجيل الدفعة", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btn_share_backup).setOnClickListener { shareBackup() }
        findViewById<Button>(R.id.btn_restore_local_backup).setOnClickListener { confirmRestoreLocalBackup() }
        findViewById<Button>(R.id.btn_restore_backup_file).setOnClickListener {
            try {
                restoreBackupLauncher.launch("*/*")
            } catch (error: ActivityNotFoundException) {
                Toast.makeText(this, "لا يوجد تطبيق لاختيار ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            }
        }
        loadAllData(); navTransfer.performClick()
    }

    private fun <T> createNeonSpinnerAdapter(
        items: List<T>,
        label: (T) -> String = { it.toString() }
    ): ArrayAdapter<T> {
        val density = resources.displayMetrics.density
        val horizontalPadding = (20 * density).toInt()
        val verticalPadding = (16 * density).toInt()
        return object : ArrayAdapter<T>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    text = label(items[position])
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    text = label(items[position])
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.background))
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                }
        }
    }

    private fun refreshTransferCompanies() {
        if (!::dbHelper.isInitialized || !::transferCompanySpinner.isInitialized) return

        val selectedCompanyId = (transferCompanySpinner.selectedItem as? Company)?.id
        val companies = dbHelper.getAllCompanies()
        transferCompanySpinner.adapter = createNeonSpinnerAdapter(companies) { it.name }
        val selectedIndex = companies.indexOfFirst { it.id == selectedCompanyId }
        if (selectedIndex >= 0) transferCompanySpinner.setSelection(selectedIndex)
    }

    private fun refreshCompanies() {
        val companies = dbHelper.getAllCompanies()
        val recyclerCompanies = findViewById<RecyclerView>(R.id.recycler_companies)
        recyclerCompanies.layoutManager = LinearLayoutManager(this)
        recyclerCompanies.adapter = CompanyAdapter(companies, dbHelper, this) { company ->
            showCompanyDialog(company)
        }
        if (::companyLogCompanySpinner.isInitialized) {
            companyLogCompanySpinner.adapter = createNeonSpinnerAdapter(companies) { it.name }
            refreshCompanyLog()
        }
        refreshTransferCompanies()
    }

    private fun showManageOfficialsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_officials, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_officials)
        val emptyView = view.findViewById<TextView>(R.id.tv_officials_empty)
        lateinit var officialAdapter: OfficialAdapter
        fun reloadOfficials() {
            val officials = dbHelper.getAllOfficials()
            officialAdapter.officials = officials
            officialAdapter.notifyDataSetChanged()
            emptyView.visibility = if (officials.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (officials.isEmpty()) View.GONE else View.VISIBLE
        }
        officialAdapter = OfficialAdapter(
            officials = emptyList(),
            dbHelper = dbHelper,
            onEdit = { official -> showOfficialEditorDialog(official) { reloadOfficials() } },
            onDelete = { official -> confirmDeleteOfficial(official) { reloadOfficials() } }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = officialAdapter
        reloadOfficials()
        view.findViewById<Button>(R.id.btn_add_official).setOnClickListener {
            showOfficialEditorDialog(null) { reloadOfficials() }
        }
        view.findViewById<Button>(R.id.btn_close_officials).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showOfficialEditorDialog(official: Official?, onSaved: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_official, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val inputName = view.findViewById<EditText>(R.id.input_official_name)
        view.findViewById<TextView>(R.id.tv_official_dialog_title).text =
            if (official == null) "إضافة مسؤول جديد" else "تعديل المسؤول"
        if (official != null) inputName.setText(official.name)
        view.findViewById<Button>(R.id.btn_cancel_official).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btn_save_official).setOnClickListener {
            val name = inputName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "أدخل اسم المسؤول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saved = if (official == null) {
                dbHelper.addOfficial(name) > 0L
            } else {
                dbHelper.updateOfficial(official.id, name) > 0
            }
            if (!saved) {
                Toast.makeText(this, "تعذر حفظ المسؤول", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            onSaved()
            Toast.makeText(this, "تم حفظ المسؤول", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun confirmDeleteOfficial(official: Official, onDeleted: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("حذف المسؤول")
            .setMessage("هل تريد حذف ${official.name}؟")
            .setPositiveButton("حذف") { _, _ ->
                when (dbHelper.deleteOfficial(official.id)) {
                    -1 -> Toast.makeText(this, "لا يمكن الحذف لوجود شركات مرتبطة", Toast.LENGTH_LONG).show()
                    0 -> Toast.makeText(this, "تعذر حذف المسؤول", Toast.LENGTH_LONG).show()
                    else -> {
                        Toast.makeText(this, "تم حذف المسؤول", Toast.LENGTH_SHORT).show()
                        onDeleted()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showOfficialPickerDialog() {
        val officials = dbHelper.getAllOfficials()
        if (officials.isEmpty()) {
            Toast.makeText(this, "لا يوجد مسؤولون مسجلون", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("اختر المسؤول")
            .setItems(officials.map { it.name }.toTypedArray()) { _, position ->
                showOfficialReportDialog(officials[position])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showOfficialReportDialog(official: Official) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_official_report, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tv_report_title).text =
            "خيارات طباعة كشف المسؤول: ${official.name}"
        val btnFrom = view.findViewById<Button>(R.id.btn_rep_from)
        val btnTo = view.findViewById<Button>(R.id.btn_rep_to)
        val includePurchases = view.findViewById<CheckBox>(R.id.cb_official_shipping_balances)
        val includePayments = view.findViewById<CheckBox>(R.id.cb_official_financial_payments)
        val includeCompanySummary = view.findViewById<CheckBox>(R.id.cb_official_company_summary)
        var fromDate = ""
        var toDate = ""
        val calendar = Calendar.getInstance()
        btnFrom.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                fromDate = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnFrom.text = "من تاريخ: $fromDate"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnTo.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                toDate = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                btnTo.text = "إلى تاريخ: $toDate"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val resultsLayout = view.findViewById<View>(R.id.layout_rep_results)
        val creditView = view.findViewById<TextView>(R.id.tv_rep_credit)
        val financialView = view.findViewById<TextView>(R.id.tv_rep_financial)
        val countView = view.findViewById<TextView>(R.id.tv_rep_count)
        view.findViewById<Button>(R.id.btn_rep_calc).setOnClickListener {
            val entries = dbHelper.getOfficialReport(
                official.id,
                fromDate,
                toDate,
                includePurchases.isChecked,
                includePayments.isChecked
            )
            val purchases = entries.filter { it.type == "أرصدة شحن" }
            val payments = entries.filter { it.type == "دفعات مالية" }
            val numberFormat = NumberFormat.getNumberInstance(Locale.US)
            creditView.text = "تكلفة أرصدة الشحن: ${numberFormat.format(purchases.sumOf { it.amount })} ل.س"
            financialView.text = "الدفعات المالية: ${numberFormat.format(payments.sumOf { it.amount })} ل.س"
            countView.text = "عدد الحركات: ${entries.size}"
            resultsLayout.visibility = View.VISIBLE
        }
        view.findViewById<Button>(R.id.btn_rep_export_pdf).setOnClickListener {
            exportOfficialPdf(
                official,
                fromDate,
                toDate,
                includePurchases.isChecked,
                includePayments.isChecked,
                includeCompanySummary.isChecked
            )
        }
        view.findViewById<Button>(R.id.btn_rep_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCompanyDialog(company: Company? = null) {
        val officials = dbHelper.getAllOfficials()
        if (officials.isEmpty()) {
            Toast.makeText(this, "أضف مسؤولاً قبل إضافة شركة", Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_company, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val inputName = view.findViewById<EditText>(R.id.input_company_name)
        val spinnerOfficial = view.findViewById<Spinner>(R.id.spinner_official)
        val inputInitialBalance = view.findViewById<EditText>(R.id.input_initial_balance)
        val inputBuyPrice = view.findViewById<EditText>(R.id.input_buy_price)
        val inputSellPrice = view.findViewById<EditText>(R.id.input_sell_price)
        val officialIds = officials.map { it.id }

        spinnerOfficial.adapter = createNeonSpinnerAdapter(officials.map { it.name })
        listOf(inputInitialBalance, inputBuyPrice, inputSellPrice).forEach { it.clearZeroOnFocus() }
        company?.let { current ->
            inputName.setText(current.name)
            inputInitialBalance.setText(current.initialBalance.toInputText())
            inputBuyPrice.setText(current.buyPrice.toInputText())
            inputSellPrice.setText(current.sellPrice.toInputText())
            val selectedOfficialIndex = officialIds.indexOf(current.officialId)
            if (selectedOfficialIndex >= 0) spinnerOfficial.setSelection(selectedOfficialIndex)
        }

        view.findViewById<Button>(R.id.btn_cancel_company).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btn_save_company).setOnClickListener {
            val name = inputName.text.toString().trim()
            val officialPosition = spinnerOfficial.selectedItemPosition
            val numericValues = listOf(inputInitialBalance, inputBuyPrice, inputSellPrice).map { input ->
                input.text.toString().trim().let { text ->
                    if (text.isEmpty()) 0.0 else text.toDoubleOrNull()
                }
            }

            if (name.isEmpty() || officialPosition !in officialIds.indices) {
                Toast.makeText(this, "أدخل اسم الشركة واختر المسؤول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (numericValues.any { it == null || !it.isFinite() || it < 0.0 }) {
                Toast.makeText(this, "تحقق من الرصيد والأسعار المدخلة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val initialBalance = numericValues[0] ?: 0.0
            val buyPrice = numericValues[1] ?: 0.0
            val sellPrice = numericValues[2] ?: 0.0

            val companyToSave = Company(
                id = company?.id ?: 0,
                name = name,
                officialId = officialIds[officialPosition],
                initialBalance = initialBalance,
                buyPrice = buyPrice,
                sellPrice = sellPrice
            )
            val saved = if (company == null) {
                dbHelper.addCompany(companyToSave) > 0L
            } else {
                dbHelper.updateCompany(companyToSave) > 0
            }
            if (!saved) {
                Toast.makeText(this, "تعذر حفظ بيانات الشركة", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            refreshCompanies()
            dialog.dismiss()
            Toast.makeText(this, "تم حفظ بيانات الشركة", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun createNameAdapter(items: List<String>): ArrayAdapter<String> {
        val density = resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (14 * density).toInt()
        return object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.background))
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                }
        }
    }

    private fun EditText.clearZeroOnFocus() {
        setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            val value = text.toString().trim()
            if (value == "0" || value == "0.0" || value == "0.00") text.clear()
        }
    }

    private fun Double.toInputText(): String = if (this == 0.0) "" else toString()

    private fun EditText.styleAsAppField() {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        setBackgroundResource(R.drawable.bg_field)
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        textSize = 16f
        gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.RIGHT
        setPadding(padding, 0, padding, 0)
    }

    private fun resetTransferUI() {
        findViewById<EditText>(R.id.input_transfer_amount).text.clear()
        findViewById<EditText>(R.id.input_transfer_price).text.clear()
        inputAdjustmentAmount.text.clear()
        radioGroupAdjustment.clearCheck()
    }

    fun showCompanyChargeDialog(company: Company, onComplete: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_company_charge, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<TextView>(R.id.tv_charge_title).text = "شحن رصيد: " + company.name
        val inpAmount = view.findViewById<EditText>(R.id.input_charge_amount)
        val inpNote = view.findViewById<EditText>(R.id.input_charge_note)
        view.findViewById<Button>(R.id.btn_save_charge).setOnClickListener {
            val amount = inpAmount.text.toString().toIntOrNull() ?: 0
            val cost = amount * company.buyPrice
            if (amount <= 0 || !cost.isFinite() || cost <= 0.0) {
                Toast.makeText(this, "تحقق من كمية الشحن وسعر الشراء للشركة", Toast.LENGTH_SHORT).show()
            } else if (dbHelper.addCompanyPurchase(company.name, amount, cost, inpNote.text.toString().trim(), getCurrentDateString())) {
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
            var purchaseCost = 0.0
            var purchaseQuantity = 0L
            var payments = 0.0
            var salesValue = 0.0
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
    private fun exportOfficialPdf(
        official: Official,
        fromDate: String,
        toDate: String,
        includePurchases: Boolean,
        includePayments: Boolean,
        includeCompanySummary: Boolean
    ) {
        val pdfDocument = PdfDocument()
        var openPage: PdfDocument.Page? = null
        try {
            val entries = dbHelper.getOfficialReport(
                official.id,
                fromDate,
                toDate,
                includePurchases,
                includePayments
            )
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
            var page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            openPage = page
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
                canvas.drawText("كشف المسؤول التفصيلي", right, y, titlePaint)
                y += 26f
                canvas.drawText("المسؤول: ${official.name}", right, y, boldPaint)
                y += 20f
                val period = if (fromDate.isEmpty() && toDate.isEmpty()) {
                    "كافة التواريخ"
                } else {
                    "${fromDate.ifEmpty { "البداية" }} - ${toDate.ifEmpty { "النهاية" }}"
                }
                canvas.drawText("الفترة: $period", right, y, regularPaint)
                y += 28f
                canvas.drawRect(
                    margin,
                    y - 16f,
                    right,
                    y + 8f,
                    Paint().apply { color = Color.rgb(235, 238, 240) }
                )
                boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText("الشركة", 550f, y, boldPaint)
                canvas.drawText("التاريخ", 455f, y, boldPaint)
                canvas.drawText("العملية", 375f, y, boldPaint)
                canvas.drawText("المبلغ / الكمية", 265f, y, boldPaint)
                canvas.drawText("التفاصيل والملاحظة", 155f, y, boldPaint)
                y += 14f
                canvas.drawLine(margin, y, right, y, gridPaint)
                y += 18f
                boldPaint.color = Color.BLACK
            }

            fun startNextPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                openPage = page
                canvas = page.canvas
                drawPageHeader()
            }

            drawPageHeader()
            var purchaseCost = 0.0
            var purchaseQuantity = 0L
            var paymentsTotal = 0.0
            val companyTotals = linkedMapOf<String, Pair<Double, Double>>()

            for (entry in entries) {
                val amountLines = when (entry.type) {
                    "أرصدة شحن" -> {
                        purchaseCost += entry.amount
                        purchaseQuantity += entry.quantity
                        val previous = companyTotals[entry.companyName] ?: (0.0 to 0.0)
                        companyTotals[entry.companyName] =
                            (previous.first + entry.amount) to previous.second
                        listOf(
                            "التكلفة: ${numberFormat.format(entry.amount)} ل.س",
                            "الكمية: ${numberFormat.format(entry.quantity)}"
                        )
                    }
                    else -> {
                        paymentsTotal += entry.amount
                        val previous = companyTotals[entry.companyName] ?: (0.0 to 0.0)
                        companyTotals[entry.companyName] =
                            previous.first to (previous.second + entry.amount)
                        listOf("المدفوع: ${numberFormat.format(entry.amount)} ل.س")
                    }
                }
                val companyLines = wrapText(entry.companyName, regularPaint, 78f)
                val noteLines = wrapText(entry.note.trim(), regularPaint, 105f)
                val rowHeight = maxOf(amountLines.size, companyLines.size, noteLines.size, 1) * 14f + 12f
                if (y + rowHeight > 770f) startNextPage()
                val firstBaseline = y + 10f
                canvas.drawText(entry.date.take(10), 455f, firstBaseline, regularPaint)
                canvas.drawText(entry.type, 375f, firstBaseline, regularPaint)
                amountLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 265f, firstBaseline + index * 14f, regularPaint)
                }
                companyLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 550f, firstBaseline + index * 14f, regularPaint)
                }
                noteLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 155f, firstBaseline + index * 14f, regularPaint)
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

            if (includeCompanySummary) {
                val summaryRows = buildList {
                    add("إجمالي أرصدة الشحن: ${numberFormat.format(purchaseQuantity)} كمية | ${numberFormat.format(purchaseCost)} ل.س")
                    add("إجمالي الدفعات المالية: ${numberFormat.format(paymentsTotal)} ل.س")
                    companyTotals.forEach { (companyName, totals) ->
                        add("$companyName: مشتريات ${numberFormat.format(totals.first)} ل.س | دفعات ${numberFormat.format(totals.second)} ل.س")
                    }
                }
                var index = 0
                while (index < summaryRows.size) {
                    if (y + 23f + 28f > 790f) startNextPage()
                    val capacity = ((770f - y - 28f) / 23f).toInt().coerceAtLeast(1)
                    val chunk = summaryRows.subList(index, minOf(summaryRows.size, index + capacity))
                    val summaryTop = y + 12f
                    val summaryBottom = summaryTop + chunk.size * 23f + 16f
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
                    chunk.forEachIndexed { rowIndex, summary ->
                        canvas.drawText(summary, right - 10f, summaryTop + 21f + rowIndex * 23f, boldPaint)
                    }
                    y = summaryBottom
                    index += chunk.size
                }
            }

            pdfDocument.finishPage(page)
            openPage = null
            val outputDirectory = getExternalFilesDir(null)
                ?: throw IOException("تعذر الوصول إلى مجلد حفظ التقرير")
            val safeOfficialName = official.name
                .replace("[^\\p{L}\\p{N}_-]".toRegex(), "_")
                .take(40)
                .ifBlank { "official" }
            val pdfFile = File(outputDirectory, "Official_Report_${safeOfficialName}.pdf")
            FileOutputStream(pdfFile).use { output -> pdfDocument.writeTo(output) }
            val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة كشف المسؤول"))
        } catch (error: Exception) {
            Log.e("OfficialReport", "Could not generate official report PDF", error)
            Toast.makeText(this, "تعذر إنشاء تقرير PDF", Toast.LENGTH_LONG).show()
        } finally {
            val pageToFinish = openPage
            if (pageToFinish != null) {
                try {
                    pdfDocument.finishPage(pageToFinish)
                } catch (error: Exception) {
                    Log.e("OfficialReport", "Could not finish official PDF page", error)
                }
            }
            try {
                pdfDocument.close()
            } catch (error: Exception) {
                Log.e("OfficialReport", "Could not close official PDF", error)
            }
        }
    }

    private fun exportCashboxPdf(
        fromDate: String,
        toDate: String,
        transactions: List<CashTransaction>,
        summary: CashboxSummary
    ) {
        val pdfDocument = PdfDocument()
        var openPage: PdfDocument.Page? = null
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
            openPage = page
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
                canvas.drawText("كشف الصندوق", right, y, titlePaint)
                y += 26f
                val period = if (fromDate.isEmpty() && toDate.isEmpty()) {
                    "كافة التواريخ"
                } else {
                    "${fromDate.ifEmpty { "البداية" }} - ${toDate.ifEmpty { "النهاية" }}"
                }
                canvas.drawText("الفترة: $period", right, y, boldPaint)
                y += 28f
                canvas.drawRect(margin, y - 16f, right, y + 8f, Paint().apply { color = Color.rgb(235, 238, 240) })
                boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText("التاريخ", 550f, y, boldPaint)
                canvas.drawText("العملية", 450f, y, boldPaint)
                canvas.drawText("المبلغ", 318f, y, boldPaint)
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
                openPage = page
                canvas = page.canvas
                drawPageHeader()
            }

            drawPageHeader()
            for (entry in transactions) {
                val typeLabel = if (entry.type == 0) "صرف" else "إيداع"
                val noteLines = wrapText(entry.note.trim(), regularPaint, 132f)
                val rowHeight = maxOf(noteLines.size, 1) * 14f + 12f
                if (y + rowHeight > 770f) startNextPage()
                val firstBaseline = y + 10f
                canvas.drawText(entry.date.take(10), 550f, firstBaseline, regularPaint)
                canvas.drawText(typeLabel, 450f, firstBaseline, regularPaint)
                canvas.drawText("${numberFormat.format(entry.amount)} ل.س", 318f, firstBaseline, regularPaint)
                noteLines.forEachIndexed { index, line ->
                    canvas.drawText(line, 185f, firstBaseline + index * 14f, regularPaint)
                }
                y += rowHeight
                canvas.drawLine(margin, y - 3f, right, y - 3f, gridPaint)
            }
            if (transactions.isEmpty()) {
                regularPaint.color = Color.GRAY
                canvas.drawText("لا توجد حركات ضمن الفترة المحددة", right, y + 12f, regularPaint)
                regularPaint.color = Color.BLACK
                y += 26f
            }

            val summaryRows = listOf(
                "إجمالي الأرباح: ${numberFormat.format(summary.profits)} ل.س",
                "إجمالي الإيداعات: ${numberFormat.format(summary.deposits)} ل.س",
                "إجمالي المصروفات: ${numberFormat.format(summary.expenses)} ل.س",
                "الصافي النقدي: ${numberFormat.format(summary.netCash)} ل.س"
            )
            if (y + summaryRows.size * 23f + 28f > 790f) startNextPage()
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
            summaryRows.forEachIndexed { index, summaryLine ->
                if (index == summaryRows.lastIndex) boldPaint.color = Color.rgb(15, 76, 92)
                canvas.drawText(summaryLine, right - 10f, summaryTop + 21f + index * 23f, boldPaint)
            }
            pdfDocument.finishPage(page)
            openPage = null

            val outputDirectory = getExternalFilesDir(null)
                ?: throw IOException("تعذر الوصول إلى مجلد حفظ التقرير")
            val pdfFile = File(outputDirectory, "Cashbox_Report.pdf")
            FileOutputStream(pdfFile).use { output -> pdfDocument.writeTo(output) }
            val uri = FileProvider.getUriForFile(this, "com.pos.transferapp.fileprovider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة كشف الصندوق"))
        } catch (error: Exception) {
            Log.e("CashboxReport", "Could not generate cashbox PDF", error)
            Toast.makeText(this, "تعذر إنشاء تقرير PDF", Toast.LENGTH_LONG).show()
        } finally {
            val pageToFinish = openPage
            if (pageToFinish != null) {
                try {
                    pdfDocument.finishPage(pageToFinish)
                } catch (error: Exception) {
                    Log.e("CashboxReport", "Could not finish cashbox PDF page", error)
                }
            }
            try {
                pdfDocument.close()
            } catch (error: Exception) {
                Log.e("CashboxReport", "Could not close cashbox PDF", error)
            }
        }
    }

    private fun showStatementDateDialog(customer: Customer) { val view = LayoutInflater.from(this).inflate(R.layout.dialog_date_filter, null); val dialog = AlertDialog.Builder(this).setView(view).create(); val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title); tvTitle.text = "كشف حساب: " + customer.name; val btnFrom = view.findViewById(R.id.btn_pick_from) as Button; val btnTo = view.findViewById(R.id.btn_pick_to) as Button; var tempFrom = ""; var tempTo = ""; val cal = Calendar.getInstance(); btnFrom.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempFrom = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnFrom.text = "من تاريخ: " + tempFrom }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }; btnTo.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> tempTo = String.format(Locale.US, "%04d/%02d/%02d", y, m + 1, d); btnTo.text = "إلى تاريخ: " + tempTo }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show() }; (view.findViewById(R.id.btn_clear_dates) as Button).setOnClickListener { generateAndSharePDF(customer, "", ""); dialog.dismiss() }; (view.findViewById(R.id.btn_apply_dates) as Button).setOnClickListener { generateAndSharePDF(customer, tempFrom, tempTo); dialog.dismiss() }; dialog.show() }
    private fun refreshCompanyLog() {
        if (!::companyLogAdapter.isInitialized || !::companyLogCompanySpinner.isInitialized || !::companyLogFilterSpinner.isInitialized) return
        val companyName = (companyLogCompanySpinner.selectedItem as? Company)?.name
        val filterType = companyLogFilterSpinner.selectedItem as? String ?: "الكل"
        val entries = if (companyName.isNullOrBlank()) {
            emptyList()
        } else {
            dbHelper.getCompanyLog(companyName, filterType)
        }
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
    }
    private fun showPendingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pending, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_pending)
        val btnFilterFromDate = view.findViewById<Button>(R.id.btn_filter_from_date)
        val btnFilterToDate = view.findViewById<Button>(R.id.btn_filter_to_date)
        val btnApplyFilter = view.findViewById<Button>(R.id.btn_apply_filter)
        recycler.layoutManager = LinearLayoutManager(this)
        var selectedFromDate = ""
        var selectedToDate = ""
        var refreshPending: () -> Unit = {}

        val pendingAdapter = PendingAdapter(emptyList(), execute@{ pending ->
            if (customerList.none { it.name == pending.customerName }) {
                Toast.makeText(this, "الزبون محذوف!", Toast.LENGTH_SHORT).show()
                return@execute
            }

            val serviceName = pending.serviceType.ifBlank {
                if (pending.network == "Syr") "رصيد Syriatel" else "رصيد MTN"
            }
            val company = dbHelper.getAllCompanies().firstOrNull { it.name == serviceName }
            if (company == null) {
                Toast.makeText(this, "الشركة المرتبطة بالطلب غير موجودة", Toast.LENGTH_LONG).show()
                return@execute
            }
            val availableBalance = dbHelper.getCompanyAvailableBalance(company.id, company.name)
            if (pending.serviceAmount.toLong() > availableBalance) {
                Toast.makeText(
                    this,
                    "لا يمكنك تنفيذ هذا الطلب. رصيد الشركة الحالي غير كافٍ.",
                    Toast.LENGTH_LONG
                ).show()
                return@execute
            }

            val profit = pending.price - pending.serviceAmount * company.buyPrice
            if (!profit.isFinite()) {
                Toast.makeText(this, "تعذر حساب ربح الطلب", Toast.LENGTH_LONG).show()
                return@execute
            }
            val note = "تحويل ${pending.serviceAmount} (${company.name})"
            val transaction = Transaction(
                id = 0,
                customerName = pending.customerName,
                amount = pending.price,
                type = 1,
                note = note,
                date = getCurrentDateString(),
                serviceType = company.name,
                serviceAmount = pending.serviceAmount,
                profit = profit,
                paymentMethod = 0
            )
            if (!dbHelper.addTransaction(transaction, pending.id)) {
                Toast.makeText(this, "تعذر حفظ الحركة؛ بقي الطلب في المعلقات", Toast.LENGTH_LONG).show()
                return@execute
            }
            Toast.makeText(this, "تم تسجيل الحركة بقيمة ${pending.price} ل.س", Toast.LENGTH_SHORT).show()
            loadAllData()
            refreshPending()
            dialog.dismiss()
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
        recycler.adapter = pendingAdapter

        fun normalizePendingDate(value: String): String? {
            val date = value.take(10)
            val isoDate = when {
                date.length == 10 && date[4] == '/' && date[7] == '/' ->
                    date.replace('/', '-')
                date.length == 10 && date[4] == '-' && date[7] == '-' -> date
                else -> return null
            }
            return isoDate.takeIf { it.all { character -> character == '-' || character.isDigit() } }
        }

        refreshPending = {
            val list = dbHelper.getAllPending()
            if (list.isEmpty()) {
                dialog.dismiss()
                Toast.makeText(this, "لا يوجد طلبات معلقة", Toast.LENGTH_SHORT).show()
            }
            val filteredList = list.filter { pending ->
                val pendingDate = normalizePendingDate(
                    pending.dueDate.ifBlank { pending.date }
                ) ?: return@filter false
                (selectedFromDate.isEmpty() || pendingDate >= selectedFromDate) &&
                    (selectedToDate.isEmpty() || pendingDate <= selectedToDate)
            }
            pendingAdapter.items = filteredList
            pendingAdapter.notifyDataSetChanged()
        }

        val calendar = Calendar.getInstance()
        btnFilterFromDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedFromDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    btnFilterFromDate.text = "من تاريخ: $selectedFromDate"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        btnFilterToDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedToDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    btnFilterToDate.text = "إلى تاريخ: $selectedToDate"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        btnApplyFilter.setOnClickListener { refreshPending() }
        refreshPending()
        dialog.show()
    }
    private fun showEditCustomerDialog(customer: Customer) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_cust, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val inputName = view.findViewById(R.id.edit_cust_name) as EditText
        val inputSyr = view.findViewById(R.id.edit_cust_syr) as EditText
        val inputMtn = view.findViewById(R.id.edit_cust_mtn) as EditText
        val inputInitialDebt = view.findViewById(R.id.input_initial_debt) as EditText
        inputName.setText(customer.name)
        inputSyr.setText(customer.phoneSyriatel)
        inputMtn.setText(customer.phoneMtn)
        inputInitialDebt.setText(customer.initialDebt.toInputText())
        inputInitialDebt.clearZeroOnFocus()
        (view.findViewById(R.id.btn_update_cust) as Button).setOnClickListener {
            val n = inputName.text.toString().trim()
            val s = inputSyr.text.toString().trim()
            val m = inputMtn.text.toString().trim()
            val initialDebtInput = inputInitialDebt.text.toString().trim()
            val parsedInitialDebt = initialDebtInput.toDoubleOrNull()
            if (n.isEmpty() || (s.isEmpty() && m.isEmpty())) {
                Toast.makeText(this, "البيانات غير مكتملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (initialDebtInput.isNotEmpty() &&
                (parsedInitialDebt == null || !parsedInitialDebt.isFinite() || parsedInitialDebt < 0.0)
            ) {
                Toast.makeText(this, "أدخل ديناً ابتدائياً صحيحاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val initialDebt = parsedInitialDebt ?: 0.0
            if (dbHelper.updateCustomer(customer.id, customer.name, n, s, m, initialDebt)) {
                Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show()
                loadAllData()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "تعذر التعديل؛ ربما الاسم مستخدم مسبقاً", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun showEditTransactionDialog(trans: Transaction) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_trans, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        (view.findViewById(R.id.tv_edit_trans_name) as TextView).text = "الزبون: " + trans.customerName
        val tvTypeLabel = view.findViewById<TextView>(R.id.tv_edit_trans_type_label)
        val inputAmount = view.findViewById(R.id.edit_trans_amount) as EditText
        val inputServiceAmount = view.findViewById(R.id.edit_trans_service_amount) as EditText
        val inputNote = view.findViewById(R.id.edit_trans_note) as EditText
        if (trans.type == 1) {
            tvTypeLabel.text = "نوع الحركة: دين (تم من التحويل)"
            tvTypeLabel.setTextColor(ContextCompat.getColor(this, R.color.colorDanger))
            inputServiceAmount.visibility = View.VISIBLE
            inputServiceAmount.setText(
                if (trans.serviceAmount > 0) trans.serviceAmount.toString() else ""
            )
        } else {
            tvTypeLabel.text = "نوع الحركة: دفعة (تسديد)"
            tvTypeLabel.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
            inputServiceAmount.visibility = View.GONE
        }
        inputAmount.setText(trans.amount.toString())
        inputNote.setText(trans.note)
        (view.findViewById(R.id.btn_update_trans) as Button).setOnClickListener {
            val amount = inputAmount.text.toString().trim().toIntOrNull() ?: 0
            if (amount <= 0) {
                Toast.makeText(this, "أدخل مبلغاً صحيحاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val serviceAmount = if (trans.type == 1) {
                inputServiceAmount.text.toString().trim().toIntOrNull() ?: 0
            } else {
                0
            }
            if (trans.type == 1 && serviceAmount <= 0) {
                Toast.makeText(this, "أدخل كمية الرصيد / الخدمة بشكل صحيح", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dbHelper.updateTransaction(
                    trans.id,
                    amount,
                    trans.type,
                    inputNote.text.toString().trim(),
                    trans.serviceType,
                    serviceAmount
                )
            ) {
                Toast.makeText(this, "تم التعديل", Toast.LENGTH_SHORT).show()
                loadAllData()
                findViewById<RecyclerView>(R.id.recycler_companies).adapter?.notifyDataSetChanged()
                refreshCompanyLog()
                dialog.dismiss()
            } else {
                Toast.makeText(
                    this,
                    "تعذر التعديل؛ تحقق من رصيد الشركة أو صحة البيانات",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        dialog.show()
    }
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
        autoAdapterTransfer = createNameAdapter(customerNamesList)
        (findViewById(R.id.input_transfer_customer) as AutoCompleteTextView).setAdapter(autoAdapterTransfer)
        autoAdapterTrans = createNameAdapter(customerNamesList)
        (findViewById(R.id.input_trans_customer) as AutoCompleteTextView).setAdapter(autoAdapterTrans)
        val inputSearchTrans = findViewById<AutoCompleteTextView>(R.id.input_search_trans)
        inputSearchTrans.setAdapter(createNameAdapter(customerNamesList.toList()))
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
        (findViewById(R.id.btn_show_pending) as Button).text =
            getString(R.string.pending_orders, pendingCount)
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
}

internal fun formatLedgerBalance(value: Double, positiveIsOwedToUs: Boolean): String {
    if (!value.isFinite() || value == 0.0) return "0"
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(kotlin.math.abs(value))
    val label = if ((value > 0) == positiveIsOwedToUs) "له" else "لنا"
    return "$formatted ($label)"
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
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { val customer = customers[position]; holder.tvName.text = customer.name; holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "SYRIATEL: " + customer.phoneSyriatel else "SYRIATEL: -"; holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: " + customer.phoneMtn else "MTN: -"; if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1); val balance = dbHelper.getCustomerBalance(customer.name); holder.tvBalance.text = formatLedgerBalance(balance, positiveIsOwedToUs = true); val cColor = when { balance > 0 -> ContextCompat.getColor(holder.itemView.context, R.color.colorDanger); balance < 0 -> ContextCompat.getColor(holder.itemView.context, R.color.colorSuccess); else -> ContextCompat.getColor(holder.itemView.context, R.color.cyan) }; holder.tvBalance.setTextColor(cColor); holder.btnEdit.setOnClickListener { onEdit(customer) }; holder.btnDelete.setOnClickListener { onDelete(customer) }; holder.btnPdf.setOnClickListener { onPdf(customer) } }
    override fun getItemCount() = customers.size
}

class TransactionAdapter(var transactions: List<Transaction>, private val onEdit: (Transaction) -> Unit, private val onDelete: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val tvName = view.findViewById(R.id.tv_trans_name) as TextView; val tvNote = view.findViewById(R.id.tv_trans_note) as TextView; val tvDate = view.findViewById(R.id.tv_trans_date) as TextView; val tvAmount = view.findViewById(R.id.tv_trans_amount) as TextView; val tvType = view.findViewById(R.id.tv_trans_type) as TextView; val tvService = view.findViewById(R.id.tv_trans_service) as TextView; val btnEdit = view.findViewById(R.id.btn_edit_trans) as ImageView; val btnDelete = view.findViewById(R.id.btn_delete_trans) as ImageView }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { val trans = transactions[position]; holder.tvName.text = trans.customerName; holder.tvNote.text = trans.note; holder.tvDate.text = trans.date; holder.tvAmount.text = NumberFormat.getNumberInstance(Locale.US).format(trans.amount); val svcStr = if (trans.serviceType.isNotEmpty()) trans.serviceType else if (trans.type == 2) "دفعة عامة" else "رصيد"; holder.tvService.text = svcStr; if (trans.type == 1) { holder.tvType.text = "دين"; holder.tvType.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorDanger)); holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorDanger)) } else { holder.tvType.text = "دفعة"; holder.tvType.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSuccess)); holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorSuccess)) }; holder.btnEdit.setOnClickListener { onEdit(trans) }; holder.btnDelete.setOnClickListener { onDelete(trans) } }
    override fun getItemCount() = transactions.size
}

class CompanyAdapter(
    var companies: List<Company>,
    private val dbHelper: DatabaseHelper,
    private val context: Context,
    private val onEdit: (Company) -> Unit
) : RecyclerView.Adapter<CompanyAdapter.ViewHolder>() {
    private val expandedCompanies = HashSet<Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById<TextView>(R.id.tv_comp_name)
        val btnToggle = view.findViewById<Button>(R.id.btn_comp_toggle)
        val layoutDetails = view.findViewById<View>(R.id.layout_comp_details)
        val tvBalance = view.findViewById<TextView>(R.id.tv_comp_balance)
        val tvProfit = view.findViewById<TextView>(R.id.tv_company_profit)
        val tvDebt = view.findViewById<TextView>(R.id.tv_comp_debt)
        val tvBuyPrice = view.findViewById<TextView>(R.id.tv_comp_buy_price)
        val tvSellPrice = view.findViewById<TextView>(R.id.tv_comp_sell_price)
        val tvInitialBalance = view.findViewById<TextView>(R.id.tv_comp_initial_balance)
        val btnCharge = view.findViewById<Button>(R.id.btn_comp_charge)
        val btnPay = view.findViewById<Button>(R.id.btn_comp_pay)
        val btnReport = view.findViewById<Button>(R.id.btn_comp_report)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_company, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comp = companies[position]
        holder.tvName.text = comp.name
        holder.tvName.setOnClickListener { onEdit(comp) }
        val numberFormat = NumberFormat.getNumberInstance(Locale.US)
        holder.tvBuyPrice.text = "سعر الشراء: ${numberFormat.format(comp.buyPrice)}"
        holder.tvSellPrice.text = "سعر البيع: ${numberFormat.format(comp.sellPrice)}"
        holder.tvInitialBalance.text = "الرصيد الابتدائي: ${numberFormat.format(comp.initialBalance)}"

        fun updateNumbers() {
            val avail = dbHelper.getCompanyAvailableBalance(comp.id, comp.name)
            val debt = dbHelper.getCompanyFinancialDebt(comp.name)
            val profit = dbHelper.getCompanyProfit(comp.name)
            holder.tvBalance.text = numberFormat.format(avail)
            holder.tvProfit.text = "الأرباح: ${numberFormat.format(profit)}"
            holder.tvDebt.text = formatLedgerBalance(debt, positiveIsOwedToUs = false) + " ل.س"
        }

        fun applyExpandedState() {
            val expanded = expandedCompanies.contains(comp.id)
            holder.layoutDetails.visibility = if (expanded) View.VISIBLE else View.GONE
            holder.btnToggle.text = if (expanded) "إخفاء التفاصيل" else "عرض التفاصيل والأرصدة"
            if (expanded) updateNumbers()
        }

        applyExpandedState()
        holder.btnToggle.setOnClickListener {
            if (expandedCompanies.contains(comp.id)) expandedCompanies.remove(comp.id) else expandedCompanies.add(comp.id)
            applyExpandedState()
        }
        holder.btnCharge.setOnClickListener {
            if (context is MainActivity) context.showCompanyChargeDialog(comp) { updateNumbers() }
        }
        holder.btnPay.setOnClickListener {
            if (context is MainActivity) context.showCompanyPayDialog(comp.name) { updateNumbers() }
        }
        holder.btnReport.setOnClickListener {
            if (context is MainActivity) context.showCompanyReportDialog(comp.name)
        }
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
        val numberFormat = NumberFormat.getNumberInstance(Locale.US)
        holder.tvAmount.text = when (entry.type) {
            "شحن رصيد" -> "كمية الرصيد: ${numberFormat.format(entry.quantity)} | التكلفة: ${numberFormat.format(entry.amount)} ل.س"
            "مبيعات" -> "الكمية: ${numberFormat.format(entry.quantity)} | القيمة: ${numberFormat.format(entry.amount)} ل.س"
            else -> "المبلغ المدفوع: ${numberFormat.format(entry.amount)} ل.س"
        }
        holder.tvNote.text = entry.note.ifBlank { "بدون ملاحظة" }
        holder.tvDate.text = entry.date
    }

    override fun getItemCount() = entries.size
}

class CashTransactionAdapter(var items: List<CashTransaction>) :
    RecyclerView.Adapter<CashTransactionAdapter.ViewHolder>() {
    class ViewHolder(view: TextView) : RecyclerView.ViewHolder(view) {
        val details: TextView = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val padding = (16 * parent.resources.displayMetrics.density).toInt()
        val textView = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * parent.resources.displayMetrics.density).toInt()
            }
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(R.drawable.bg_panel)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            textDirection = View.TEXT_DIRECTION_RTL
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val typeLabel = if (item.type == 0) "صرف" else "إيداع"
        val amount = NumberFormat.getNumberInstance(Locale.US).format(item.amount)
        holder.details.text = "$typeLabel: $amount ل.س\n${item.note.ifBlank { "بدون ملاحظة" }}\n${item.date}"
        holder.details.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (item.type == 0) R.color.colorDanger else R.color.colorSuccess
            )
        )
    }

    override fun getItemCount(): Int = items.size
}

class OfficialAdapter(
    var officials: List<Official>,
    private val dbHelper: DatabaseHelper,
    private val onEdit: (Official) -> Unit,
    private val onDelete: (Official) -> Unit
) : RecyclerView.Adapter<OfficialAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_official_name)
        val balance: TextView = view.findViewById(R.id.tv_official_balance)
        val edit: ImageView = view.findViewById(R.id.btn_edit_official)
        val delete: ImageView = view.findViewById(R.id.btn_delete_official)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_official, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val official = officials[position]
        holder.name.text = official.name
        val debt = dbHelper.getOfficialDebt(official.id)
        holder.balance.text = formatLedgerBalance(debt, positiveIsOwedToUs = false)
        holder.edit.setOnClickListener { onEdit(official) }
        holder.delete.setOnClickListener { onDelete(official) }
    }

    override fun getItemCount(): Int = officials.size
}