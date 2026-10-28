package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.FreshRssApiService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FreshFeed", appName)
    }

    @Test
    fun `verify api base normalization`() {
        val api = FreshRssApiService()
        assertEquals(
            "https://freshrss.example.com/api/greader.php",
            api.normalizeApiBase("https://freshrss.example.com")
        )
        assertEquals(
            "https://freshrss.example.com/api/greader.php",
            api.normalizeApiBase("https://freshrss.example.com/api/greader.php")
        )
        assertEquals(
            "https://freshrss.example.com:8080/api/greader.php",
            api.normalizeApiBase("freshrss.example.com:8080")
        )
    }
}
