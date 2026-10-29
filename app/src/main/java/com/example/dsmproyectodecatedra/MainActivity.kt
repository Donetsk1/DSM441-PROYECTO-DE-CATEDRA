package com.example.dsmproyectodecatedra

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.dsmproyectodecatedra.auth.LoginActivity
import com.example.dsmproyectodecatedra.budget.BudgetFragment
import com.example.dsmproyectodecatedra.dashboard.DashboardFragment
import com.example.dsmproyectodecatedra.data.UserRepository
import com.example.dsmproyectodecatedra.history.HistoryFragment
import com.example.dsmproyectodecatedra.news.NewsFragment
import com.example.dsmproyectodecatedra.profile.ProfileActivity
import com.example.dsmproyectodecatedra.transactions.TransactionsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val repository = UserRepository()
    private var requestedMovementId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (repository.currentUser == null) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<MaterialButton>(R.id.buttonProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.buttonLogout).setOnClickListener {
            repository.logout()
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.navDashboard -> DashboardFragment()
                R.id.navTransactions -> TransactionsFragment().apply {
                    arguments = Bundle().apply { putString("editMovementId", requestedMovementId) }
                    requestedMovementId = null
                }
                R.id.navBudget -> BudgetFragment()
                R.id.navHistory -> HistoryFragment()
                R.id.navNews -> NewsFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(fragment)
            true
        }

        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.navDashboard
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.navHostContainer, fragment)
            .commit()
    }

    fun openMovementEditor(id: String) {
        val navigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        requestedMovementId = id
        if (navigation.selectedItemId == R.id.navTransactions) {
            showFragment(TransactionsFragment().apply { arguments = Bundle().apply { putString("editMovementId", id) } })
            requestedMovementId = null
        } else navigation.selectedItemId = R.id.navTransactions
    }
}
