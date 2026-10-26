package com.example.dsmproyectodecatedra.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dsmproyectodecatedra.MainActivity
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.UserRepository
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val repository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (repository.currentUser != null) {
            goToMain()
            return
        }

        val layoutEmail = findViewById<TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<TextInputLayout>(R.id.layoutPassword)
        val buttonLogin = findViewById<Button>(R.id.buttonLogin)
        val textGoRegister = findViewById<TextView>(R.id.textGoRegister)
        val textForgotPassword = findViewById<TextView>(R.id.textForgotPassword)

        buttonLogin.setOnClickListener {
            val email = layoutEmail.editText?.text?.toString()?.trim().orEmpty()
            val password = layoutPassword.editText?.text?.toString().orEmpty()

            if (!validate(layoutEmail, layoutPassword, email, password)) return@setOnClickListener

            setLoading(buttonLogin, true)
            lifecycleScope.launch {
                try {
                    repository.login(email, password)
                    goToMain()
                } catch (e: Exception) {
                    Snackbar.make(buttonLogin, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                } finally {
                    setLoading(buttonLogin, false)
                }
            }
        }

        textGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        textForgotPassword.setOnClickListener {
            val email = layoutEmail.editText?.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layoutEmail.error = getString(R.string.error_invalid_email)
                return@setOnClickListener
            }
            layoutEmail.error = null
            lifecycleScope.launch {
                try {
                    repository.sendPasswordReset(email)
                    Snackbar.make(buttonLogin, getString(R.string.msg_reset_sent), Snackbar.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Snackbar.make(buttonLogin, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validate(
        layoutEmail: TextInputLayout,
        layoutPassword: TextInputLayout,
        email: String,
        password: String
    ): Boolean {
        layoutEmail.error = null
        layoutPassword.error = null

        var valid = true
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.error = getString(R.string.error_invalid_email)
            valid = false
        }
        if (password.isEmpty()) {
            layoutPassword.error = getString(R.string.error_required_field)
            valid = false
        }
        return valid
    }

    private fun setLoading(button: Button, loading: Boolean) {
        button.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
