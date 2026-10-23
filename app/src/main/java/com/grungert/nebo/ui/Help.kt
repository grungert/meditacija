package com.grungert.nebo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a kind of session is, how it goes, when it helps, and how long it takes: the card a tap on its icon opens. */
class SessionHelp(val what: String, val how: String, val helps: String, val length: String)

val SESSION_HELP = mapOf(
    "breath" to SessionHelp(
        "Dah po ritmu koji izabereš. Krug svjetla se širi na udahu i skuplja na izdahu, a ti ga pratiš.",
        "Izabereš ritam, na primjer 4–6: udah četiri, izdah šest sekundi. Ako zatvoriš oči, dah vode tihi zvuk i blaga vibracija.",
        "Kad si napet ili rasut, prije sna, prije razgovora koji te brine. Duži izdah smiruje tijelo.",
        "3 do 20 minuta, ili bez ograničenja"),
    "walk" to SessionHelp(
        "Hodanje svjesno, korak po korak: livadom na ekranu, ili stvarno, s telefonom u ruci.",
        "Dah prati korake, a pejzaž ide pored tebe. Pažnja je na stopalima i na ritmu koraka i daha.",
        "Kad ne možeš da sjediš mirno, kad si nemiran ili umoran od sjedenja.",
        "3 do 20 minuta, ili bez ograničenja"),
    "thoughts" to SessionHelp(
        "Misli gledaš kao oblake koji dolaze i prolaze, a da ih ne pratiš.",
        "Sjediš uz dah. Kad primijetiš da je misao odlutala, dodirni nebo: misao postane oblak i ode. Na kraju označiš kuda je um najčešće odlazio.",
        "Kad se misli vrte, kad planiraš ili premotavaš. Vidiš koliko često um odluta, i to je sasvim u redu.",
        "3 do 20 minuta, ili bez ograničenja"),
    "silence" to SessionHelp(
        "Samo nebo i zvono, bez vođenja.",
        "Zvono na početku i na kraju, a po želji i između. Ti biraš čemu daješ pažnju: dahu, zvucima ili tijelu.",
        "Kad već znaš kako, ili kad ti treba mir bez riječi.",
        "3 do 20 minuta, ili bez ograničenja"),
    "points" to SessionHelp(
        "Misli, emocije, tijelo i nagoni, jedno po jedno, pa sve zajedno.",
        "Sesija ima pet jednakih dijelova. Svaki pita šta primjećuješ u jednoj tački, a posljednji u sve četiri odjednom: kako jedna pomjera drugu.",
        "Kad je nešto teško i zapetljano, da vidiš od čega se sastoji.",
        "3 do 20 minuta"),
    "hourglass" to SessionHelp(
        "Tri minute za usred dana: široko, pa samo dah, pa cijelo tijelo.",
        "Prvi minut primijetiš šta je sada tu. Drugi suziš pažnju na dah. Treći je proširiš na cijelo tijelo. Pijesak teče dok traje.",
        "Između obaveza, prije sastanka, kad osjetiš da gubiš tlo pod nogama. Najkraći put nazad sebi.",
        "3 minute"),
    "bodyscan" to SessionHelp(
        "Svjetlo putuje od stopala do tjemena, dio po dio.",
        "Na svakom dijelu primijetiš šta osjećaš, bez mijenjanja. Ako negdje osjetiš napetost, dodirni to mjesto na figuri. Vremenom vidiš gdje je najčešće nosiš.",
        "Kad si u glavi, a ne u tijelu; prije sna; kad osjećaš napetost, a ne znaš gdje.",
        "5 do 20 minuta, ili bez ograničenja"),
    "urge" to SessionHelp(
        "Posmatraš poriv (telefon, nešto slatko, cigaretu) kako raste, dostigne vrh i opada, umjesto da ga poslušaš.",
        "Izabereš šta te vuče i koliko jako, od 1 do 10. Pet kratkih koraka prate talas na vodi. Na kraju opet ocijeniš jačinu i vidiš koliko je pao.",
        "U trenutku kad te nešto vuče, prije nego što uradiš.",
        "3, 5 ili 10 minuta"),
    "rain" to SessionHelp(
        "Četiri koraka za tešku emociju: prepoznaj, dozvoli, istraži, njeguj.",
        "Imenuješ šta osjećaš, dozvoliš da bude tu, dodirneš gdje je u tijelu i kažeš sebi nešto blago. Ideš dalje kad si spreman, bez tajmera. Sve se sačuva kao bilješka.",
        "Kad te nešto preplavi: tuga, ljutnja, strah, stid.",
        "koliko treba, obično 3 do 8 minuta"),
    "note" to SessionHelp(
        "Kratka bilješka o tome kako si: glasom ili tekstom.",
        "Govoriš do dva minuta (tekst nastaje na telefonu, ništa ne ide na internet) ili pišeš. Možeš označiti raspoloženje i četiri tačke. Od bilješki nastaju niti i pregledi.",
        "Uvijek kad primijetiš nešto u sebi. Riječ ili dvije su dovoljne.",
        "minut-dva"),
)

