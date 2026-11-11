package com.family.taskmanager

import com.family.taskmanager.data.repository.deliverCommittedSystemNotification
import com.family.taskmanager.data.repository.postLocalNotificationSafely
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NotificationDeliveryTest {
    @Test
    fun successfulDelivery_runsExactlyOnce() {
        var attempts = 0
        val result = postLocalNotificationSafely { attempts++ }
        assertTrue(result.isSuccess)
        assertEquals(1, attempts)
    }

    @Test
    fun permissionFailure_isReportedWithoutThrowing() {
        val denied = SecurityException("Notification permission revoked")
        val result = postLocalNotificationSafely { throw denied }
        assertSame(denied, result.exceptionOrNull())
    }

    @Test
    fun cancellation_isNeverSwallowed() {
        val cancelled = CancellationException("Screen closed")
        try {
            postLocalNotificationSafely { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun unexpectedFailure_isNeverSwallowed() {
        val defect = IllegalArgumentException("Invalid notification configuration")
        try {
            postLocalNotificationSafely { throw defect }
            fail("Unexpected errors must propagate")
        } catch (actual: IllegalArgumentException) {
            assertSame(defect, actual)
        }
    }

    @Test
    fun committedDelivery_separatesExpectedAndUnexpectedFailures_andRethrowsCancellation() {
        val expectedError = SecurityException("POST_NOTIFICATIONS denied")
        var recordedExpected: Throwable? = null
        var recordedUnexpected: Throwable? = null

        deliverCommittedSystemNotification(
            post = { throw expectedError },
            onExpectedFailure = { recordedExpected = it },
            onUnexpectedFailure = { recordedUnexpected = it }
        )
        assertSame(expectedError, recordedExpected)
        assertNull(recordedUnexpected)

        val unexpectedError = IllegalStateException("Unexpected channel failure after DB commit")
        recordedExpected = null
        deliverCommittedSystemNotification(
            post = { throw unexpectedError },
            onExpectedFailure = { recordedExpected = it },
            onUnexpectedFailure = { recordedUnexpected = it }
        )
        assertNull(recordedExpected)
        assertSame(unexpectedError, recordedUnexpected)

        val cancelled = CancellationException("Coroutine cancelled")
        try {
            deliverCommittedSystemNotification(
                post = { throw cancelled },
                onExpectedFailure = { fail("Should not report cancellation as expected") },
                onUnexpectedFailure = { fail("Should not swallow cancellation as unexpected") }
            )
            fail("CancellationException must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
