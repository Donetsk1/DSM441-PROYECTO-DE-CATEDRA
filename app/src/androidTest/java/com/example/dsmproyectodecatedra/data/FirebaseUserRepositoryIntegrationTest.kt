package com.example.dsmproyectodecatedra.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirebaseUserRepositoryIntegrationTest {
    @Test
    fun profileAndFinancesShareRealtimeDatabaseAndAccountDeletionCleansUp() = runBlocking {
        val email = "firebase.audit.${UUID.randomUUID()}@example.com"
        val password = "Audit-${UUID.randomUUID()}-1a"
        val otherEmail = "firebase.audit.${UUID.randomUUID()}@example.com"
        val otherPassword = "Audit-${UUID.randomUUID()}-2b"
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repository = UserRepository()
        try {
            repository.register(email, password, "Prueba temporal", "TEST001", "Prueba", "USD")
            val uid = requireNotNull(repository.currentUser).uid
            val initial = requireNotNull(repository.getProfile())
            assertEquals("Prueba temporal", initial.nombreCompleto)
            assertEquals(email, initial.correo)

            val finance = FinanceRepository()
            val movementId = finance.saveMovement(MovementDraft(amount = "8.50", description = "Prueba temporal"), "USD")
            val budget = CategoryBudget("integration-test", "Alimentación", FinanceFormat.month(), 1000, "USD", System.currentTimeMillis())
            finance.saveBudget(budget)
            repository.updateProfile("Perfil actualizado", "TEST002", "Nueva carrera", "GTQ")
            val updated = requireNotNull(repository.getProfile())
            assertEquals("Perfil actualizado", updated.nombreCompleto)
            assertEquals("GTQ", updated.moneda)
            val snapshot = withTimeout(20_000) { finance.observe().first { it.currency == "GTQ" } }
            assertEquals(850L, snapshot.movements.single { it.id == movementId }.amountCents)
            assertEquals(1000L, snapshot.budgets.single().limitCents)

            val independentApp = com.google.firebase.FirebaseApp.initializeApp(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                com.google.firebase.FirebaseApp.getInstance().options,
                "independent-${UUID.randomUUID()}"
            )
            try {
                val independentAuth = FirebaseAuth.getInstance(independentApp)
                independentAuth.signInWithEmailAndPassword(email, password).await()
                val remoteSnapshot = FirebaseDatabase.getInstance(independentApp).getReference("users").child(uid).get().await()
                assertEquals(850L, remoteSnapshot.child("movements").child(movementId).child("amountCents").getValue(Long::class.java))
                assertEquals("GTQ", remoteSnapshot.child("moneda").getValue(String::class.java))
                independentAuth.signOut()
            } finally {
                independentApp.delete()
            }

            repository.logout()
            repository.login(email, password)
            assertEquals(uid, requireNotNull(repository.currentUser).uid)
            assertEquals("GTQ", requireNotNull(repository.getProfile()).moneda)

            repository.logout()
            repository.register(otherEmail, otherPassword, "Otra cuenta temporal", "TEST003", "Prueba", "USD")
            val otherUid = requireNotNull(repository.currentUser).uid
            assertTrue(uid != otherUid)
            val otherFinance = FinanceRepository()
            val emptyHistory = withTimeout(20_000) { otherFinance.observe().first() }
            assertTrue(emptyHistory.movements.isEmpty())
            assertTrue(emptyHistory.budgets.isEmpty())
            val otherMovement = otherFinance.saveMovement(MovementDraft(type = "income", amount = "20.00", category = "Beca"), "USD")
            val ownHistory = withTimeout(20_000) { otherFinance.observe().first { it.movements.isNotEmpty() } }
            assertEquals(listOf(otherMovement), ownHistory.movements.map { it.id })
            try {
                FirebaseDatabase.getInstance().getReference("users").child(uid).get().await()
                fail("Una cuenta no debe leer los datos de otra")
            } catch (e: Exception) {
                assertTrue(generateSequence(e as Throwable) { it.cause }.any {
                    it.message.orEmpty().contains("permission", true)
                })
            }
            repository.deleteAccount(otherPassword)
            assertNull(repository.currentUser)
            repository.login(email, password)
            assertEquals(listOf(movementId), withTimeout(20_000) { FinanceRepository().observe().first() }.movements.map { it.id })

            val reference = FirebaseDatabase.getInstance().getReference("users").child(uid)
            val deletionObserved = com.google.android.gms.tasks.TaskCompletionSource<Boolean>()
            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!snapshot.exists()) deletionObserved.trySetResult(true)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) = Unit
            }
            reference.addValueEventListener(listener)
            try {
                repository.deleteAccount(password)
                assertEquals(true, withTimeout(10_000) { deletionObserved.task.await() })
                assertNull(repository.currentUser)
            } finally {
                reference.removeEventListener(listener)
            }
        } finally {
            for ((testEmail, testPassword) in listOf(email to password, otherEmail to otherPassword)) {
                try {
                    auth.signInWithEmailAndPassword(testEmail, testPassword).await()
                } catch (_: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                    continue
                } catch (_: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    continue
                }
                val remaining = auth.currentUser
                if (remaining?.email == testEmail) {
                    FirebaseDatabase.getInstance().getReference("users").child(remaining.uid).removeValue().await()
                    remaining.delete().await()
                }
            }
            auth.signOut()
        }
    }
}