/**
 * A round icon that starts its session only when held: a ring of light fills around it and, when it closes, the session
 * begins with a short tick. A tap instead opens what the session is and how it works. With a screen reader the tap
 * opens the card (where Kreni is), and "Pokreni odmah" is offered as an action.
 */
@Composable
fun HoldIconButton(icon: NeboIcon, label: String, onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, circle: Dp = 48.dp, holdMillis: Int = 700) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .semantics {
                role = Role.Button
                onClick(label = "Opis") { tap(); true }
                customActions = listOf(CustomAccessibilityAction("Pokreni odmah") { hold(); true })
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val start = System.currentTimeMillis()
                    var done = false
                    val fill = scope.launch {
                        delay(110)   // a quick tap should not flash the ring
                        progress.animateTo(1f, tween(holdMillis - 110, easing = LinearEasing))
                        done = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hold()
                    }
                    val released = tryAwaitRelease()
                    if (!done) {
                        fill.cancel()
                        if (released && System.currentTimeMillis() - start < 300) tap()
                        scope.launch { progress.animateTo(0f, tween(220)) }
                    }
                })
            }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(circle + 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(circle).clip(CircleShape).background(if (selected) Ink.paper else Ink.surfaceHigh), contentAlignment = Alignment.Center) {
                Icon(icon, if (selected) Ink.inkOnPaper else Ink.text, 24.dp)
            }
            // the ring of the hold, from the top, clockwise
            Canvas(Modifier.size(circle + 8.dp)) {
                val p = progress.value
                if (p <= 0f) return@Canvas
                val w = 3.dp.toPx()
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                drawArc((if (Ink.day) Color(0xFFE0A04E) else Ink.accent).copy(alpha = 0.22f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arcSize, style = Stroke(w))
                drawArc((if (Ink.day) Color(0xFFE0A04E) else Ink.accent), -90f, 360f * p, false, androidx.compose.ui.geometry.Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Text(label, style = SmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink.text), maxLines = 1, softWrap = false)
    }
}

/** The card for one kind of session (or for a note): what it is, how it goes, when it helps, how long, and the way in. */
@Composable
fun SessionInfoSheet(id: String, name: String, icon: NeboIcon, start: String, onStart: () -> Unit, onSetup: (() -> Unit)?, onClose: () -> Unit) {
    val help = SESSION_HELP[id] ?: return
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Ink.sheet)
                .clickable(interactionSource = null, indication = null) {}
                .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Ink.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Icon(icon, Ink.accentInk, 24.dp) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = HeadStyle.copy(shadow = null))
                    Text(help.length, style = SmallStyle.copy(shadow = null))
                }
                Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Zatvori", onClick = onClose).padding(10.dp)) { Icon(NeboIcon.Close, Ink.text, 18.dp) }
            }
            Text(help.what, style = BodyStyle.copy(fontSize = 17.sp, lineHeight = 25.sp, shadow = null))
            for ((label, text) in listOf("KAKO IDE" to help.how, "KADA POMAŽE" to help.helps)) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = LabelStyle.copy(shadow = null))
                Text(text, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Ink.soft, shadow = null))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onSetup != null) GlassButton("Podesi", onSetup, Modifier.weight(1f), icon = NeboIcon.Settings)
                GlassButton(start, onStart, Modifier.weight(if (onSetup != null) 1.4f else 1f), strong = true, icon = NeboIcon.Play)
            }
            if (id != "note") Text("Sljedeći put: drži prst na ikonici i počinje odmah.", style = SmallStyle.copy(fontSize = 12.sp, shadow = null))
        }
    }
}
