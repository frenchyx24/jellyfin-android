package org.jellyfin.mobile

import org.jellyfin.mobile.nflix.data.NflixApi
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cold start: JellyfinApplication (Koin graph), the Nflix HTTP client, then MainActivity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchSmokeTest {
    /** Robolectric has no ANDROID_ID; the Jellyfin SDK device info needs one. */
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "0123456789abcdef")
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun nflixApiBuilds() {
        assertNotNull(GlobalContext.get().get<NflixApi>().client)
    }

    @Test
    fun mainActivityStarts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
