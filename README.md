# Nebo

Android aplikacija za meditaciju i razumijevanje vlastitih uvida. Nebo s oblacima u Ghibli stilu diše s tobom, a glasovne bilješke prije i poslije sesije postaju tekst i biraju koan ili poruku po značenju. Sve ostaje na telefonu.

## Šta ima

- **Sesije:** Disanje (krug svjetla), Šetnja (livadom, korak uz dah), Oblaci misli (dodir neba pretvara misao u oblak koji otplovi), Tišina. Trajanje od 3 do 20 minuta ili slobodno, zvono na početku i kraju, po želji tiho zvono svakih 5 minuta.
- **Nebo:** oblaci, trava, stvari koje se sreću usput i doba dana (po satu, ciklus dana ili ručno). Shader je isti kao u prototipu (`prototype/nebo/`).
- **Bilješke glasom:** snimak se uvijek čuva, a Whisper ga na telefonu pretvara u tekst. Može se i pisati i izabrati raspoloženje.
- **Koan ili poruka:** 49 koana iz *Bezvratne kapije* (javno vlasništvo, prepričano na naš jezik) i 42 originalne poruke. Bira ih model za značenje (multilingual-e5-small), a bez njega ključne riječi.
- **Zapažanja tokom dana:** 2–5 notifikacija u nasumično vrijeme, u prozoru koji izabereš, uz mogućnost da utišaš dan.
- **Uvidi:** zvjezdicom označiš bilješku kao uvid; „Slične" pokaže ranije bilješke najbliže po značenju („kad sam se ovako osjećao").
- **Sedmični pregled:** prvo tvoje zapažanje, pa šta se vraćalo u odnosu na prošlu sedmicu, kada, tvojim riječima i ranija slična stanja. Podsjetnik nedjeljom u 19 h.
- **Zvuk prirode:** vjetar koji jača na izdah, ptice danju, cvrčci noću, sve sintetizovano u kodu.
- **Zatvorene oči:** ekran se zatamni, dah vode vibracija koja raste i opada i tihi tonovi.
- **Odgovor iz notifikacije:** „Napiši" (odgovor u samoj notifikaciji), „mirno", „nemirno".
- **Dnevnik, pregled i izvoz:** minute po sedmici, doba dana, teme koje se vraćaju, „prije → poslije". Izvoz je u običan tekst. Bez nizova i bodova.

Fontovi: Cormorant Garamond i Nunito (SIL Open Font License, licence u `content/fonts/`).

## Build

Potrebni su Android Studio ili JDK 17, Android SDK (platforma 37), NDK 28.2 i CMake 3.31.

```bash
git submodule update --init
./gradlew :app:assembleDebug
```

APK se pojavi u `app/build/outputs/apk/debug/`. Whisper (whisper.cpp v1.9.4) se kompajlira iz `third_party/whisper.cpp`, uvijek kao Release.

Test tokenizera poredi Kotlin implementaciju s Hugging Face tokenizerom:

```bash
./gradlew :app:testDebugUnitTest -Pspm=/putanja/do/sentencepiece.bpe.model
```

## Modeli

Modeli se preuzimaju iz aplikacije (Postavke):

| Model | Veličina | Čemu služi |
|---|---|---|
| Whisper base / small / large-v3-turbo | 60 / 190 / 574 MB | glas u tekst |
| multilingual-e5-small (int8) | 123 MB | izbor koana po značenju |

Za Whisper je podrazumijevani jezik hrvatski, jer daje latinicu i ijekavicu najbliže crnogorskom.

## Struktura

```
app/src/main/java/com/grungert/nebo/
  sky/      nebo: GL shader, livada i krug (Scenery), disanje, doba dana
  ui/       ekrani (početni, sesija, bilješka i koan, dnevnik, pregled, postavke)
  data/     SQLite, postavke, izvoz
  audio/    snimanje, zvono, reprodukcija
  ml/       Whisper (JNI), tokenizer, e5 embedder, preuzimanje modela
  koan/     biblioteka i izbor koana
  esm/      nasumične notifikacije
app/src/main/assets/   koani.json, poruke.json, sky.frag
content/README.md      kako dopuniti biblioteku koana
docs/PLAN.md           plan i odluke
```
