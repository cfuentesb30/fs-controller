package com.fscontroller.app.service

import com.fscontroller.app.model.AppButton
import com.fscontroller.app.service.ButtonDetector.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lines captured on the Fire TV Stick 4K Max (Fire OS 8.1.8.5) on 2026-10-22. */
class ButtonDetectorTest {

    private val detector = ButtonDetector()

    /** As delivered by `logcat -v brief -v uid` for a line written by system_server (uid 1000). */
    private fun brief(message: String) = "I/ActivityTaskManager( 1000:  683): $message"

    /** The same message written by another app or the shell: forgeable text, untrusted writer. */
    private fun forged(message: String, writer: String = "shell: 5107") = "I/ActivityTaskManager($writer): $message"

    private fun feed(message: String, now: Long) = detector.onLine(brief(message), now)

    @Test
    fun netflixButton_redirectsImmediately() {
        val d = feed(NETFLIX_BUTTON, 0)
        assertEquals(Detection(AppButton.NETFLIX, "com.netflix.ninja"), d)
        assertNull(detector.deadlineMs)
    }

    @Test
    fun lineWithoutTrustedHeader_isIgnored() {
        assertNull(detector.onLine(NETFLIX_BUTTON, 0))
    }

    @Test
    fun forgedInitiatingLine_fromOtherApp_isIgnored() {
        assertNull(detector.onLine(forged(NETFLIX_BUTTON), 0))
        assertNull(detector.onLine(forged(NETFLIX_BUTTON, "10123:  4242"), 0))
        assertNull(detector.onLine(forged(PEACOCK_TRAMPOLINE, "u0_a97:  4242"), 0))
        assertNull(detector.deadlineMs)
    }

    @Test
    fun forgedFollowUp_duringTrampoline_doesNotResolveIt() {
        assertNull(feed(DISNEY_TRAMPOLINE, 0))
        assertNull(detector.onLine(forged(DISNEY_FOLLOW_UP), 10))
        assertEquals(1_500L, detector.deadlineMs)
        assertEquals(Detection(AppButton.DISNEY_PLUS, "com.disney.disneyplus"), feed(DISNEY_FOLLOW_UP, 20))
    }

    @Test
    fun secondPressWithinCooldown_isIgnored_thenAllowedAfter() {
        assertEquals(AppButton.NETFLIX, feed(NETFLIX_BUTTON, 0)?.button)
        assertNull(feed(NETFLIX_BUTTON, 999))
        assertEquals(AppButton.NETFLIX, feed(NETFLIX_BUTTON, 1_000)?.button)
    }

    @Test
    fun primeButton_redirectsOnFollowUp() {
        assertNull(feed(PRIME_TRAMPOLINE, 1_000))
        assertEquals(2_500L, detector.deadlineMs)
        val d = feed(PRIME_FOLLOW_UP, 1_035)
        assertEquals(Detection(AppButton.PRIME_VIDEO, "com.amazon.firebat"), d)
        assertNull(detector.deadlineMs)
        assertNull(detector.onTimeout(2_500))
    }

    @Test
    fun disneyButton_redirectsOnFollowUp() {
        assertNull(feed(DISNEY_TRAMPOLINE, 0))
        assertEquals(Detection(AppButton.DISNEY_PLUS, "com.disney.disneyplus"), feed(DISNEY_FOLLOW_UP, 20))
    }

    @Test
    fun peacockButton_notInstalled_redirectsOnStorePageFromAppstoreUid() {
        assertNull(feed(PEACOCK_TRAMPOLINE, 0))
        assertNull(detector.onTimeout(400)) // not due yet
        assertEquals(Detection(AppButton.PEACOCK, "com.amazon.venezia"), feed(PEACOCK_STORE_PAGE, 600))
    }

    @Test
    fun peacockButton_noFollowUp_redirectsAfterTimeout() {
        assertNull(feed(PEACOCK_TRAMPOLINE, 10_000))
        assertEquals(11_500L, detector.deadlineMs)
        assertNull(detector.onTimeout(11_499))
        assertEquals(Detection(AppButton.PEACOCK, null), detector.onTimeout(11_500))
        assertNull(detector.deadlineMs)
        assertNull(detector.onTimeout(20_000))
        // A later unrelated launch must not fire anything.
        assertNull(feed(PEACOCK_STORE_PAGE, 12_000))
    }

    @Test
    fun netflixHomeTile_isIgnored() {
        assertNull(feed(NETFLIX_TILE_TRAMPOLINE, 0))
        assertNull(detector.deadlineMs)
        assertNull(feed(NETFLIX_TILE_FOLLOW_UP, 30))
        assertNull(detector.onTimeout(10_000))
    }

    @Test
    fun home_isIgnored() {
        assertNull(feed(HOME, 0))
        assertNull(detector.deadlineMs)
    }

