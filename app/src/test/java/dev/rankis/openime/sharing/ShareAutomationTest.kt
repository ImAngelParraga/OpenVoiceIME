package dev.rankis.openime.sharing

import org.junit.Assert.*
import org.junit.Test

class ShareAutomationTest {
    private fun ShareAutomation.step(pkg: String = "chat", now: Long = 1L, more: Boolean = false,
        share: Boolean = false, target: Boolean = false, resolver: Boolean = false, receiver: Boolean = false) =
        next(pkg, now, more, share, target, resolver, receiver)

    @Test fun selectedMessageNeedsExplicitStart() {
        val flow = ShareAutomation()
        assertNull(flow.step(more = true))
        flow.start("chat", 0)
        assertEquals(ShareAutomation.Action.MORE, flow.step(more = true))
        assertNull(flow.step(more = true))
        assertEquals(ShareAutomation.Action.SHARE, flow.step(share = true))
        assertNull(flow.step(share = true))
        assertEquals(ShareAutomation.Action.TARGET, flow.step("android", target = true, resolver = true))
        assertNull(flow.step("android", target = true, resolver = true))
        assertEquals(ShareAutomation.Action.STOP, flow.step("own", receiver = true))
        assertEquals(ShareAutomation.Stage.IDLE, flow.stage)
    }

    @Test fun directShareSkipsMoreAndUnrelatedAppsCannotReceiveClicks() {
        val flow = ShareAutomation().apply { start("chat", 0) }
        assertEquals(ShareAutomation.Action.SHARE, flow.step(share = true))
        assertNull(flow.step(target = true))
        assertEquals(ShareAutomation.Action.STOP, flow.step("other", target = true))
        assertNull(flow.step("android", target = true, resolver = true))
    }

    @Test fun timeoutAndExplicitStopPreventLaterActions() {
        val flow = ShareAutomation().apply { start("chat", 0) }
        assertEquals(ShareAutomation.Action.STOP, flow.step(now = 8_000, share = true))
        flow.start("chat", 10_000)
        flow.stop()
        assertNull(flow.step(now = 10_001, share = true))
    }

    @Test fun chooserScrollingIsBoundedAndConfirmationHappensOnlyOnceAfterOwnTarget() {
        val flow = ShareAutomation().apply { start("chat", 0) }
        assertEquals(ShareAutomation.Action.SHARE, flow.step(share = true))
        for (now in listOf(1L, 501L, 1001L, 1501L)) {
            assertEquals(ShareAutomation.Action.SCROLL, flow.next("android", now, false, false, false, true, false, canScroll = true))
        }
        assertNull(flow.next("android", 2001L, false, false, false, true, false, canScroll = true, confirm = true))
        assertEquals(ShareAutomation.Action.TARGET, flow.step("android", now = 2100L, target = true, resolver = true))
        assertEquals(ShareAutomation.Action.CONFIRM, flow.next("android", 2200L, false, false, false, true, false, confirm = true))
        assertNull(flow.next("android", 2300L, false, false, false, true, false, confirm = true))
    }

    @Test fun resolverCannotOpenOriginMenusAndTargetMustFollowShare() {
        val flow = ShareAutomation().apply { start("chat", 0) }
        assertNull(flow.step("android", more = true, share = true, target = true, resolver = true))
        assertEquals(ShareAutomation.Action.MORE, flow.step(more = true))
        assertNull(flow.step("android", share = true, resolver = true))
    }
}
