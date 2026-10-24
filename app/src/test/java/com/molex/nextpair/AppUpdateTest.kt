package com.molex.nextpair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test fun versionsCompareAsNumbers() {
        assertTrue(AppUpdate.isNewer("3.8", "3.7"))
        assertTrue(AppUpdate.isNewer("3.10", "3.9"))
        assertTrue(AppUpdate.isNewer("v4.0", "3.12"))
        assertTrue(AppUpdate.isNewer("3.7.1", "3.7"))
        assertFalse(AppUpdate.isNewer("3.7", "3.7"))
        assertFalse(AppUpdate.isNewer("3.6", "3.7"))
    }

    @Test fun parsesGithubRelease() {
        val json = """{"tag_name":"v3.8","body":"[важно]\n- Починил вход в IIS","assets":[
            {"name":"notes.txt","browser_download_url":"x","size":1},
            {"name":"UniTime-3.8.apk","browser_download_url":"https://github.com/molex1le/UniTime/releases/download/v3.8/UniTime-3.8.apk","size":6835523}]}"""
        val r = AppUpdate.parse(json)!!
        assertEquals("3.8", r.version)
        assertTrue(r.mandatory)
        assertEquals("- Починил вход в IIS", r.notes)
        assertTrue(r.apkUrl.endsWith("UniTime-3.8.apk"))
        assertEquals(6835523L, r.size)
    }

    @Test fun ordinaryReleaseIsNotMandatory() {
        val r = AppUpdate.parse("""{"tag_name":"v3.9","body":"Мелочи","assets":[{"name":"UniTime-3.9.apk","browser_download_url":"u","size":5}]}""")!!
        assertFalse(r.mandatory)
    }
}
