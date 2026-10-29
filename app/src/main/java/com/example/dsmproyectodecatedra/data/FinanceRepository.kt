package com.example.dsmproyectodecatedra.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class FinanceRepository {
    private val user = FirebaseDatabase.getInstance().getReference("users")
        .child(requireNotNull(FirebaseAuth.getInstance().currentUser?.uid))

    fun observe() = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }

            override fun onDataChange(snapshot: DataSnapshot) {
                val data = (snapshot.value as? Map<*, *>).orEmpty()
                val movements = (data["movements"] as? Map<*, *>).orEmpty().mapNotNull { (id, value) ->
                    val entry = value as? Map<*, *> ?: return@mapNotNull null
                    val amount = (entry["amountCents"] as? Number)?.toLong() ?: return@mapNotNull null
                    if (amount <= 0) return@mapNotNull null
                    FinancialMovement(id.toString(), entry["type"] as? String ?: "expense", amount,
                        entry["category"] as? String ?: "Otros", entry["date"] as? String ?: FinanceFormat.today(),
                        entry["paymentMethod"] as? String ?: "", entry["description"] as? String ?: "",
                        (entry["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        entry["currency"] as? String ?: "USD", (entry["createdAt"] as? Number)?.toLong() ?: 0)
                }
                val budgets = (data["budgets"] as? Map<*, *>).orEmpty().mapNotNull { (id, value) ->
                    val entry = value as? Map<*, *> ?: return@mapNotNull null
                    val limit = (entry["limitCents"] as? Number)?.toLong() ?: return@mapNotNull null
                    if (limit <= 0) return@mapNotNull null
                    CategoryBudget(id.toString(), entry["category"] as? String ?: "Otros",
                        entry["month"] as? String ?: "", limit, entry["currency"] as? String ?: "USD",
                        (entry["createdAt"] as? Number)?.toLong() ?: 0)
                }
                trySend(FinanceSnapshot(movements.sortedByDescending { it.createdAt }, budgets,
                    (data["moneda"] as? String)?.takeIf { it in UserProfile.supportedCurrencies } ?: "USD"))
            }
        }
        user.addValueEventListener(listener)
        awaitClose { user.removeEventListener(listener) }
    }

    suspend fun saveMovement(draft: MovementDraft, currency: String): String = withTimeout(15_000) {
        val id = draft.id ?: UUID.randomUUID().toString()
        val amount = requireNotNull(FinanceFormat.cents(draft.amount))
        val value = mapOf(
            "type" to draft.type, "amountCents" to amount, "category" to draft.category,
            "date" to draft.date, "paymentMethod" to draft.paymentMethod,
            "description" to draft.description.trim(), "tags" to draft.tags,
            "currency" to (draft.currency ?: currency),
            "createdAt" to (draft.createdAt ?: System.currentTimeMillis())
        )
        user.child("movements").child(id).setValue(value).await()
        id
    }

    suspend fun deleteMovement(id: String) = withTimeout(15_000) {
        user.child("movements").child(id).removeValue().await()
        Unit
    }

    suspend fun saveBudget(budget: CategoryBudget) = withTimeout(15_000) {
        val value = mapOf("category" to budget.category, "month" to budget.month,
            "limitCents" to budget.limitCents, "currency" to budget.currency, "createdAt" to budget.createdAt)
        user.child("budgets").child(budget.id).setValue(value).await()
        Unit
    }

    suspend fun deleteBudget(id: String) = withTimeout(15_000) {
        user.child("budgets").child(id).removeValue().await()
        Unit
    }
}
