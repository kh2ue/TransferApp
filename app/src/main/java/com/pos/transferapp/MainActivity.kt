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

        val ivTransfer = navTransfer.getChildAt(0) as ImageView
        val tvTransfer = navTransfer.getChildAt(1) as TextView
        val ivCustomers = navCustomers.getChildAt(0) as ImageView
        val tvCustomers = navCustomers.getChildAt(1) as TextView
        val ivDebt = navDebt.getChildAt(0) as ImageView
        val tvDebt = navDebt.getChildAt(1) as TextView
        val ivSettings = navSettings.getChildAt(0) as ImageView
        val tvSettings = navSettings.getChildAt(1) as TextView

        fun updateNavUI(selected: String) {
            val orange = Color.parseColor("#FF6B00"); val gray = Color.parseColor("#888888")
            ivTransfer.setColorFilter(if (selected == "transfer") orange else gray, PorterDuff.Mode.SRC_IN)
            tvTransfer.setTextColor(if (selected == "transfer") orange else gray)
            ivCustomers.setColorFilter(if (selected == "customers") orange else gray, PorterDuff.Mode.SRC_IN)
            tvCustomers.setTextColor(if (selected == "customers") orange else gray)
            ivDebt.setColorFilter(if (selected == "debt") orange else gray, PorterDuff.Mode.SRC_IN)
            tvDebt.setTextColor(if (selected == "debt") orange else gray)
            ivSettings.setColorFilter(if (selected == "settings") orange else gray, PorterDuff.Mode.SRC_IN)
            tvSettings.setTextColor(if (selected == "settings") orange else gray)
        }

        navTransfer.setOnClickListener { layoutTransfer.visibility = View.VISIBLE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "التحويل السريع"; updateNavUI("transfer") }
        navCustomers.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.VISIBLE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "إدارة الزبائن"; updateNavUI("customers") }
        navDebt.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.VISIBLE; layoutSettings.visibility = View.GONE; tvMainTitle.text = "الحسابات والدفعات"; updateNavUI("debt") }
        navSettings.setOnClickListener { layoutTransfer.visibility = View.GONE; layoutCustomers.visibility = View.GONE; layoutDebt.visibility = View.GONE; layoutSettings.visibility = View.VISIBLE; tvMainTitle.text = "الإعدادات"; updateNavUI("settings") }

        // ================= 1. قسم التحويل =================
        val inputTransferCustomer = findViewById(R.id.input_transfer_customer) as AutoCompleteTextView
        val rbSyr = findViewById(R.id.rb_syr) as RadioButton
        val rbMtn = findViewById(R.id.rb_mtn) as RadioButton
        val spinnerQuickAmounts = findViewById(R.id.spinner_quick_amounts) as Spinner
        val inputTransferAmount = findViewById(R.id.input_transfer_amount) as EditText
        val inputTransferPrice = findViewById(R.id.input_transfer_price) as EditText
        val btnExecuteTransfer = findViewById(R.id.btn_execute_transfer) as Button

        inputTransferCustomer.setOnClickListener { inputTransferCustomer.showDropDown() }
        inputTransferCustomer.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) inputTransferCustomer.showDropDown() }
        autoAdapterTransfer = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        inputTransferCustomer.setAdapter(autoAdapterTransfer)

        fun parseAmounts(rawString: String): ArrayList<Pair<String, String>> {
            val list = ArrayList<Pair<String, String>>()
            val items = rawString.split(",")
            for (item in items) {
                val parts = item.trim().split(":")
                if (parts.size >= 2) list.add(Pair(parts[0].trim(), parts[1].trim()))
                else if (parts.size == 1 && parts[0].trim().isNotEmpty()) list.add(Pair(parts[0].trim(), parts[0].trim()))
            }
            return list
        }

        fun updateSpinner(isSyr: Boolean) {
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val raw = if (isSyr) prefs.getString("syr_amounts", "1000:1300, 2000:2600") else prefs.getString("mtn_amounts", "1000:1300, 2000:2600")
            currentQuickAmounts = parseAmounts(raw ?: "")
            val displayList = ArrayList<String>(); displayList.add("اختر الفئة (اختياري)...")
            for (pair in currentQuickAmounts) displayList.add("مبلغ: ${pair.first} رصيد | التكلفة: ${pair.second} ل.س")
            spinnerQuickAmounts.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)
            inputTransferAmount.setText(""); inputTransferPrice.setText("")
        }

        rbSyr.setOnCheckedChangeListener { _, isChecked -> if(isChecked) updateSpinner(true) }
        rbMtn.setOnCheckedChangeListener { _, isChecked -> if(isChecked) updateSpinner(false) }

        spinnerQuickAmounts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val selectedPair = currentQuickAmounts[position - 1]
                    inputTransferAmount.setText(selectedPair.first); inputTransferPrice.setText(selectedPair.second)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnExecuteTransfer.setOnClickListener {
            val selectedName = inputTransferCustomer.text.toString().trim()
            val amount = inputTransferAmount.text.toString().trim()
            val priceStr = inputTransferPrice.text.toString().trim()
            val isSyr = rbSyr.isChecked
            
            val customer = customerList.find { it.name == selectedName }
            if (customer == null) { Toast.makeText(this, "اختر زبون مسجل", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val targetPhone = if (isSyr) customer.phoneSyriatel else customer.phoneMtn
            if (targetPhone.isEmpty()) { Toast.makeText(this, "لا يوجد رقم ${if (isSyr) "Syr" else "MTN"}", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            if (amount.isEmpty() || priceStr.isEmpty()) { Toast.makeText(this, "أدخل المبلغ والسعر", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            val price = priceStr.toIntOrNull() ?: 0
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val pin = prefs.getString("default_pin", "") ?: ""
            val finalPin = if (pin.isEmpty()) "0000" else pin

            val ussd = "*150*$targetPhone*$amount*$finalPin"
            pendingUssdCode = ussd + Uri.encode("#")
            
            val netName = if(isSyr) "Syr" else "MTN"
            dbHelper.addTransaction(selectedName, price, 1, "تحويل $amount رصيد ($netName)")
            loadAllData() 
            Toast.makeText(this, "تم قيد $price ل.س كدين", Toast.LENGTH_SHORT).show()
            
            checkPermissionAndCall()
        }

        // ================= 2. الإعدادات =================
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val inputSettingsPin = findViewById(R.id.input_settings_pin) as EditText
        val inputSettingsSyrAmounts = findViewById(R.id.input_settings_syr_amounts) as EditText
        val inputSettingsMtnAmounts = findViewById(R.id.input_settings_mtn_amounts) as EditText
        val btnSaveSettings = findViewById(R.id.btn_save_settings) as Button

        inputSettingsPin.setText(prefs.getString("default_pin", ""))
        inputSettingsSyrAmounts.setText(prefs.getString("syr_amounts", "1000:1300, 2000:2600, 5000:6500"))
        inputSettingsMtnAmounts.setText(prefs.getString("mtn_amounts", "1000:1250, 2000:2500, 5000:6250"))

        btnSaveSettings.setOnClickListener {
            prefs.edit().putString("default_pin", inputSettingsPin.text.toString().trim())
                .putString("syr_amounts", inputSettingsSyrAmounts.text.toString().trim())
                .putString("mtn_amounts", inputSettingsMtnAmounts.text.toString().trim()).apply()
            Toast.makeText(this, "تم حفظ الإعدادات بنجاح!", Toast.LENGTH_SHORT).show()
            updateSpinner(rbSyr.isChecked)
        }

        // ================= 3. الزبائن (تعديل بضغطة / حذف بضغطة مطولة) =================
        val inputCustName = findViewById(R.id.input_cust_name) as EditText
        val inputCustSyriatel = findViewById(R.id.input_cust_syriatel) as EditText
        val inputCustMtn = findViewById(R.id.input_cust_mtn) as EditText
        val btnSaveCust = findViewById(R.id.btn_save_customer) as Button
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList, dbHelper, { customerToEdit ->
            // دالة التعديل للزبون
            showEditCustomerDialog(customerToEdit)
        }, { customerToDelete ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("تأكيد الحذف").setMessage("هل تريد حذف الزبون: ${customerToDelete.name}؟")
            builder.setPositiveButton("حذف") { _, _ -> if (dbHelper.deleteCustomer(customerToDelete.id)) { Toast.makeText(this, "تم حذف الزبون", Toast.LENGTH_SHORT).show(); loadAllData() } }
            builder.setNegativeButton("إلغاء", null).show()
        })
        recyclerCustomers.adapter = customerAdapter

        btnSaveCust.setOnClickListener {
            val name = inputCustName.text.toString().trim()
            val syriatel = inputCustSyriatel.text.toString().trim()
            val mtn = inputCustMtn.text.toString().trim()
            if (name.isNotEmpty() && (syriatel.isNotEmpty() || mtn.isNotEmpty())) {
                if (dbHelper.addCustomer(name, syriatel, mtn)) {
                    Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
                    inputCustName.text.clear(); inputCustSyriatel.text.clear(); inputCustMtn.text.clear()
                    loadAllData()
                }
            } else { Toast.makeText(this, "أدخل الاسم ورقم واحد", Toast.LENGTH_SHORT).show() }
        }

        // ================= 4. الحسابات (تعديل بضغطة / حذف بضغطة مطولة) =================
        tvTotalDebt = findViewById(R.id.tv_total_debt) as TextView
        val inputTransCustomer = findViewById(R.id.input_trans_customer) as AutoCompleteTextView
        val rbDebt = findViewById(R.id.rb_debt) as RadioButton
        val inputTransAmount = findViewById(R.id.input_trans_amount) as EditText
        val inputTransNote = findViewById(R.id.input_trans_note) as EditText
        val btnSaveTrans = findViewById(R.id.btn_save_trans) as Button
        val recyclerTransactions = findViewById(R.id.recycler_transactions) as RecyclerView

        inputTransCustomer.setOnClickListener { inputTransCustomer.showDropDown() }
        inputTransCustomer.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) inputTransCustomer.showDropDown() }
        autoAdapterTrans = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerNamesList)
        inputTransCustomer.setAdapter(autoAdapterTrans)

        recyclerTransactions.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(transactionList, { transToEdit ->
            // دالة التعديل للحركة
            showEditTransactionDialog(transToEdit)
        }, { transToDelete ->
            val builder = AlertDialog.Builder(this)
            builder.setTitle("حذف الحركة").setMessage("هل تريد حذف الحركة (${transToDelete.amount}) العائدة لـ ${transToDelete.customerName}؟")
            builder.setPositiveButton("حذف") { _, _ -> if (dbHelper.deleteTransaction(transToDelete.id)) { Toast.makeText(this, "تم حذف الحركة", Toast.LENGTH_SHORT).show(); loadAllData() } }
            builder.setNegativeButton("إلغاء", null).show()
        })
        recyclerTransactions.adapter = transactionAdapter

        btnSaveTrans.setOnClickListener {
            val name = inputTransCustomer.text.toString().trim()
            val amountStr = inputTransAmount.text.toString().trim()
            val note = inputTransNote.text.toString().trim()
            val type = if (rbDebt.isChecked) 1 else 2
            if (name.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                if (dbHelper.addTransaction(name, amount, type, note)) {
                    Toast.makeText(this, "تم تسجيل الحركة", Toast.LENGTH_SHORT).show()
                    inputTransCustomer.text.clear(); inputTransAmount.text.clear(); inputTransNote.text.clear()
                    loadAllData()
                }
            } else { Toast.makeText(this, "أدخل الاسم والمبلغ", Toast.LENGTH_SHORT).show() }
        }

        loadAllData()
        updateSpinner(true)
        navTransfer.performClick()
    }

    private fun showEditCustomerDialog(customer: Customer) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_customer, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val inputName = dialogView.findViewById(R.id.input_edit_cust_name) as EditText
        val inputSyr = dialogView.findViewById(R.id.input_edit_cust_syr) as EditText
        val inputMtn = dialogView.findViewById(R.id.input_edit_cust_mtn) as EditText
        val btnSave = dialogView.findViewById(R.id.btn_save_edit_cust) as Button

        inputName.setText(customer.name)
        inputSyr.setText(customer.phoneSyriatel)
        inputMtn.setText(customer.phoneMtn)

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val syr = inputSyr.text.toString().trim()
            val mtn = inputMtn.text.toString().trim()
            if (name.isNotEmpty() && (syr.isNotEmpty() || mtn.isNotEmpty())) {
                if (dbHelper.updateCustomer(customer.id, name, syr, mtn)) {
                    Toast.makeText(this, "تم تعديل الزبون بنجاح", Toast.LENGTH_SHORT).show()
                    loadAllData()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "أدخل الاسم ورقم واحد على الأقل", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showEditTransactionDialog(trans: Transaction) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_transaction, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        val tvName = dialogView.findViewById(R.id.tv_edit_trans_name) as TextView
        val rbDebt = dialogView.findViewById(R.id.rb_edit_debt) as RadioButton
        val rbPayment = dialogView.findViewById(R.id.rb_edit_payment) as RadioButton
        val inputAmount = dialogView.findViewById(R.id.input_edit_trans_amount) as EditText
        val inputNote = dialogView.findViewById(R.id.input_edit_trans_note) as EditText
        val btnSave = dialogView.findViewById(R.id.btn_save_edit_trans) as Button

        tvName.text = "الحساب: ${trans.customerName}"
        if (trans.type == 1) rbDebt.isChecked = true else rbPayment.isChecked = true
        inputAmount.setText(trans.amount.toString())
        inputNote.setText(trans.note)

        btnSave.setOnClickListener {
            val amountStr = inputAmount.text.toString().trim()
            val note = inputNote.text.toString().trim()
            val type = if (rbDebt.isChecked) 1 else 2
            
            if (amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                if (dbHelper.updateTransaction(trans.id, amount, type, note)) {
                    Toast.makeText(this, "تم تعديل الحركة بنجاح", Toast.LENGTH_SHORT).show()
                    loadAllData()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال المبلغ", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun loadAllData() {
        customerList.clear(); customerList.addAll(dbHelper.getAllCustomers()); customerAdapter.notifyDataSetChanged()
        customerNamesList.clear(); customerNamesList.addAll(customerList.map { it.name })
        autoAdapterTransfer.notifyDataSetChanged(); autoAdapterTrans.notifyDataSetChanged()
        transactionList.clear(); transactionList.addAll(dbHelper.getAllTransactions()); transactionAdapter.notifyDataSetChanged()
        
        val total = dbHelper.getTotalDebt()
        tvTotalDebt.text = "${NumberFormat.getNumberInstance(Locale.US).format(total)} ل.س"
    }

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_REQUEST_CODE)
        } else { executeUSSD() }
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
        if (requestCode == CALL_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { executeUSSD() }
    }
}

class CustomerAdapter(private val customers: List<Customer>, private val dbHelper: DatabaseHelper, private val onItemClick: (Customer) -> Unit, private val onItemLongClick: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvSyriatel = view.findViewById(R.id.tv_syriatel) as TextView
        val tvMtn = view.findViewById(R.id.tv_mtn) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
        val tvBalance = view.findViewById(R.id.tv_customer_balance) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "Syr: ${customer.phoneSyriatel}" else "Syr: -"
        holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: ${customer.phoneMtn}" else "MTN: -"
        if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1)
        
        val balance = dbHelper.getCustomerBalance(customer.name)
        holder.tvBalance.text = NumberFormat.getNumberInstance(Locale.US).format(balance)
        if(balance < 0) holder.tvBalance.setTextColor(Color.parseColor("#388E3C")) else holder.tvBalance.setTextColor(Color.parseColor("#FF6B00"))

        holder.itemView.setOnClickListener { onItemClick(customer) }
        holder.itemView.setOnLongClickListener { onItemLongClick(customer); true }
    }
    override fun getItemCount() = customers.size
}

class TransactionAdapter(private val transactions: List<Transaction>, private val onItemClick: (Transaction) -> Unit, private val onItemLongClick: (Transaction) -> Unit) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_trans_name) as TextView
        val tvNote = view.findViewById(R.id.tv_trans_note) as TextView
        val tvAmount = view.findViewById(R.id.tv_trans_amount) as TextView
        val tvType = view.findViewById(R.id.tv_trans_type) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trans = transactions[position]
        holder.tvName.text = trans.customerName
        holder.tvNote.text = trans.note
        holder.tvAmount.text = "${NumberFormat.getNumberInstance(Locale.US).format(trans.amount)}"
        if (trans.type == 1) {
            holder.tvType.text = "دين"
            holder.tvType.setTextColor(Color.parseColor("#D32F2F"))
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            holder.tvType.text = "دفعة"
            holder.tvType.setTextColor(Color.parseColor("#388E3C"))
            holder.tvAmount.setTextColor(Color.parseColor("#388E3C"))
        }
        
        holder.itemView.setOnClickListener { onItemClick(trans) }
        holder.itemView.setOnLongClickListener { onItemLongClick(trans); true }
    }
    override fun getItemCount() = transactions.size
}