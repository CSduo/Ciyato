package com.ciyato.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Open a system screen, and never let the tap do nothing.
 *
 * Six places asked Android to open a settings page, and each got it slightly wrong
 * in one of two ways:
 *
 * - `startActivity` with no guard at all, which throws `ActivityNotFoundException`
 *   when nothing handles the Intent. In `AppContextMenu` that meant long-pressing an
 *   app and choosing App Info could **crash the launcher** — the process that draws
 *   the home screen — on any image without that settings activity.
 * - `runCatching { startActivity(...) }` with no else branch, which is the same tap
 *   doing nothing at all, silently. Safer and just as confusing: the person taps the
 *   one control offered to fix their problem and gets no response, so they tap again.
 *
 * Both were already fixed once each, in `openAppSettings` and in the permission gate,
 * and both kept reappearing because each site was solving it locally. This is the
 * shared answer, written with its call sites rather than ahead of them.
 *
 * @param whereInstead where the person can reach the same screen by hand, shown when
 *   Android will not open it. Never a bare "failed" — the point of telling them is
 *   that they can still get there.
 * @return true when the screen opened.
 */
fun openSystemScreen(context: Context, intent: Intent, whereInstead: String): Boolean {
    val opened = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) {
        Toast.makeText(
            context,
            "This phone will not open that page from here — $whereInstead",
            Toast.LENGTH_LONG,
        ).show()
    }
    return opened
}

/**
 * Hand an implicit Intent to whichever app handles it, and say so when none does.
 *
 * Separate from [openSystemScreen] because the message has to be different: a missing
 * settings page is a quirk of the build, while a missing calendar or store app is a
 * fact about the phone that the person can act on by installing one. Telling someone
 * to "find it in Settings" when the real answer is "you have no calendar app" sends
 * them looking for something that is not there.
 */
fun openWithApp(context: Context, intent: Intent, noHandlerMessage: String): Boolean {
    val opened = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) {
        Toast.makeText(context, noHandlerMessage, Toast.LENGTH_LONG).show()
    }
    return opened
}
