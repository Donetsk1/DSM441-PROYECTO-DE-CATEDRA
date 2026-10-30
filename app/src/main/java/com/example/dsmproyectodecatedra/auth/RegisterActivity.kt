package com.example.dsmproyectodecatedra.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dsmproyectodecatedra.MainActivity
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.UserRepository
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RegisterActivity : AppCompatActivity() {

    private val repository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val layoutFullName = findViewById<TextInputLayout>(R.id.layoutFullName)
        val layoutStudentId = findViewById<TextInputLayout>(R.id.layoutStudentId)
        val layoutCareer = findViewById<TextInputLayout>(R.id.layoutCareer)
        val spinnerCurrency = findViewById<Spinner>(R.id.spinnerCurrency)
        val layoutEmail = findViewById<TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<TextInputLayout>(R.id.layoutPassword)
        val layoutConfirmPassword = findViewById<TextInputLayout>(R.id.layoutConfirmPassword)
        val buttonRegister = findViewById<Button>(R.id.buttonRegister)
        val textGoLogin = findViewById<TextView>(R.id.textGoLogin)

        buttonRegister.setOnClickListener {
            val fullName = layoutFullName.editText?.text?.toString()?.trim().orEmpty()
            val studentId = layoutStudentId.editText?.text?.toString()?.trim().orEmpty()
            val career = layoutCareer.editText?.text?.toString()?.trim().orEmpty()
            val currency = spinnerCurrency.selectedItem?.toString().orEmpty()
            val email = layoutEmail.editText?.text?.toString()?.trim().orEmpty()
            val password = layoutPassword.editText?.text?.toString().orEmpty()
            val confirmPassword = layoutConfirmPassword.editText?.text?.toString().orEmpty()

            if (!validate(
                    layoutFullName, layoutStudentId, layoutCareer,
                    layoutEmail, layoutPassword, layoutConfirmPassword,
                    fullName, studentId, career, email, password, confirmPassword
                )
            ) return@setOnClickListener

            buttonRegister.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.register(email, password, fullName, studentId, career, currency)
                    goToMain()
                } catch (e: Exception) {
                    Snackbar.make(buttonRegister, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                } finally {
                    buttonRegister.isEnabled = true
                }
            }
        }

        textGoLogin.setOnClickListener { finish() }
    }

    private fun validate(
        layoutFullName: TextInputLayout,
        layoutStudentId: TextInputLayout,
        layoutCareer: TextInputLayout,
        layoutEmail: TextInputLayout,
        layoutPassword: TextInputLayout,
        layoutConfirmPassword: TextInputLayout,
        fullName: String,
        studentId: String,
        career: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        layoutFullName.error = null
        layoutStudentId.error = null
        layoutCareer.error = null
        layoutEmail.error = null
        layoutPassword.error = null
        layoutConfirmPassword.error = null

        var valid = true
        if (fullName.isEmpty()) {
            layoutFullName.error = getString(R.string.error_required_field)
            valid = false
        }
        if (studentId.isEmpty()) {
            layoutStudentId.error = getString(R.string.error_required_field)
            valid = false
        }
        if (career.isEmpty()) {
            layoutCareer.error = getString(R.string.error_required_field)
            valid = false
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.error = getString(R.string.error_invalid_email)
            valid = false
        }
        if (password.length < 6) {
            layoutPassword.error = getString(R.string.error_password_too_short)
            valid = false
        }
        if (confirmPassword != password) {
            layoutConfirmPassword.error = getString(R.string.error_passwords_do_not_match)
            valid = false
        }
        return valid
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
