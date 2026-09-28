package com.ciyato.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * "We asked and could not find out" — never "there is nothing".
 *
 * Named for the failure rather than for a generic error because the distinction is
 * the whole reason it exists. A screen with no data has two completely different
 * things to say and this audit found four screens saying the friendly one for both:
 * a failed usage query rendered as a reassuring "😌 Quiet day", a failed
 * count rendered as "Checked all 500 on this device", a failed frequency query
 * rendered as "Not enough data yet - use your phone for a few days". Each invited
 * the person to believe something untrue, and the last one invited them to wait
 * for a result that was never coming.
 *
 * [detail] is where that gets said out loud. It should contradict the reassuring
 * reading explicitly - "this is not a quiet day", "waiting will not help" - because
 * the person has usually already formed it from the empty screen.
 *
 * There was a `CiyatoErrorState` in the component library that nothing ever called,
 * taking a single `message` and offering a Retry. It could not express any of this:
 * a title and a detail collapse into one sentence, and Retry is wrong for a
 * permission that Android has refused. It was deleted rather than adopted, and the
 * lesson is recorded in the removal ledger - a component written without a call
 * site gets shaped by what seemed reasonable rather than by what a screen needs,
 * and then sits there looking available.
 */
@Composable
fun QueryFailureState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A warning glyph, deliberately not the calm one. The emoji carried more
            // of the wrong message than the words did.
            Text("⚠️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = CiyatoWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = CiyatoMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
