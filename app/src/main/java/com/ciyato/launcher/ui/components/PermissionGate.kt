package com.ciyato.launcher.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciyato.launcher.data.PermissionCapability
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted
import com.ciyato.launcher.ui.theme.CiyatoSec
import com.ciyato.launcher.ui.theme.CiyatoWhite

/**
 * The one place a special-access permission is explained and requested.
 *
 * PermissionCapability's own KDoc says the Play Data Safety form, the privacy
 * policy, `DATA_INVENTORY.md` **and the in-app disclosures** should all answer
 * from one row each. Three of those four did. The in-app disclosures did not: the
 * registry had no production caller at all, so six screens kept their own
 * hand-written Usage Access copy and the registry quietly became a document
 * rather than a source. F-194 was the finding about exactly that drift, and it
 * was half-fixed — the contract existed and was tested against the manifest,
 * while the text people actually read still came from six places.
 *
 * What the six versions had drifted into is the argument for this component:
 *
 * - Every one said what Ciyato wanted ("to show your screen time breakdown") and
 *   none said what the permission actually reaches. The registry's `scope` line
 *   does, including the part that reassures — no content, no keystrokes, no
 *   screen contents — which is the half a person needs to decide and the half
 *   nobody remembers to write.
 * - ContextualSuggestions said Usage Access lets Ciyato "learn your app
 *   patterns". That is the F-122 claim, which I removed from that screen's title,
 *   its body and its empty state, and which survived here — a fourth copy, in the
 *   text shown *before* consent, where a false claim costs the most.
 * - All six called `startActivity` unguarded, so on an image with no usage-access
 *   settings activity the Grant button crashed the app rather than doing nothing.
 *
 * @param capability the row being explained. Must be [PermissionCapability.Kind.SPECIAL_ACCESS];
 *   runtime permissions use the system dialog and do not belong here.
 * @param icon the glyph the screen already used, kept per-screen because it
 *   identifies the feature rather than the permission.
 * @param featureLine what this particular screen cannot do without it. The only
 *   per-screen sentence, because it is the only genuinely per-screen fact.
 */
@Composable
fun SpecialAccessGate(
    capability: PermissionCapability,
    icon: String,
    featureLine: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "${capability.settingsPathLabel()} is off",
            color = CiyatoWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            featureLine,
            color = CiyatoMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        // The scope, verbatim from the registry. Stated before the request rather
        // than in a policy document, because this is the moment the person is
        // deciding.
        Text(
            capability.scope,
            color = CiyatoSec,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val action = capability.settingsAction
                val opened = action != null && runCatching {
                    context.startActivity(Intent(action))
                }.isSuccess
                if (!opened) {
                    // Was a crash: six unguarded startActivity calls for a settings
                    // activity that a stripped or managed image need not have.
                    android.widget.Toast.makeText(
                        context,
                        "This phone will not open that page from here — ${capability.settingsPath}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = CiyatoGold),
        ) {
            Text("Open ${capability.settingsPathLabel()} settings", color = Color.Black)
        }
        Spacer(Modifier.height(12.dp))
        // What refusing costs, so declining is an informed choice too. The
        // registry forbids "nothing happens" in this field.
        Text(
            capability.onDenial,
            color = CiyatoMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The system's own name for the toggle, taken from the tail of [settingsPath].
 *
 * Using Android's wording rather than Ciyato's matters here: the person is about
 * to go looking for this switch in Settings, and a screen that calls it something
 * else has sent them to find a thing that is not there.
 */
private fun PermissionCapability.settingsPathLabel(): String =
    settingsPath.substringAfterLast('>').trim().ifBlank { settingsPath }
