package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AedExplanationStringsTest {

    private val languages = listOf("en", "hi", "kn", "ta", "te", "ml", "mr", "gu", "bn", "or")

    @Test
    fun `first guide AED mention has defibrillator and later uses stay AED`() {
        for (code in languages) {
            val file = File(TestAssets.assetsDir, "strings/$code.json")
            val json = AssetJson.readObject(file)

            val step2Title = AssetJson.asString(json["cprStep2Title"], "cprStep2Title in $code")
            assertTrue(
                "cprStep2Title in $code must contain 'AED (defibrillator)': was $step2Title",
                step2Title.contains("AED (defibrillator)"),
            )

            // Later uses stay "AED" without defibrillator
            val laterUses = listOf("cprStep2Body", "cprStep2Visual", "cprStep5Title", "cprStep5Body", "cprStep5Tip")
            for (key in laterUses) {
                val value = AssetJson.asString(json[key], "$key in $code")
                assertTrue("$key in $code must contain AED", value.contains("AED"))
                assertFalse(
                    "$key in $code must not contain defibrillator: was $value",
                    value.contains("defibrillator", ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `emergency reminder text has heart-shock box and keeps AED in Latin`() {
        val enJson = AssetJson.readObject(File(TestAssets.assetsDir, "strings/en.json"))
        assertEquals(
            "Ask someone to bring an AED (heart-shock box)",
            AssetJson.asString(enJson["em_reminder_aed"], "em_reminder_aed in en"),
        )

        for (code in languages) {
            val file = File(TestAssets.assetsDir, "strings/$code.json")
            val json = AssetJson.readObject(file)
            val value = AssetJson.asString(json["em_reminder_aed"], "em_reminder_aed in $code")
            assertTrue("em_reminder_aed in $code must contain Latin AED: was $value", value.contains("AED"))
            assertTrue("em_reminder_aed in $code must not be empty", value.isNotBlank())
        }
    }

    @Test
    fun `popup explanation matches exact wording in English and is present in all languages`() {
        val expectedEnglish = "An AED is a portable box that can restart the heart with an electric shock. " +
            "You find them in malls, airports, metro stations and offices. " +
            "Switch it on and it tells you exactly what to do."

        val enJson = AssetJson.readObject(File(TestAssets.assetsDir, "strings/en.json"))
        assertEquals(
            expectedEnglish,
            AssetJson.asString(enJson["aed_explanation"], "aed_explanation in en"),
        )
        assertEquals(
            "Close",
            AssetJson.asString(enJson["aed_close"], "aed_close in en"),
        )

        for (code in languages) {
            val file = File(TestAssets.assetsDir, "strings/$code.json")
            val json = AssetJson.readObject(file)

            val explanation = AssetJson.asString(json["aed_explanation"], "aed_explanation in $code")
            assertTrue("aed_explanation in $code must contain Latin AED: was $explanation", explanation.contains("AED"))
            assertTrue("aed_explanation in $code must be non-blank", explanation.isNotBlank())

            val close = AssetJson.asString(json["aed_close"], "aed_close in $code")
            assertTrue("aed_close in $code must be non-blank", close.isNotBlank())
        }
    }
}
