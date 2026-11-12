package dev.rankis.openime.sharing

/** Same-process acknowledgement after the receiver's first frame. Holds no audio or text. */
internal object ShareHandoff {
    var receiverReady: (() -> Unit)? = null
}
