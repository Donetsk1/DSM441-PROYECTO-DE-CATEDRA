package com.example.dsmproyectodecatedra.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

private const val NETWORK_TIMEOUT_MS = 15_000L

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private var pendingRegistrationUid: String? = null
    val currentUser get() = auth.currentUser

    suspend fun register(
        correo: String,
        password: String,
        nombreCompleto: String,
        carnet: String,
        carrera: String,
        moneda: String
    ) = withTimeout(NETWORK_TIMEOUT_MS) {
        validateProfile(nombreCompleto, carnet, carrera, moneda)
        val pendingUser = auth.currentUser?.takeIf { it.uid == pendingRegistrationUid }
        if (pendingUser != null && !pendingUser.email.orEmpty().equals(correo.trim(), true)) {
            throw IllegalStateException("La cuenta anterior ya fue creada. Guarda su perfil o inicia sesión antes de registrar otra.")
        }
        val user = pendingUser ?: auth.createUserWithEmailAndPassword(correo.trim(), password).await().user
            ?: throw IllegalStateException("No se pudo crear el usuario")
        pendingRegistrationUid = user.uid
        val profile = UserProfile(user.uid, nombreCompleto, user.email ?: correo, carnet, carrera, moneda)
        try {
            database.getReference("users").child(user.uid).updateChildren(profile.databaseValues()).await()
            pendingRegistrationUid = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("La cuenta ya fue creada, pero falta guardar el perfil. Reintenta con el mismo correo o complétalo en Mi perfil.", e)
        }
        Unit
    }

    suspend fun login(correo: String, password: String) = withTimeout(NETWORK_TIMEOUT_MS) {
        auth.signInWithEmailAndPassword(correo, password).await()
        Unit
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun getProfile(): UserProfile? = withTimeout(NETWORK_TIMEOUT_MS) {
        val user = auth.currentUser ?: return@withTimeout null
        val snapshot = database.getReference("users").child(user.uid).get().await()
        UserProfile.fromDatabase(user.uid, user.email.orEmpty(), (snapshot.value as? Map<*, *>).orEmpty())
    }

    suspend fun updateProfile(nombreCompleto: String, carnet: String, carrera: String, moneda: String) =
        withTimeout(NETWORK_TIMEOUT_MS) {
            validateProfile(nombreCompleto, carnet, carrera, moneda)
            val user = auth.currentUser ?: throw IllegalStateException("No hay sesión activa")
            val profile = UserProfile(user.uid, nombreCompleto, user.email.orEmpty(), carnet, carrera, moneda)
            database.getReference("users").child(user.uid).updateChildren(profile.databaseValues()).await()
            Unit
        }

    suspend fun changePassword(currentPassword: String, newPassword: String) = withTimeout(NETWORK_TIMEOUT_MS) {
        val user = auth.currentUser ?: throw IllegalStateException("No hay sesión activa")
        val email = user.email ?: throw IllegalStateException("El usuario no tiene correo asociado")
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
        Unit
    }

    suspend fun sendPasswordReset(correo: String) = withTimeout(NETWORK_TIMEOUT_MS) {
        auth.sendPasswordResetEmail(correo).await()
        Unit
    }

    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("No hay sesión activa")
        val email = user.email ?: throw IllegalStateException("El usuario no tiene correo asociado")
        val credential = EmailAuthProvider.getCredential(email, password)
        val reference = database.getReference("users").child(user.uid)
        val backup = withTimeout(NETWORK_TIMEOUT_MS) {
            user.reauthenticate(credential).await()
            reference.get().await().value
        }
        withContext(NonCancellable) {
            try {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    reference.removeValue().await()
                    user.delete().await()
                }
            } catch (e: Exception) {
                try {
                    if (backup != null) withTimeout(NETWORK_TIMEOUT_MS) { restoreDeletedData(reference, backup) }
                } catch (restoreError: Exception) {
                    throw IllegalStateException("No se pudo confirmar la eliminación de la cuenta ni restaurar sus datos. Revisa la conexión antes de reintentar.", restoreError)
                }
                throw e
            }
        }
    }

    private suspend fun restoreDeletedData(reference: DatabaseReference, backup: Any) {
        val result = TaskCompletionSource<Unit>()
        reference.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value == null) currentData.value = backup
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) result.setException(error.toException())
                else if (!committed) result.setException(IllegalStateException("No se pudieron restaurar los datos"))
                else result.setResult(Unit)
            }
        })
        result.task.await()
    }

    private fun validateProfile(name: String, studentId: String, career: String, currency: String) {
        require(name.isNotBlank() && studentId.isNotBlank() && career.isNotBlank()) { "Completa todos los campos del perfil." }
        require(currency in UserProfile.supportedCurrencies) { "Selecciona una moneda válida." }
    }

    fun mapAuthError(e: Exception): String = when (e) {
        is TimeoutCancellationException ->
            if (pendingRegistrationUid != null) "La cuenta fue creada, pero no se confirmó el perfil. Reintenta con el mismo correo o complétalo en Mi perfil."
            else "La operación tardó demasiado. Verifica tu conexión e inténtalo de nuevo."
        is FirebaseNetworkException ->
            "No se pudo conectar con Firebase. Revisa tu conexión e inténtalo de nuevo."
        is DatabaseException ->
            if (e.message.orEmpty().contains("permission", true)) "No tienes permiso para acceder a tus datos. Revisa las reglas de Realtime Database."
            else "No se pudo acceder a Realtime Database. Revisa tu conexión e inténtalo de nuevo."
        is FirebaseAuthWeakPasswordException ->
            "La contraseña es demasiado débil. Usa al menos 6 caracteres."
        is FirebaseAuthInvalidCredentialsException ->
            "Correo o contraseña inválidos."
        is FirebaseAuthUserCollisionException ->
            "Ya existe una cuenta registrada con ese correo."
        is FirebaseAuthInvalidUserException ->
            "No existe una cuenta con ese correo."
        else -> if (generateSequence(e as Throwable) { it.cause }.any { it.message.orEmpty().contains("permission denied", true) })
            "No tienes permiso para acceder a tus datos. Revisa las reglas de Realtime Database."
        else e.localizedMessage ?: "Ocurrió un error inesperado. Inténtalo de nuevo."
    }
}
