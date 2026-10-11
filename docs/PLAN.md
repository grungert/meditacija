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
- Slojevi s paralaksom (daleki, srednji, bliski oblaci, cirusi).
- Livada: pojedinačne vlati trave koje se povijaju u naletima vjetra (isti talas pomjera i odsjaj na brdu), nekoliko stabljika trske s perjanicama, lišće koje vjetar nosi preko neba.
- Doba dana: noć → zora → jutro → dan → zlatno popodne → zalazak → sumrak → noć. Sunce i mjesec putuju lukom; svjetlo na oblacima dolazi od njih. Noću zvijezde, mlijecni put i krijesnice.
- Načini: **po satu** (podrazumijevano, prati stvarno vrijeme), **ciklus dana** (cijeli dan za 3 minute) ili ručno izabrano doba; prelaz uvijek ide naprijed kroz dan.

### Šetnja (uključuje se dugmetom „Šetnja")

Pogled sa strane: livada polako klizi s desna na lijevo, a krug ostaje u mjestu kao sidro.

- Korak ide s dahom: na izdah hodaš, na udah se skoro zaustaviš, na zadržavanju stojiš.
- Paralaksa: daleka brda (s maglom u dolinama) idu najsporije, greben livade srednje, bliska trava najbrže; oblaci se pomjere tek malo.
- Usput, otprilike jednom u minut-dva: usamljeno drvo, red telegrafskih stubova, polje cvijeća, mali putni oltar, kamen s mahovinom.
- Za pregled u browseru: `?brzo` ubrzava šetnju 20 puta (radi samo lokalno).

### Disanje

Indikator je **krug svjetla** iznad oblaka: na udah raste, na izdah se smanjuje. Luk oko kruga pokazuje napredak faze, a svjetlucave čestice se na punim plućima skupe na krug i na izdah rasprše. Na svakoj promjeni faze od kruga se odvoji talas.

| Faza | Šta se dešava |
|---|---|
| Udah | krug raste, čestice se skupljaju, sunce jača, osvijetljeni dio oblaka raste |
| Zadržavanje | vjetar staje: oblaci, trava i lišće se smire |
| Izdah | krug se smanjuje, čestice se raspršuju, svjetlo se smiruje |

Ritmovi: 4–6 (podrazumijevano), 5,5–5,5 koherentno, 4–7–8, kvadrat 4–4–4–4. Natpis faze + odbrojavanje u krugu; blaga vibracija na promjeni faze.

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
