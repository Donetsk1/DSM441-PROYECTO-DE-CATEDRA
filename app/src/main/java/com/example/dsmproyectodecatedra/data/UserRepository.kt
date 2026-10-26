package com.example.dsmproyectodecatedra.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val USERS_COLLECTION = "users"
private const val NETWORK_TIMEOUT_MS = 15_000L

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser get() = auth.currentUser

    suspend fun register(
        correo: String,
        password: String,
        nombreCompleto: String,
        carnet: String,
        carrera: String,
        moneda: String
    ) = withTimeout(NETWORK_TIMEOUT_MS) {
        val result = auth.createUserWithEmailAndPassword(correo, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("No se pudo crear el usuario")
        val profile = UserProfile(uid, nombreCompleto, correo, carnet, carrera, moneda)
        firestore.collection(USERS_COLLECTION).document(uid).set(profile).await()
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
        val uid = auth.currentUser?.uid ?: return@withTimeout null
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        snapshot.toObject(UserProfile::class.java)
    }

    suspend fun updateProfile(nombreCompleto: String, carnet: String, carrera: String, moneda: String) =
        withTimeout(NETWORK_TIMEOUT_MS) {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("No hay sesión activa")
            val updates = mapOf(
                "nombreCompleto" to nombreCompleto,
                "carnet" to carnet,
                "carrera" to carrera,
                "moneda" to moneda
            )
            firestore.collection(USERS_COLLECTION).document(uid).update(updates).await()
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

    suspend fun deleteAccount(password: String) = withTimeout(NETWORK_TIMEOUT_MS) {
        val user = auth.currentUser ?: throw IllegalStateException("No hay sesión activa")
        val email = user.email ?: throw IllegalStateException("El usuario no tiene correo asociado")
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
        firestore.collection(USERS_COLLECTION).document(user.uid).delete().await()
        user.delete().await()
        Unit
    }

    fun mapAuthError(e: Exception): String = when (e) {
        is TimeoutCancellationException ->
            "La operación tardó demasiado. Verifica tu conexión e inténtalo de nuevo."
        is FirebaseAuthWeakPasswordException ->
            "La contraseña es demasiado débil. Usa al menos 6 caracteres."
        is FirebaseAuthInvalidCredentialsException ->
            "Correo o contraseña inválidos."
        is FirebaseAuthUserCollisionException ->
            "Ya existe una cuenta registrada con ese correo."
        is FirebaseAuthInvalidUserException ->
            "No existe una cuenta con ese correo."
        else -> e.localizedMessage ?: "Ocurrió un error inesperado. Inténtalo de nuevo."
    }
}
