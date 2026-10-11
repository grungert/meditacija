# Meditacija — plan

Android aplikacija za smirenje i razumijevanje vlastitih uvida. Dva stuba dijele isti vizuelni svijet:

1. **Nebo** — ekran za meditaciju: oblaci u Ghibli stilu koji „dišu" s tobom i pokazuju udah i izdah.
2. **Zapažanja** — uzorkovanje iskustva: telefon te u nasumičnim trenucima pita kako si; odgovor glasom ili tekstom za 10–20 sekundi.

## Odluke (2026-10-11)

| Tema | Odluka |
|---|---|
| Platforma | Android 13+ (minSdk 33), Kotlin + Jetpack Compose |
| Nebo | Shader u stvarnom vremenu: prototip u WebGL-u (browser), zatim port u AGSL (`RuntimeShader`) |
| Redoslijed | Nebo i disanje prvo, zatim zapažanja |
| Alati | Android Studio instalira korisnik; build i iz terminala (`./gradlew`) |
| Notifikacije | `AlarmManager.setAndAllowWhileIdle` (ne WorkManager — Doze ga može odgoditi satima) |
| Glas | Snimak se uvijek čuva kao audio; transkripcija je zaseban, ponovljiv korak |
| Podaci | Sve lokalno (Room); izvoz u običan `.txt` |

## Nebo — vizuelni jezik

- Veliki kumulusi s ravnim dnom i visokim, zaobljenim vrhovima; oštre (ne zamućene) ivice.
- Slikarsko sjenčenje u 3 tona: topla bijela (osvijetljeno), hladna bijela (sredina), lavanda-plava (sjenka) + krem odsjaj na ivici prema suncu.
- Jak gradijent neba (azur → blijedo tirkizna kod horizonta), sunčev sjaj i blagi zraci.
- Slojevi s paralaksom (daleki, srednji, bliski oblaci, cirusi) + niska livada s talasima vjetra.
- Paleta: dan i zalazak.

### Disanje

| Faza | Šta nebo radi |
|---|---|
| Udah | sunce jača, osvijetljeni dio oblaka raste, oblaci se blago šire |
| Zadržavanje | vjetar staje |
| Izdah | svjetlo se smiruje, oblaci nastavljaju da plove |

Ritmovi: 4–6 (podrazumijevano), 5,5–5,5 koherentno, 4–7–8, kvadrat 4–4–4–4. Natpis faze + odbrojavanje; blaga vibracija na promjeni faze.

## Faze

| Faza | Sadržaj | Status |
|---|---|---|
| 0 | Prototip neba u browseru (`prototype/nebo/`) | u toku |
| 1a | Android kostur, ekran Nebo (AGSL port), izbor ritma | |
| 1b | Zapažanja: nasumične notifikacije (3–4 dnevno u prozoru, npr. 9–20h), utišaj dan, snimak glasa i/ili tekst, dnevnik, izvoz `.txt` | |
| 2 | Whisper na telefonu (whisper.cpp) + ponovna transkripcija na računaru | |
| 3 | Laya + sinhronizacija s računarom | otvoreno: šta Laya tačno radi |
| 4 | Baza priča i koana | |

## Principi zapažanja

- Bez obaveznih polja, bez nizova i bodova. Propuštena notifikacija je samo propuštena.
- Odgovor za 10–20 sekundi.
- Sve lokalno; izvoz kad korisnik poželi.

## Model podataka (nacrt, faza 1b)

```
Entry(id, promptedAt?, answeredAt, audioPath?, text?, transcript?, transcriptModel?, tags[])
Prompt(id, scheduledAt, firedAt?, answeredEntryId?, dismissed)
Settings(windowStart, windowEnd, promptsPerDay, silencedDate?)
```

Izvoz: jedan zapis po bloku — datum i vrijeme, je li odgovor na notifikaciju, tekst/transkript.