    @Test
    fun ownRedirectLaunch_withNothingPending_isIgnored() {
        assertNull(feed(OWN_LEANBACK_LAUNCH, 0))
        // Fallback getLaunchIntentForPackage form: category LAUNCHER, but not from uid 1000.
        assertNull(feed(OWN_LAUNCHER_LAUNCH, 0))
        assertNull(detector.deadlineMs)
    }

    @Test
    fun nonStartLines_areIgnored() {
        assertNull(detector.onLine("I/ActivityTaskManager(  683): Displayed com.netflix.ninja/.MainActivity: +1s2ms", 0))
        assertNull(detector.onLine("--------- beginning of main", 0))
        assertNull(detector.onLine("", 0))
    }

    @Test
    fun nonStartLine_doesNotResolvePendingTrampoline() {
        feed(PRIME_TRAMPOLINE, 0)
        assertNull(detector.onLine("I/ActivityTaskManager(  683): Displayed com.amazon.venezia/.pdi.AppLaunchActivity: +5ms", 1))
        assertEquals(AppButton.PRIME_VIDEO, feed(PRIME_FOLLOW_UP, 30)?.button)
    }

    @Test
    fun trailingWhitespace_isTolerated() {
        assertEquals(AppButton.NETFLIX, detector.onLine(brief(NETFLIX_BUTTON) + "\r", 0)?.button)
    }

    @Test
    fun uidThatMerelyStartsWith1000_isNotSystem() {
        assertNull(feed(NETFLIX_BUTTON.replace("from uid 1000", "from uid 10001"), 0))
    }

    @Test
    fun reset_dropsPendingTrampoline() {
        feed(DISNEY_TRAMPOLINE, 0)
        detector.reset()
        assertNull(detector.deadlineMs)
        assertNull(feed(DISNEY_FOLLOW_UP, 20))
    }

    private companion object {
        const val NETFLIX_BUTTON =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=com.netflix.ninja cmp=com.netflix.ninja/.MainActivity (has extras)} from uid 1000"
        const val PRIME_TRAMPOLINE =
            "START u0 {act=android.intent.action.VIEW dat=amzns://apps/android?p=com.amazon.firebat&libraryItemId=deeplink_prime_video&libraryItemType=SHORTCUT flg=0x10000000 cmp=com.amazon.venezia/.pdi.AppLaunchActivity (has extras)} from uid 1000"
        const val PRIME_FOLLOW_UP =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10000000 pkg=com.amazon.firebat cmp=com.amazon.firebat/com.amazon.pyrocore.IgnitionActivity (has extras)} from uid 1000"
        const val DISNEY_TRAMPOLINE =
            "START u0 {act=android.intent.action.VIEW dat=amzns://apps/android?asin=B07Y8SJGCV&clickStreamReftag=r_btn_app_12 flg=0x10000000 cmp=com.amazon.venezia/.pdi.AppLaunchActivity (has extras)} from uid 1000"
        const val DISNEY_FOLLOW_UP =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10000000 pkg=com.disney.disneyplus cmp=com.disney.disneyplus/com.bamtechmedia.dominguez.main.MainActivity (has extras)} from uid 1000"
        const val PEACOCK_TRAMPOLINE =
            "START u0 {act=android.intent.action.VIEW dat=amzns://apps/android?asin=B08BG9MPT9&clickStreamReftag=r_btn_app_77 flg=0x10000000 cmp=com.amazon.venezia/.pdi.AppLaunchActivity (has extras)} from uid 1000"
        const val PEACOCK_STORE_PAGE =
            "START u0 {flg=0x8080000 cmp=com.amazon.venezia/.details.AppDetailsActivity (has extras)} from uid 10210"
        const val NETFLIX_TILE_TRAMPOLINE =
            "START u0 {act=android.intent.action.VIEW dat=amzns://apps/android?p=com.netflix.ninja&clickStreamReftag=apps_e73_alr_hm_1&PDI_SRC=YAC_AppDetailPage flg=0x10000000 cmp=com.amazon.venezia/.pdi.AppLaunchActivity (has extras)} from uid 10199"
        const val NETFLIX_TILE_FOLLOW_UP =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10000000 pkg=com.netflix.ninja cmp=com.netflix.ninja/.MainActivity (has extras)} from uid 1000"
        const val HOME =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000100 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 0"

        // Synthesized (not captured): what our own redirect looks like, from our app's uid.
        const val OWN_LEANBACK_LAUNCH =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10200000 pkg=com.netflix.ninja cmp=com.netflix.ninja/.MainActivity} from uid 10215"
        const val OWN_LAUNCHER_LAUNCH =
            "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 pkg=com.netflix.ninja cmp=com.netflix.ninja/.MainActivity} from uid 10215"
    }
}
