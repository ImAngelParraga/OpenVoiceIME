package dev.rankis.openime.sharing

/** Screen-pixel geometry, independent of Android's window and node lifetimes. */
data class MessageBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

data class MessageActionPosition(val x: Int, val y: Int, val anchored: Boolean)

fun positionMessageAction(frame: MessageBounds, anchor: MessageBounds?, width: Int, height: Int,
                          gap: Int, bottomReserve: Int): MessageActionPosition {
    val maxX = (frame.right - width).coerceAtLeast(frame.left)
    val maxY = (frame.bottom - height).coerceAtLeast(frame.top)
    val visible = anchor?.takeIf { it.width > 0 && it.height > 0 && it.top >= frame.top &&
        it.bottom <= frame.bottom && it.right > frame.left && it.left < frame.right }
    if (visible != null) {
        val below = visible.bottom + gap
        val above = visible.top - gap - height
        val y = when {
            below <= maxY -> below
            above >= frame.top -> above
            else -> null
        }
        if (y != null) return MessageActionPosition((visible.right - width).coerceIn(frame.left, maxX), y, true)
    }
    return MessageActionPosition((frame.left + (frame.width - width) / 2).coerceIn(frame.left, maxX),
        (frame.bottom - bottomReserve - height).coerceIn(frame.top, maxY), false)
}
