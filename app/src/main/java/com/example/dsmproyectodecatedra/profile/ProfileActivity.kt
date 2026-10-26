package com.example.dsmproyectodecatedra.profile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.auth.LoginActivity
import com.example.dsmproyectodecatedra.data.UserProfile
import com.example.dsmproyectodecatedra.data.UserRepository
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private val repository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val layoutFullName = findViewById<TextInputLayout>(R.id.layoutFullName)
        val layoutStudentId = findViewById<TextInputLayout>(R.id.layoutStudentId)
        val layoutCareer = findViewById<TextInputLayout>(R.id.layoutCareer)
        val spinnerCurrency = findViewById<Spinner>(R.id.spinnerCurrency)
        val buttonSaveProfile = findViewById<Button>(R.id.buttonSaveProfile)
        val layoutCurrentPassword = findViewById<TextInputLayout>(R.id.layoutCurrentPassword)
        val layoutNewPassword = findViewById<TextInputLayout>(R.id.layoutNewPassword)
        val buttonChangePassword = findViewById<Button>(R.id.buttonChangePassword)
        val buttonLogout = findViewById<Button>(R.id.buttonLogout)
        val layoutDeletePassword = findViewById<TextInputLayout>(R.id.layoutDeletePassword)
        val layoutDeleteConfirmWord = findViewById<TextInputLayout>(R.id.layoutDeleteConfirmWord)
        val buttonDeleteAccount = findViewById<Button>(R.id.buttonDeleteAccount)

        val currencies = resources.getStringArray(R.array.currency_codes)

        loadProfile(layoutFullName, layoutStudentId, layoutCareer, spinnerCurrency, currencies)

        buttonSaveProfile.setOnClickListener {
            val fullName = layoutFullName.editText?.text?.toString()?.trim().orEmpty()
            val studentId = layoutStudentId.editText?.text?.toString()?.trim().orEmpty()
            val career = layoutCareer.editText?.text?.toString()?.trim().orEmpty()
            val currency = spinnerCurrency.selectedItem?.toString().orEmpty()

            layoutFullName.error = null
            layoutStudentId.error = null
            layoutCareer.error = null

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
            if (!valid) return@setOnClickListener

            buttonSaveProfile.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.updateProfile(fullName, studentId, career, currency)
                    Snackbar.make(buttonSaveProfile, getString(R.string.msg_profile_saved), Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(buttonSaveProfile, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                } finally {
                    buttonSaveProfile.isEnabled = true
                }
            }
        }

        buttonChangePassword.setOnClickListener {
            val currentPassword = layoutCurrentPassword.editText?.text?.toString().orEmpty()
            val newPassword = layoutNewPassword.editText?.text?.toString().orEmpty()

            layoutCurrentPassword.error = null
            layoutNewPassword.error = null

            var valid = true
            if (currentPassword.isEmpty()) {
                layoutCurrentPassword.error = getString(R.string.error_required_field)
                valid = false
            }
            if (newPassword.length < 6) {
                layoutNewPassword.error = getString(R.string.error_password_too_short)
                valid = false
            }
            if (!valid) return@setOnClickListener

            buttonChangePassword.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.changePassword(currentPassword, newPassword)
                    layoutCurrentPassword.editText?.text?.clear()
                    layoutNewPassword.editText?.text?.clear()
                    Snackbar.make(buttonChangePassword, getString(R.string.msg_password_changed), Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(buttonChangePassword, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                } finally {
                    buttonChangePassword.isEnabled = true
                }
            }
        }

        buttonLogout.setOnClickListener {
            repository.logout()
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        val confirmWord = getString(R.string.confirm_word)
        val deleteInputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val password = layoutDeletePassword.editText?.text?.toString().orEmpty()
                val confirmText = layoutDeleteConfirmWord.editText?.text?.toString().orEmpty()
                buttonDeleteAccount.isEnabled = password.isNotEmpty() && confirmText == confirmWord
            }
        }
        layoutDeletePassword.editText?.addTextChangedListener(deleteInputWatcher)
        layoutDeleteConfirmWord.editText?.addTextChangedListener(deleteInputWatcher)

        buttonDeleteAccount.setOnClickListener {
            val password = layoutDeletePassword.editText?.text?.toString().orEmpty()

            buttonDeleteAccount.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.deleteAccount(password)
                    startActivity(
                        Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                } catch (e: Exception) {
                    Snackbar.make(buttonDeleteAccount, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
                    buttonDeleteAccount.isEnabled = true
                }
            }
        }
    }

    private fun loadProfile(
        layoutFullName: TextInputLayout,
        layoutStudentId: TextInputLayout,
        layoutCareer: TextInputLayout,
        spinnerCurrency: Spinner,
        currencies: Array<String>
    ) {
        lifecycleScope.launch {
            try {
                val profile = repository.getProfile() ?: UserProfile()
                layoutFullName.editText?.setText(profile.nombreCompleto)
                layoutStudentId.editText?.setText(profile.carnet)
                layoutCareer.editText?.setText(profile.carrera)
                if (spinnerCurrency.adapter == null) {
                    spinnerCurrency.adapter = ArrayAdapter(
                        this@ProfileActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        currencies
                    )
                }
                val index = currencies.indexOf(profile.moneda).let { if (it >= 0) it else 0 }
                spinnerCurrency.setSelection(index)
            } catch (e: Exception) {
                Snackbar.make(layoutFullName, repository.mapAuthError(e), Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
