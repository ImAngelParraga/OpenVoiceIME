package dev.rankis.openime.settings

enum class AfterInsertAction {
    SwitchKeyboard,
    HidePanel,
    KeepDictating,
    ;

    companion object {
        fun of(settings: AppSettings): AfterInsertAction = when {
            settings.returnToKeyboardAfterInsert -> SwitchKeyboard
            settings.hideAfterSuccess -> HidePanel
            else -> KeepDictating
        }
    }
}

fun AppSettings.withAfterInsertAction(action: AfterInsertAction): AppSettings = when (action) {
    AfterInsertAction.SwitchKeyboard -> copy(returnToKeyboardAfterInsert = true, hideAfterSuccess = true)
    AfterInsertAction.HidePanel -> copy(returnToKeyboardAfterInsert = false, hideAfterSuccess = true)
    AfterInsertAction.KeepDictating -> copy(returnToKeyboardAfterInsert = false, hideAfterSuccess = false)
}

enum class ServerReadiness {
    NotConfigured,
    NotTested,
    Ready,
}

fun serverReadiness(settings: AppSettings, connectionTested: Boolean): ServerReadiness = when {
    !validateSettings(settings).isValid -> ServerReadiness.NotConfigured
    connectionTested -> ServerReadiness.Ready
    else -> ServerReadiness.NotTested
}
