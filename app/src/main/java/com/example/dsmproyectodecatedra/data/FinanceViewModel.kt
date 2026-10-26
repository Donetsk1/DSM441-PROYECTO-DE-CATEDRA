package com.example.dsmproyectodecatedra.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.database.DatabaseException

data class FinanceState(
    val data: FinanceSnapshot = FinanceSnapshot(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val detailId: String? = null,
    val message: String? = null
)

class FinanceViewModel : ViewModel() {
    private val repository = FinanceRepository()
    private val mutableState = MutableStateFlow(FinanceState())
    val state = mutableState.asStateFlow()
    var draft = MovementDraft()
    var selectedMonth = FinanceFormat.month()
    var showAlerts = false
    private var observation: kotlinx.coroutines.Job? = null

    init { retry() }

    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = viewModelScope.launch {
            val initialLoad = launch {
                delay(15_000)
                if (mutableState.value.loading) mutableState.update {
                    it.copy(loading = false, error = "No se pudieron cargar tus datos. Revisa tu conexión e inténtalo de nuevo.")
                }
            }
            try {
                repository.observe().collect { data ->
                    initialLoad.cancel()
                    mutableState.update { it.copy(data = data, loading = false, error = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = errorMessage(e)) }
            } finally {
                initialLoad.cancel()
            }
        }
    }

    fun newMovement() {
        draft = MovementDraft()
        mutableState.update { it.copy(detailId = null) }
    }

    fun showMovement(id: String) { mutableState.update { it.copy(detailId = id) } }

    fun editMovement(movement: FinancialMovement) {
        draft = MovementDraft(movement.id, movement.type, FinanceFormat.amount(movement.amountCents),
            movement.category, movement.date, movement.paymentMethod, movement.description,
            movement.tags, movement.currency, movement.createdAt)
        mutableState.update { it.copy(detailId = null) }
    }

    fun saveMovement() = perform {
        if (draft.id == null) draft.id = java.util.UUID.randomUUID().toString()
        if (draft.createdAt == null) draft.createdAt = System.currentTimeMillis()
        val id = repository.saveMovement(draft.copy(), mutableState.value.data.currency)
        mutableState.update { it.copy(detailId = id) }
    }

    fun deleteMovement(id: String) = perform {
        repository.deleteMovement(id)
        newMovement()
        mutableState.update { it.copy(message = "Movimiento eliminado") }
    }

    fun saveBudget(budget: CategoryBudget) = perform {
        repository.saveBudget(budget)
        mutableState.update { it.copy(message = "Presupuesto guardado") }
    }

    fun deleteBudget(id: String) = perform {
        repository.deleteBudget(id)
        mutableState.update { it.copy(message = "Presupuesto eliminado") }
    }

    fun clearNotice() { mutableState.update { it.copy(message = null, error = null) } }

    private fun perform(operation: suspend () -> Unit) {
        if (mutableState.value.busy || mutableState.value.loading) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
            } catch (e: TimeoutCancellationException) {
                mutableState.update { it.copy(error = "No se pudo confirmar el guardado. Revisa tu conexión y vuelve a cargar los datos antes de reintentar.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = errorMessage(e)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun errorMessage(e: Exception): String =
        if (e is DatabaseException && e.message.orEmpty().contains("permission", true))
            "No tienes permiso para acceder a estos datos. Revisa las reglas de Realtime Database."
        else "No se pudo completar la operación. Revisa tu conexión y vuelve a intentarlo."
}
