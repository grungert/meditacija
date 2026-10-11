# Biblioteka sadržaja: koani i poruke

Poslije svake meditacije korisnik izgovori kratak uvid ili osjećaj. Aplikacija taj tekst pretvori u vektor (model EmbeddingGemma) i izabere koan ili poruku čije je polje `kadaPomaze` po značenju najbliže onome što je rečeno. Zato je `kadaPomaze` najvažnije polje u cijeloj biblioteci.

Fajlovi:

- `app/src/main/assets/koani.json` — 49 slučajeva iz zbirke *Bezvratna kapija*, prevedenih na crnogorski (ijekavica, latinica).
- `app/src/main/assets/poruke.json` — 42 kratke originalne poruke za trenutke kad zagonetni koan ne bi pristajao (umor, tuga, nemir, samokritika, zahvalnost, radost i slično).

## Izvor i autorska prava

Koani su iz zbirke *Wumenguan* / *Mumonkan* (Mumon, 1228), prema engleskom prevodu Nyogena Senzakija i Paula Repsa, *The Gateless Gate* (1934):
https://en.wikisource.org/wiki/The_Gateless_Gate

Original je u javnom vlasništvu zbog starosti. Engleski prevod je u SAD-u u javnom vlasništvu jer autorsko pravo nije obnovljeno (Wikisource oznake PD-old i PD-US-no-renewal). Prevodi na crnogorski su naši, rađeni slobodno, ali vjerno engleskom tekstu. Mumonovi komentari su ponegdje skraćeni. Poruke su originalni tekstovi napisani za ovu aplikaciju, nijesu citati.

## Teme

U polju `teme` koristi samo ove ključeve, od 1 do 4 po stavci:

`nemir`, `tuga`, `ljutnja`, `umor`, `strah`, `sumnja`, `zudnja` (vezanost, žudnja), `kontrola`, `samokritika`, `usamljenost`, `zahvalnost`, `radost`, `radoznalost`, `zbunjenost`, `smirenost`, `ego`, `prolaznost`, `svakodnevica`, `odluka`, `odnosi`, `prisutnost`

## Kako dodati novi koan

Dodaj objekat na kraj niza u `koani.json`:

```json
{
  "id": "gg-50",
  "naslov": "Kratak naslov",
  "koan": "Sama priča, prevedena prirodnim jezikom.",
  "komentar": "Komentar (smije biti skraćen).",
  "stih": "Stih, redovi odvojeni sa \n",
  "pitanje": "Jedno kratko pitanje za meditaciju (najviše dvanaestak riječi).",
  "teme": ["sumnja", "ego"],
  "kadaPomaze": "Dvije do četiri rečenice.",
  "izvor": "Autor, djelo, broj slučaja; prevod; status autorskog prava"
}
```

Za koan iz druge zbirke uzmi novi prefiks za `id` (npr. `bk-01`).

## Kako dodati novu poruku

U `poruke.json` dodaj:

```json
{
  "id": "p-43",
  "tekst": "Jedna do tri blage rečenice.",
  "teme": ["umor"],
  "kadaPomaze": "Dvije do četiri rečenice."
}
```

Ton poruka je topao, jednostavan i bez patetike. U porukama nema terapeutskog žargona, obećanja ni dijagnoza, ne savjetuje se ništa o lijekovima i nema brojanja dana ni bodova. Sadržaj o krizi i samopovređivanju ne ide u biblioteku, jer aplikacija takve situacije obrađuje odvojeno.

## Kako pisati `kadaPomaze`

Piši onako kako bi korisnik sam opisao svoje stanje poslije meditacije: u prvom licu, svakodnevnim riječima, s konkretnim osjećajima i situacijama i bez budističkih termina. Na primjer: „Kad stalno tražim konačan odgovor i ne mogu da se smirim dok ga ne nađem. Kad mi se čini da moram razumjeti sve prije nego što krenem.“

Pravila za sve stavke:

- `id` mora biti jedinstven.
- Sva polja su obavezna.
- U tekstu nema engleskih riječi.
- Ijekavica i latinica važe svuda.
- Navodnike piši kao „…“, a unutrašnje kao ‚…‘.
