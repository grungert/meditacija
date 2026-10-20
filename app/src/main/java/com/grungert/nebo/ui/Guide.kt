package com.grungert.nebo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grungert.nebo.Screen
import com.grungert.nebo.app

/** The first look at Nebo: a few short pages, skippable, shown once (and again from Help). */
private val INTRO = listOf(
    Triple(NeboIcon.Sun, "Nebo", "Mjesto za nekoliko daha, svaki dan. Nebo iznad tebe prati pravo doba dana, vrijeme i godišnje doba."),
    Triple(NeboIcon.Play, "Kako početi", "Na početnom ekranu dodirni ikonicu da vidiš šta je i kako ide, a drži prst na njoj da počne. Veliko dugme Kreni pokreće ono što si radio zadnji put."),
    Triple(NeboIcon.Mic, "Zapiši kako si", "Glasom ili tekstom, riječ ili dvije. Od bilješki Nebo vremenom pokaže šta se vraća, gdje nosiš napetost i šta ti pomaže."),
    Triple(NeboIcon.Heart, "Samo tvoje", "Sve ostaje na telefonu. Ni glas ni riječi ne idu na internet. Kopiju praviš ti, kad hoćeš, u Postavkama."),
)

@Composable
fun IntroScreen(go: (Screen) -> Unit) {
    val s = LocalContext.current.app.settings
    var page by remember { mutableIntStateOf(0) }
    fun done() { s.introSeen = true; go(Screen.Home) }
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().screenPadding().padding(vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < INTRO.lastIndex) Text("Preskoči", style = BodyStyle.copy(color = Ink.soft, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { done() }.padding(10.dp))
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "intro") { p ->
                val (icon, title, text) = INTRO[p]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Ink.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, Ink.accent, 38.dp) }
                    Text(title, style = TitleStyle.copy(fontSize = 34.sp), textAlign = TextAlign.Center)
                    Text(text, style = BodyStyle.copy(fontSize = 18.sp, lineHeight = 27.sp), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
                for (i in INTRO.indices) Box(Modifier.padding(horizontal = 5.dp).size(if (i == page) 10.dp else 7.dp).clip(CircleShape)
                    .background(if (i == page) Ink.accent else Ink.faint))
            }
            GlassButton(if (page < INTRO.lastIndex) "Dalje" else "Počni", { if (page < INTRO.lastIndex) page++ else done() },
                Modifier.fillMaxWidth(), strong = true)
        }
    }
}

/** How Nebo works: every kind of session, notes and the four points, threads, the overview, reminders and privacy. */
@Composable
fun HelpScreen(go: (Screen) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        Scrim(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .screenPadding().padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader("Kako radi Nebo", { go(Screen.Settings) })
            Text("Na početnom ekranu dodirni ikonicu da vidiš šta je, a drži prst na njoj da počne. Krug oko ikonice pokazuje koliko još.",
                style = BodyStyle)

            Panel {
                SectionLabel("Vrste sesija")
                Text("Dodirni jednu za više.", style = SmallStyle.copy(shadow = null))
                for (t in SESSION_TYPES) {
                    val help = SESSION_HELP[t.id] ?: continue
                    val shown = open == t.id
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { open = if (shown) null else t.id }
                        .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Ink.surfaceHigh), contentAlignment = Alignment.Center) { Icon(t.icon, Ink.accent, 19.dp) }
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold, shadow = null))
                                Text(help.what, style = SmallStyle.copy(shadow = null))
                            }
                        }
                        AnimatedVisibility(shown) {
                            Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(help.how, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Ink.soft, shadow = null))
                                Text("Kada pomaže: " + help.helps, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Ink.soft, shadow = null))
                                Text("Trajanje: " + help.length, style = SmallStyle.copy(shadow = null))
                            }
                        }
                    }
                }
            }

            for ((title, lines) in GUIDE) Panel {
                SectionLabel(title)
                for (l in lines) Text(l, style = BodyStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, shadow = null))
            }

            GlassButton("Pogledaj uvod ponovo", { go(Screen.Intro) }, Modifier.fillMaxWidth())
        }
    }
}

private val GUIDE = listOf(
    "Bilješke i četiri tačke" to listOf(
        "Zapiši kako si glasom ili tekstom. Govor postaje tekst na samom telefonu.",
        "Četiri tačke su misli, emocije, tijelo i nagoni. Utiču jedne na druge: misao probudi strah, strah stegne stomak, stomak traži da pobjegneš. Nebo ih pročita iz tvojih riječi, a ti ih možeš ispraviti.",
        "Prije i poslije sesije možeš zapisati kako si (uključuje se u podešavanju sesije). Tako Pregled može pokazati šta ti pomaže.",
    ),
    "Niti" to listOf(
        "Kad se ista stvar vrati u bilješkama različitih dana, nastaje nit: posao, san, porodica, telefon uveče. Svaka nit pokaže kada dolazi, s čim, da li se budi ili smiruje, i predloži vježbu koja joj odgovara.",
        "U Dnevniku su pod Niti, a i kao sazviježđa na nebu zapisa.",
    ),
    "Pregled" to listOf(
        "Minute po sedmici, kada meditiraš, raspoloženja kroz sedmice, kako su emocije, tijelo i nagoni povezani, gdje nosiš napetost, navike uma i šta ti pomaže.",
        "Ništa se ne tvrdi iz premalo podataka: Nebo kaže šta vidi tek kad se nešto stvarno ponavlja.",
    ),
    "Podsjetnici" to listOf(
        "Sve po želji, u Postavkama: podsjetnik za meditaciju, kratki pozivi tokom dana (kako si sada?), 50-50 trenuci, pitanje ujutro, večernji pregled, nedjeljni pogled na sedmicu, i pitanje dan poslije teške bilješke.",
        "Na dugi pritisak na ikonu aplikacije su prečice, a u brzim podešavanjima telefona može se dodati pločica za tri minute.",
    ),
    "Privatnost" to listOf(
        "Sve ostaje na telefonu: bilješke, snimci, sesije. Ništa ne ide na internet, a modeli za govor i značenje rade na samom telefonu.",
        "Kopiju praviš ti, ručno ili jednom sedmično u folder koji izabereš. Iz nje se sve može vratiti.",
    ),
)
