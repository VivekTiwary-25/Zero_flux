package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `defaultLanguageFor returns matching language for exact 2-letter codes`() {
        assertEquals(AppLanguage.EN, defaultLanguageFor("en"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("hi"))
        assertEquals(AppLanguage.KN, defaultLanguageFor("kn"))
        assertEquals(AppLanguage.TA, defaultLanguageFor("ta"))
        assertEquals(AppLanguage.TE, defaultLanguageFor("te"))
        assertEquals(AppLanguage.ML, defaultLanguageFor("ml"))
        assertEquals(AppLanguage.MR, defaultLanguageFor("mr"))
        assertEquals(AppLanguage.GU, defaultLanguageFor("gu"))
        assertEquals(AppLanguage.BN, defaultLanguageFor("bn"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("or"))
    }

    @Test
    fun `defaultLanguageFor handles Odia variations`() {
        assertEquals(AppLanguage.OR, defaultLanguageFor("or"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("ory"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("ori"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("or-IN"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("ory-IN"))
        assertEquals(AppLanguage.OR, defaultLanguageFor("ory_IN"))
    }

    @Test
    fun `defaultLanguageFor handles Hindi with region tags`() {
        assertEquals(AppLanguage.HI, defaultLanguageFor("hi"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("hi-IN"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("hi_IN"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("hi-Deva-IN"))
    }

    @Test
    fun `defaultLanguageFor handles other languages with region tags`() {
        assertEquals(AppLanguage.EN, defaultLanguageFor("en-US"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("en-GB"))
        assertEquals(AppLanguage.KN, defaultLanguageFor("kn-IN"))
        assertEquals(AppLanguage.TA, defaultLanguageFor("ta-IN"))
        assertEquals(AppLanguage.TA, defaultLanguageFor("ta-LK"))
        assertEquals(AppLanguage.TE, defaultLanguageFor("te-IN"))
        assertEquals(AppLanguage.ML, defaultLanguageFor("ml-IN"))
        assertEquals(AppLanguage.MR, defaultLanguageFor("mr-IN"))
        assertEquals(AppLanguage.GU, defaultLanguageFor("gu-IN"))
        assertEquals(AppLanguage.BN, defaultLanguageFor("bn-BD"))
        assertEquals(AppLanguage.BN, defaultLanguageFor("bn-IN"))
    }

    @Test
    fun `defaultLanguageFor is case-insensitive and trims whitespace`() {
        assertEquals(AppLanguage.HI, defaultLanguageFor("HI"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("HI-IN"))
        assertEquals(AppLanguage.HI, defaultLanguageFor("  hi-IN  "))
        assertEquals(AppLanguage.OR, defaultLanguageFor("ORY"))
        assertEquals(AppLanguage.OR, defaultLanguageFor(" Ory-IN "))
        assertEquals(AppLanguage.EN, defaultLanguageFor("EN"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("\ten-US\n"))
    }

    @Test
    fun `defaultLanguageFor falls back to English for unknown tags or empty inputs`() {
        assertEquals(AppLanguage.EN, defaultLanguageFor("fr"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("fr-FR"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("es"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("de"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("zh-CN"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("ja"))
        assertEquals(AppLanguage.EN, defaultLanguageFor(""))
        assertEquals(AppLanguage.EN, defaultLanguageFor("   "))
        assertEquals(AppLanguage.EN, defaultLanguageFor("-"))
        assertEquals(AppLanguage.EN, defaultLanguageFor("unknown-XYZ"))
    }
}
