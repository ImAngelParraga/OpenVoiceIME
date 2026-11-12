package dev.rankis.openime.sharing

/** Bounded explicit-action sequence. No coordinates or arbitrary menu items are accepted. */
class ShareAutomation {
    enum class Stage { IDLE, MESSAGE, SHARE_MENU, TARGET, OPENING }
    enum class Action { MORE, SHARE, TARGET, SCROLL, CONFIRM, STOP }
    var stage = Stage.IDLE
        private set
    private var origin = ""
    private var deadline = 0L
    private var scrolls = 0
    private var nextScroll = 0L
    private var confirmed = false

    fun start(packageName: String, now: Long) {
        origin = packageName
        deadline = now + 8_000L
        stage = Stage.MESSAGE
        scrolls = 0
        nextScroll = now
        confirmed = false
    }

    fun stop() { stage = Stage.IDLE }

    fun next(packageName: String, now: Long, more: Boolean, share: Boolean, target: Boolean,
             resolver: Boolean, receiver: Boolean, canScroll: Boolean = false, confirm: Boolean = false): Action? {
        if (stage == Stage.IDLE) return null
        if (now >= deadline || receiver || (packageName != origin && !resolver)) {
            stop()
            return Action.STOP
        }
        return when (stage) {
            Stage.MESSAGE -> if (packageName == origin) when {
                share -> { stage = Stage.TARGET; Action.SHARE }
                more -> { stage = Stage.SHARE_MENU; Action.MORE }
                else -> null
            } else null
            Stage.SHARE_MENU -> if (packageName == origin && share) {
                stage = Stage.TARGET; Action.SHARE
            } else null
            Stage.TARGET -> when {
                resolver && target -> { stage = Stage.OPENING; Action.TARGET }
                resolver && canScroll && scrolls < 4 && now >= nextScroll -> {
                    scrolls++
                    nextScroll = now + 450L
                    Action.SCROLL
                }
                else -> null
            }
            Stage.OPENING -> if (resolver && confirm && !confirmed) {
                confirmed = true
                Action.CONFIRM
            } else null
            else -> null
        }
    }
}
