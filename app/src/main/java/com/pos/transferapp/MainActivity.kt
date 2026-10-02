package com.pos.transferapp

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    lateinit var dbHelper: DatabaseHelper
    lateinit var customerAdapter: CustomerAdapter
    var customerList = ArrayList<Customer>()

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
            val orange = Color.parseColor("#FF6B00")
            val gray = Color.parseColor("#888888")
            
            ivTransfer.setColorFilter(if (selected == "transfer") orange else gray, PorterDuff.Mode.SRC_IN)
            tvTransfer.setTextColor(if (selected == "transfer") orange else gray)
            ivCustomers.setColorFilter(if (selected == "customers") orange else gray, PorterDuff.Mode.SRC_IN)
            tvCustomers.setTextColor(if (selected == "customers") orange else gray)
            ivDebt.setColorFilter(if (selected == "debt") orange else gray, PorterDuff.Mode.SRC_IN)
            tvDebt.setTextColor(if (selected == "debt") orange else gray)
            ivSettings.setColorFilter(if (selected == "settings") orange else gray, PorterDuff.Mode.SRC_IN)
            tvSettings.setTextColor(if (selected == "settings") orange else gray)
        }

        navTransfer.setOnClickListener {
            layoutTransfer.visibility = View.VISIBLE
            layoutCustomers.visibility = View.GONE
            layoutDebt.visibility = View.GONE
            layoutSettings.visibility = View.GONE
            tvMainTitle.text = "التحويل السريع"
            updateNavUI("transfer")
        }
        navCustomers.setOnClickListener {
            layoutTransfer.visibility = View.GONE
            layoutCustomers.visibility = View.VISIBLE
            layoutDebt.visibility = View.GONE
            layoutSettings.visibility = View.GONE
            tvMainTitle.text = "إدارة الزبائن"
            updateNavUI("customers")
        }
        navDebt.setOnClickListener {
            layoutTransfer.visibility = View.GONE
            layoutCustomers.visibility = View.GONE
            layoutDebt.visibility = View.VISIBLE
            layoutSettings.visibility = View.GONE
            tvMainTitle.text = "الحسابات والدفعات"
            updateNavUI("debt")
        }
        navSettings.setOnClickListener {
            layoutTransfer.visibility = View.GONE
            layoutCustomers.visibility = View.GONE
            layoutDebt.visibility = View.GONE
            layoutSettings.visibility = View.VISIBLE
            tvMainTitle.text = "الإعدادات"
            updateNavUI("settings")
        }

        // برمجة قسم الزبائن
        val inputCustName = findViewById(R.id.input_cust_name) as EditText
        val inputCustSyriatel = findViewById(R.id.input_cust_syriatel) as EditText
        val inputCustMtn = findViewById(R.id.input_cust_mtn) as EditText
        val btnSaveCust = findViewById(R.id.btn_save_customer) as Button
        val recyclerCustomers = findViewById(R.id.recycler_customers) as RecyclerView
        
        recyclerCustomers.layoutManager = LinearLayoutManager(this)
        customerAdapter = CustomerAdapter(customerList) { selectedCustomer ->
            // حالياً نعرض رسالة، لاحقاً يمكن إضافة خيار لتعديل الزبون
            Toast.makeText(this, "اخترت: ${selectedCustomer.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerCustomers.adapter = customerAdapter

        btnSaveCust.setOnClickListener {
            val name = inputCustName.text.toString().trim()
            val syriatel = inputCustSyriatel.text.toString().trim()
            val mtn = inputCustMtn.text.toString().trim()

            if (name.isNotEmpty() && (syriatel.isNotEmpty() || mtn.isNotEmpty())) {
                if (dbHelper.addCustomer(name, syriatel, mtn)) {
                    Toast.makeText(this, "تم حفظ الزبون بنجاح", Toast.LENGTH_SHORT).show()
                    inputCustName.text.clear()
                    inputCustSyriatel.text.clear()
                    inputCustMtn.text.clear()
                    loadCustomersData()
                } else {
                    Toast.makeText(this, "حدث خطأ أثناء الحفظ", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "الرجاء إدخال الاسم ورقم واحد على الأقل", Toast.LENGTH_LONG).show()
            }
        }

        // تحميل البيانات أول ما يفتح التطبيق
        loadCustomersData()
        
        // عرض شاشة الزبائن كشاشة افتراضية مؤقتاً لتسهيل التجربة
        navCustomers.performClick()
    }

    private fun loadCustomersData() {
        customerList.clear()
        customerList.addAll(dbHelper.getAllCustomers())
        customerAdapter.notifyDataSetChanged()
    }
}

class CustomerAdapter(private val customers: List<Customer>, private val onItemClick: (Customer) -> Unit) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName = view.findViewById(R.id.tv_customer_name) as TextView
        val tvSyriatel = view.findViewById(R.id.tv_syriatel) as TextView
        val tvMtn = view.findViewById(R.id.tv_mtn) as TextView
        val tvInitial = view.findViewById(R.id.tv_initial) as TextView
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_customer, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val customer = customers[position]
        holder.tvName.text = customer.name
        
        holder.tvSyriatel.text = if (customer.phoneSyriatel.isNotEmpty()) "سيريتل: ${customer.phoneSyriatel}" else "سيريتل: لا يوجد"
        holder.tvMtn.text = if (customer.phoneMtn.isNotEmpty()) "MTN: ${customer.phoneMtn}" else "MTN: لا يوجد"
        
        if (customer.name.isNotEmpty()) holder.tvInitial.text = customer.name.take(1)
        holder.itemView.setOnClickListener { onItemClick(customer) }
    }
    override fun getItemCount() = customers.size
}