package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyContactStoreTest {
    private fun store(values: MutableMap<String, String>) = EmergencyContactStore(
        read = values::get,
        write = { name, number ->
            if (name == null || number == null) {
                values.clear()
            } else {
                values["emergency_contact_name"] = name
                values["emergency_contact_number"] = number
            }
        },
    )

    @Test
    fun `emergency contact storage saves and clears both fields`() {
        val values = mutableMapOf<String, String>()
        val store = store(values)
        val contact = EmergencyContact("Alex", "+1 555 0100")

        store.save(contact)
        assertEquals(contact, store.load())

        store.save(null)
        assertEquals(null, store.load())
        assertEquals(emptyMap<String, String>(), values)
    }

    @Test
    fun `blank name or number clears the contact`() {
        for (blank in listOf(EmergencyContact("", "555"), EmergencyContact("Alex", "  "), EmergencyContact(" ", ""))) {
            val values = mutableMapOf<String, String>()
            val store = store(values)
            store.save(EmergencyContact("Sam", "100"))
            store.save(blank)
            assertEquals(null, store.load())
            assertEquals(emptyMap<String, String>(), values)
        }
    }
}
