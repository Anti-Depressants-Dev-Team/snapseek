package dev.snapseek.android

import androidx.test.core.app.ApplicationProvider
import dev.snapseek.android.platform.CrashLog
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The report is the only thing a phone can hand back when it dies, so it had better be written and readable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {

    @Test
    fun `a crash is written down, shown once and cleared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        CrashLog.clear(context)
        assertNull(CrashLog.last(context))

        CrashLog.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("something went wrong"))

        val report = CrashLog.last(context)
        assertTrue("nothing was written", report != null)
        assertTrue("the report has no exception in it", report!!.contains("IllegalStateException"))
        assertTrue("the report has no message in it", report.contains("something went wrong"))
        assertTrue("the report does not say which android", report.contains("android:"))

        CrashLog.clear(context)
        assertNull(CrashLog.last(context))
    }
}
