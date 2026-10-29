package com.example.dsmproyectodecatedra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserProfileTest {
    @Test
    fun profileUpdatesContainOnlyProfileFields() {
        val profile = UserProfile("uid", " Nombre ", " usuario@example.com ", " AB123456 ", " Carrera ", "GTQ")
        val updates = profile.databaseValues()
        assertEquals(setOf("uid", "nombreCompleto", "correo", "carnet", "carrera", "moneda"), updates.keys)
        assertEquals("Nombre", updates["nombreCompleto"])
        assertEquals("usuario@example.com", updates["correo"])
        assertFalse(updates.containsKey("movements"))
        assertFalse(updates.containsKey("budgets"))
    }

    @Test
    fun existingAuthAccountWithoutProfileCanBeRead() {
        val profile = UserProfile.fromDatabase("uid", "usuario@example.com", mapOf("movements" to mapOf("m" to 850L)))
        assertEquals("uid", profile.uid)
        assertEquals("usuario@example.com", profile.correo)
        assertEquals("", profile.nombreCompleto)
        assertEquals("USD", profile.moneda)
    }

    @Test
    fun authenticatedIdentityTakesPrecedenceOverStoredIdentity() {
        val profile = UserProfile.fromDatabase("uid", "actual@example.com", mapOf("uid" to "otro", "correo" to "viejo@example.com"))
        assertEquals("uid", profile.uid)
        assertEquals("actual@example.com", profile.correo)
    }

    @Test
    fun supportedCurrencyIsSharedWithFinance() {
        UserProfile.supportedCurrencies.forEach { currency ->
            assertEquals(currency, UserProfile.fromDatabase("uid", "", mapOf("moneda" to currency)).moneda)
        }
        assertEquals("USD", UserProfile.fromDatabase("uid", "", mapOf("moneda" to "inválida")).moneda)
    }
}
