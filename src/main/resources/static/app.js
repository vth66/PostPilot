"use strict";

/* PostPilot — Oberfläche. Kommt ohne Framework aus, damit die Seite auch dann lädt,
   wenn die NAS gerade kein Internet hat. */

/**
 * Das gewählte Format ist das Format des ganzen Posts: Instagram zwingt einem Carousel das
 * Seitenverhältnis des ersten Bildes auf. Pano-Split ist deshalb kein eigenes Format mehr,
 * sondern eine Eigenschaft je Foto — so liegen ein 4:5-Opener und ein geteiltes Panorama
 * im selben Carousel.
 */
const FORMATE = {
  FEED_4_5:   { label: "4:5 Feed",   aspect: 0.8,    breite: 1080, hoehe: 1350, carousel: true },
  SQUARE_1_1: { label: "1:1",        aspect: 1.0,    breite: 1080, hoehe: 1080, carousel: true },
  STORY_9_16: { label: "9:16 Story", aspect: 0.5625, breite: 1080, hoehe: 1920, carousel: false }
};

/** Dieselben Regeln wie PanoSplitService im Server — der Parität-Test hält beide zusammen. */
const PANO = {
  MIN_ASPECT: 0.8,
  MAX_ASPECT: 1.91,
  MIN_HOEHEN_NUTZUNG: 0.5,
  MAX_BILDER: 10,
  EPS: 1e-9
};

/**
 * Hashtag-Grenzen. 30 ist Instagrams harte Obergrenze je Beitrag (Caption und Kommentare
 * zusammengezählt) — darüber nimmt Instagram den Post nicht an. 12 bis 18 ist die eigene
 * Zielgröße aus der Hashtag-Bank, also eine Empfehlung, keine technische Schranke.
 */
const HASHTAG = { ZIEL_MIN: 12, ZIEL_MAX: 18, MAX: 30 };

const state = {
  fotos: [],
  fotoId: null,
  format: "FEED_4_5",
  // Je Foto: { art: "ausschnitt" | "ganz" | "pano", anzahl: n, fuellung: "schwarz" }.
  // Fehlt ein Eintrag, gilt „ausschnitt".
  modus: {},
  ort: null,
  orte: [],
  caption: "",
  hashtags: [],
  // Fokuspunkt je Foto und Format: 0,5/0,5 ist mittig. Wird durch Ziehen verändert.
  fokus: {},
  ziehen: null,
  wischen: null,
  bank: { gruppen: [], accounts: [] },
  genres: [],
  // Gewählte Gegend („siegerland", „shanghai", …) oder "" für keinen lokalen Bezug. Steht
  // hier und nicht nur im Auswahlfeld, weil die Auswahl erst existiert, wenn die Bank
  // geladen ist — ein gespeicherter Entwurf kommt oft vorher an.
  region: "",
  vorschauIndex: 0
};

function carouselAspect() {
  return FORMATE[state.format].aspect;
}

function fokusSchluessel() {
  return state.fotoId + "|" + state.format;
}

function aktuellerFokus() {
  return fokusVon(state.fotoId);
}

function fokusVon(fotoId) {
  return state.fokus[fotoId + "|" + state.format] || { x: 0.5, y: 0.5 };
}

function setzeFokus(x, y) {
  state.fokus[fokusSchluessel()] = { x: x, y: y };
}

const $ = (id) => document.getElementById(id);

/* ---------------------------------------------------------------- Netzwerk */

async function api(pfad, optionen) {
  const antwort = await fetch(pfad, optionen);
  if (!antwort.ok) {
    const text = await antwort.text();
    throw new Error(text || ("Fehler " + antwort.status));
  }
  // Löschen und Reihenfolge antworten ohne Inhalt. Ein leerer Rumpf durch JSON.parse zu
  // schicken wirft „Unexpected end of JSON input" — der Aufrufer sähe einen Fehler,
  // obwohl alles geklappt hat.
  const text = await antwort.text();
  if (!text) return null;
  const typ = antwort.headers.get("content-type") || "";
  return typ.includes("application/json") ? JSON.parse(text) : text;
}

async function ladeFotos() {
  state.fotos = await api("/api/fotos");
  const bekannt = state.fotos.some((foto) => foto.id === state.fotoId);
  if (!bekannt) state.fotoId = state.fotos.length > 0 ? state.fotos[0].id : null;
  state.vorschauIndex = 0;
  if (state.fotoId) {
    await waehleFoto(state.fotoId);
  } else {
    zeichneThumbs();
    zeichneBuehne();
    zeichneVorschau();
  }
}

async function hochladen(dateien) {
  for (const datei of dateien) {
    const formular = new FormData();
    formular.append("datei", datei);
    try {
      const foto = await api("/api/fotos", { method: "POST", body: formular });
      state.fotoId = foto.id;
    } catch (fehler) {
      zeigeMeldung("Hochladen fehlgeschlagen: " + fehler.message, true);
    }
  }
  await ladeFotos();
}

/* ------------------------------------------------------------------ Fotos */

async function waehleFoto(id) {
  state.fotoId = id;
  zeichneThumbs();
  zeichneBuehne();
  zeichneVorschau();
  await ladeOrte();
}

function aktuellesFoto() {
  return state.fotos.find((foto) => foto.id === state.fotoId) || null;
}

function zeichneThumbs() {
  const behaelter = $("thumbs");
  behaelter.innerHTML = "";
  state.fotos.forEach((foto, index) => {
    const huelle = document.createElement("div");
    huelle.className = "thumb-wrap";

    const nummer = document.createElement("span");
    nummer.className = "thumb-nr";
    nummer.textContent = index + 1;

    const bild = document.createElement("img");
    bild.src = "/api/fotos/" + foto.id + "/original";
    bild.alt = foto.dateiname;
    bild.title = foto.breite + " × " + foto.hoehe + " px";
    if (foto.id === state.fotoId) bild.classList.add("is-active");
    bild.addEventListener("click", () => waehleFoto(foto.id));

    const pfeile = document.createElement("div");
    pfeile.className = "thumb-pfeile";
    const hoch = document.createElement("button");
    hoch.type = "button";
    hoch.textContent = "▲";
    hoch.title = "Nach vorne";
    hoch.disabled = index === 0;
    hoch.addEventListener("click", () => verschiebeFoto(index, index - 1));
    const runter = document.createElement("button");
    runter.type = "button";
    runter.textContent = "▼";
    runter.title = "Nach hinten";
    runter.disabled = index === state.fotos.length - 1;
    runter.addEventListener("click", () => verschiebeFoto(index, index + 1));
    pfeile.append(hoch, runter);

    const weg = document.createElement("button");
    weg.type = "button";
    weg.className = "thumb-weg";
    weg.textContent = "×";
    weg.title = "Dieses Foto aus dem Post entfernen";
    weg.addEventListener("click", () => loescheFoto(foto.id));

    const kopie = document.createElement("button");
    kopie.type = "button";
    kopie.className = "thumb-kopie";
    kopie.textContent = "⧉";
    kopie.title = "Kopie anlegen — etwa einmal ganz und einmal als Pano-Split";
    kopie.addEventListener("click", () => dupliziereFoto(foto.id));

    huelle.append(nummer, bild, pfeile, kopie, weg);

    // Sichtbar machen, wie dieses Foto ins Carousel geht.
    const art = wirksameArt(foto);
    if (art !== "ausschnitt") {
      const marke = document.createElement("span");
      marke.className = "thumb-marke";
      marke.textContent = art === "pano" ? "Pano " + modusVon(foto.id).anzahl : "Ganz";
      marke.title = art === "pano"
        ? "Geht als " + modusVon(foto.id).anzahl + " Ausschnitte ins Carousel"
        : "Geht vollständig ins Carousel, mit Freiraum";
      huelle.appendChild(marke);
    }

    behaelter.appendChild(huelle);
  });
}

/**
 * Legt eine zweite Fassung desselben Fotos direkt dahinter an. Damit lässt sich dasselbe
 * Panorama erst ganz zeigen und gleich danach als Pano-Split in groß.
 */
async function dupliziereFoto(id) {
  let kopie;
  try {
    kopie = await api("/api/fotos/" + id + "/duplizieren", { method: "POST" });
  } catch (fehler) {
    zeigeMeldung("Kopie fehlgeschlagen: " + fehler.message, true);
    return;
  }
  // Die Kopie übernimmt Zuschnitt und Einstellung des Originals — sonst müsste man
  // den Ausschnitt ein zweites Mal suchen.
  const vorlage = modusVon(id);
  state.modus[kopie.id] = { art: vorlage.art, anzahl: vorlage.anzahl, fuellung: vorlage.fuellung };
  for (const [schluessel, wert] of Object.entries(state.fokus)) {
    if (schluessel.startsWith(id + "|")) {
      state.fokus[kopie.id + schluessel.slice(id.length)] = { x: wert.x, y: wert.y };
    }
  }
  state.fotoId = kopie.id;
  await ladeFotos();
  zeigeMeldung("Kopie angelegt — sie steht direkt hinter dem Original und lässt sich "
    + "unabhängig einstellen.");
}

/** Entfernt ein Foto aus dem Post; die Datei im Datenordner wird mitgelöscht. */
async function loescheFoto(id) {
  try {
    await api("/api/fotos/" + id, { method: "DELETE" });
  } catch (fehler) {
    zeigeMeldung("Foto konnte nicht entfernt werden: " + fehler.message, true);
    return;
  }
  delete state.modus[id];
  for (const schluessel of Object.keys(state.fokus)) {
    if (schluessel.startsWith(id + "|")) delete state.fokus[schluessel];
  }
  if (state.fotoId === id) state.fotoId = null;
  await ladeFotos();
}

/** Räumt alles weg und fängt mit einem leeren Post an. */
async function neuerPost() {
  if (state.fotos.length > 0
      && !window.confirm("Alle " + state.fotos.length + " Fotos aus dem Post entfernen und "
        + "Caption, Hashtags und Ort zurücksetzen?")) {
    return;
  }
  try {
    await api("/api/fotos", { method: "DELETE" });
  } catch (fehler) {
    zeigeMeldung("Aufräumen fehlgeschlagen: " + fehler.message, true);
    return;
  }
  setzeArbeitsbereichZurueck();
  entwurfVergessen();
  await ladeFotos();
  await ladeOrte();
  zeigeMeldung("Alles zurückgesetzt — der nächste Post kann beginnen.");
}

/** Leert Auswahl, Text, Hashtags und Ort — die Fotodateien selbst sind da schon weg. */
function setzeArbeitsbereichZurueck() {
  state.fotoId = null;
  state.modus = {};
  state.fokus = {};
  state.caption = "";
  state.hashtags = [];
  state.ort = null;
  state.genres = [];
  state.region = "";
  state.vorschauIndex = 0;
  $("caption").value = "";
  $("hashtag-bloecke").innerHTML = "";
  $("hashtag-hinweis").textContent = "";
  $("ort-suche").value = "";
  zeichneGenreChips();
  zeichneRegionen();
  zeichneHashtags();
}

/** Tauscht zwei Fotos in der Reihenfolge und merkt sie sich auf dem Server. */
async function verschiebeFoto(von, nach) {
  if (nach < 0 || nach >= state.fotos.length) return;
  const liste = state.fotos.slice();
  const [bewegt] = liste.splice(von, 1);
  liste.splice(nach, 0, bewegt);
  state.fotos = liste;
  state.vorschauIndex = 0;
  zeichneThumbs();
  zeichneVorschau();
  try {
    await api("/api/fotos/reihenfolge", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids: liste.map((f) => f.id) })
    });
  } catch (fehler) {
    console.warn("Reihenfolge nicht gespeichert:", fehler.message);
  }
}

/* -------------------------------------------------------------- Zuschnitt */

/* --- Pano-Regeln, deckungsgleich mit PanoSplitService ------------------- */

/** Erlaubter Bereich für die Anzahl Ausschnitte bei diesem Foto und Zielformat. */
function panoGrenzen(breite, hoehe, zielAspect) {
  const aus = { geeignet: false, min: 0, max: 0 };
  if (zielAspect < PANO.MIN_ASPECT - PANO.EPS || zielAspect > PANO.MAX_ASPECT + PANO.EPS) return aus;
  if (breite / hoehe <= 1 + PANO.EPS) return aus;

  // Mindestens so viele Ausschnitte, dass das Band in die Bildhöhe passt — nur dann
  // trifft jeder Ausschnitt das Zielformat genau.
  aus.min = Math.max(2, Math.ceil(breite / (zielAspect * hoehe) - PANO.EPS));
  aus.max = Math.min(PANO.MAX_BILDER,
    Math.floor(breite / (zielAspect * hoehe * PANO.MIN_HOEHEN_NUTZUNG) + PANO.EPS));
  aus.geeignet = aus.min <= aus.max;
  return aus;
}

/** Lage und Höhe des waagerechten Bandes sowie die Schnittkanten in Bildpixeln. */
function berechneBand(breite, hoehe, anzahl, zielAspect, fokusY) {
  const sliceBreite = Math.round(breite / anzahl);
  const bandHoehe = Math.min(hoehe, Math.round(sliceBreite / zielAspect));
  const bandOben = klemme(Math.round(fokusY * hoehe - bandHoehe / 2), 0, hoehe - bandHoehe);

  const kanten = [];
  for (let i = 0; i <= anzahl; i++) {
    kanten.push(i === anzahl ? breite : Math.round((breite * i) / anzahl));
  }
  return { oben: bandOben, hoehe: bandHoehe, kanten: kanten };
}

function klemme(wert, min, max) {
  return Math.max(min, Math.min(max, wert));
}

function modusVon(fotoId) {
  const gespeichert = state.modus[fotoId];
  return {
    art: gespeichert && gespeichert.art ? gespeichert.art : "ausschnitt",
    anzahl: gespeichert ? gespeichert.anzahl || 0 : 0,
    fuellung: gespeichert && gespeichert.fuellung ? gespeichert.fuellung : "schwarz"
  };
}

/**
 * Wie dieses Foto tatsächlich ins Carousel geht. Weicht von der gespeicherten Wahl ab,
 * wenn sie im aktuellen Format nicht möglich ist — die Wahl selbst bleibt dabei erhalten.
 */
function wirksameArt(foto) {
  if (!foto) return "ausschnitt";
  const modus = modusVon(foto.id);
  if (modus.art === "pano") {
    if (!FORMATE[state.format].carousel) return "ausschnitt";
    return panoGrenzen(foto.breite, foto.hoehe, carouselAspect()).geeignet ? "pano" : "ausschnitt";
  }
  return modus.art;
}

function istPanoFoto(foto) {
  return wirksameArt(foto) === "pano";
}

function istGanzesFoto(foto) {
  return wirksameArt(foto) === "ganz";
}

/** Schaltet das gewählte Foto zwischen Ausschnitt, ganzem Bild und Pano-Split um. */
function setzeModus(art) {
  const foto = aktuellesFoto();
  if (!foto) return;
  const vorher = modusVon(foto.id);
  const grenzen = panoGrenzen(foto.breite, foto.hoehe, carouselAspect());
  if (art === "pano" && !grenzen.geeignet) return;

  const anzahl = grenzen.geeignet
    ? klemme(vorher.anzahl || Math.max(grenzen.min, Math.min(grenzen.max, 3)), grenzen.min, grenzen.max)
    : vorher.anzahl;
  state.modus[foto.id] = { art: art, anzahl: anzahl, fuellung: vorher.fuellung };
  state.vorschauIndex = 0;
  zeichneThumbs();
  zeichneBuehne();
  zeichneVorschau();
}

function setzeFuellung(fuellung) {
  const foto = aktuellesFoto();
  if (!foto) return;
  const vorher = modusVon(foto.id);
  state.modus[foto.id] = { art: vorher.art, anzahl: vorher.anzahl, fuellung: fuellung };
  zeichneBuehne();
  zeichneVorschau();
}

function aenderePanoAnzahl(schritt) {
  const foto = aktuellesFoto();
  if (!istPanoFoto(foto)) return;
  const grenzen = panoGrenzen(foto.breite, foto.hoehe, carouselAspect());
  const vorher = modusVon(foto.id);
  const neu = vorher.anzahl + schritt;
  if (neu < grenzen.min || neu > grenzen.max) return;
  state.modus[foto.id] = { art: "pano", anzahl: neu, fuellung: vorher.fuellung };
  state.vorschauIndex = 0;
  zeichneThumbs();
  zeichneBuehne();
  zeichneVorschau();
}

function setFormat(format) {
  state.format = format;
  state.vorschauIndex = 0;
  for (const button of document.querySelectorAll(".format")) {
    button.classList.toggle("is-active", button.dataset.format === format);
  }
  // Nach einem Formatwechsel passt die alte Anzahl Ausschnitte oft nicht mehr. Die Wahl
  // bleibt aber stehen, wenn das neue Format gar keinen Pano-Split kann — wer zurückwechselt,
  // findet seine Einteilung wieder vor; istPanoFoto sorgt dafür, dass sie derweil ruht.
  for (const foto of state.fotos) {
    const modus = modusVon(foto.id);
    if (modus.art !== "pano") continue;
    const grenzen = panoGrenzen(foto.breite, foto.hoehe, carouselAspect());
    if (grenzen.geeignet) {
      state.modus[foto.id] = {
        art: "pano",
        anzahl: klemme(modus.anzahl, grenzen.min, grenzen.max),
        fuellung: modus.fuellung
      };
    }
  }
  zeichneThumbs();
  zeichneBuehne();
  zeichneVorschau();
}

/**
 * Größter Ausschnitt mit dem Zielverhältnis, positioniert über den Fokuspunkt.
 * Spiegelt CropService.computeCrop im Server — beide müssen dasselbe rechnen,
 * sonst zeigt die Maske etwas anderes als später exportiert wird.
 */
function berechneCrop(breite, hoehe, aspect, fokusX, fokusY) {
  let cropBreite, cropHoehe;
  if (breite / hoehe > aspect) {
    cropHoehe = hoehe;
    cropBreite = Math.round(hoehe * aspect);
  } else {
    cropBreite = breite;
    cropHoehe = Math.round(breite / aspect);
  }
  cropBreite = Math.min(cropBreite, breite);
  cropHoehe = Math.min(cropHoehe, hoehe);

  return {
    x: klemme(Math.round(fokusX * breite - cropBreite / 2), 0, breite - cropBreite),
    y: klemme(Math.round(fokusY * hoehe - cropHoehe / 2), 0, hoehe - cropHoehe),
    breite: cropBreite,
    hoehe: cropHoehe
  };
}

function zeichneBuehne() {
  const foto = aktuellesFoto();
  const bild = $("stage-image");
  const overlay = $("stage-overlay");

  $("stage-leer").classList.toggle("hidden", !!foto);
  overlay.innerHTML = "";

  if (!foto) {
    bild.removeAttribute("src");
    $("stage-info").textContent = "";
    $("modus-zeile").classList.add("hidden");
    $("pano-hinweis").classList.add("hidden");
    $("stage").classList.remove("is-draggable");
    $("crop-reset").classList.add("hidden");
    zeichneCarouselZeile();
    return;
  }

  zeichneModusZeile(foto);

  // Beim ganzen Bild zeigt die Bühne das fertig gerechnete Ergebnis vom Server — samt
  // Freiraum und weichem Hintergrund. Sonst das Original, auf das die Maske gelegt wird.
  const modus = modusVon(foto.id);
  bild.src = istGanzesFoto(foto)
    ? "/api/fotos/" + foto.id + "/ganz?seitenverhaeltnis=" + carouselAspect()
      + "&fuellung=" + encodeURIComponent(modus.fuellung)
    : "/api/fotos/" + foto.id + "/original";
  bild.onload = () => zeichneOverlay(foto);
  if (bild.complete) zeichneOverlay(foto);

  zeichneCarouselZeile();
}

/** Die Zeile unter der Bühne: Einzelbild oder Pano-Split für genau dieses Foto. */
function zeichneModusZeile(foto) {
  const grenzen = panoGrenzen(foto.breite, foto.hoehe, carouselAspect());
  const art = wirksameArt(foto);
  const modus = modusVon(foto.id);

  $("modus-zeile").classList.remove("hidden");
  $("modus-ausschnitt").classList.toggle("is-active", art === "ausschnitt");
  $("modus-ganz").classList.toggle("is-active", art === "ganz");
  $("modus-pano").classList.toggle("is-active", art === "pano");
  $("modus-pano").disabled = !grenzen.geeignet || !FORMATE[state.format].carousel;

  $("pano-controls").classList.toggle("hidden", art !== "pano");
  $("fuellung-wahl").classList.toggle("hidden", art !== "ganz");
  $("fuellung").value = modus.fuellung;

  if (art === "pano") {
    $("pano-anzahl").textContent = modus.anzahl;
    $("pano-grenzen").textContent = "erlaubt sind " + grenzen.min + " bis " + grenzen.max;
    $("pano-minus").disabled = modus.anzahl <= grenzen.min;
    $("pano-plus").disabled = modus.anzahl >= grenzen.max;
  }

  const hinweis = $("pano-hinweis");
  const text = panoHinweis(foto, grenzen);
  hinweis.textContent = text;
  hinweis.classList.toggle("hidden", !text);
}

/** Sagt im Klartext, warum ein Pano-Split hier geht oder eben nicht. */
function panoHinweis(foto, grenzen) {
  const quelle = foto.breite / foto.hoehe;
  const format = FORMATE[state.format];

  if (istGanzesFoto(foto)) {
    return "Das ganze Foto, nichts abgeschnitten — der Freiraum füllt den Rest. Als Auftakt "
      + "gedacht: Kopiere das Foto (⧉ an der Miniatur) und stelle die Kopie auf Pano-Split.";
  }
  if (!format.carousel) {
    return "Eine Story ist ein Einzelbild. Für ein Carousel mit Pano-Split oben 4:5 oder 1:1 wählen.";
  }
  if (quelle <= 1) {
    return "Für einen Pano-Split muss das Foto breiter als hoch sein — dieses ist "
      + foto.breite + " × " + foto.hoehe + " px.";
  }
  if (!grenzen.geeignet) {
    const gebraucht = Math.ceil(foto.breite / (format.aspect * foto.hoehe));
    return gebraucht > PANO.MAX_BILDER
      ? "So breit, dass es im Format " + format.label + " " + gebraucht
        + " Ausschnitte bräuchte — Instagram nimmt nur " + PANO.MAX_BILDER + "."
      : "Im Format " + format.label + " bliebe von diesem Foto weniger als die halbe Bildhöhe übrig.";
  }
  if (istPanoFoto(foto)) return "";
  return quelle >= 1.6
    ? "Sehr breites Foto — als Pano-Split werden daraus " + grenzen.min + " bis " + grenzen.max
      + " nahtlose Bilder im Format " + format.label + "."
    : "";
}

/** Zeigt, was am Ende tatsächlich im Carousel landet — Opener, Ausschnitte, Gesamtzahl. */
function zeichneCarouselZeile() {
  const zeile = $("carousel-info");
  const eintraege = carouselEintraege();
  if (eintraege.length === 0) {
    zeile.textContent = "";
    setzeExportKnoepfe(false);
    return;
  }

  const format = FORMATE[state.format];
  if (!format.carousel) {
    zeile.textContent = "Story · 1 Bild · " + format.breite + " × " + format.hoehe + " px";
    setzeExportKnoepfe(true);
    return;
  }

  const teile = state.fotos.map((foto, index) => {
    const art = wirksameArt(foto);
    const was = art === "pano" ? modusVon(foto.id).anzahl + " Ausschnitte"
      : art === "ganz" ? "ganz" : "Ausschnitt";
    return "Foto " + (index + 1) + ": " + was;
  });

  const zuViel = eintraege.length > PANO.MAX_BILDER;
  zeile.textContent = "Carousel: " + eintraege.length + " Bild(er) im Format " + format.label
    + " · " + teile.join(" · ")
    + (zuViel ? " — Instagram nimmt nur " + PANO.MAX_BILDER + ". Ausschnitte reduzieren oder ein Foto entfernen."
              : "");
  zeile.classList.toggle("warnung", zuViel);
  setzeExportKnoepfe(!zuViel);
}

function setzeExportKnoepfe(moeglich) {
  $("export-button").disabled = !moeglich;
  $("ablegen-button").disabled = !moeglich;
}

function zeichneOverlay(foto) {
  const bild = $("stage-image");
  const overlay = $("stage-overlay");
  overlay.innerHTML = "";

  const buehne = $("stage").getBoundingClientRect();
  const rahmen = bild.getBoundingClientRect();
  const links = rahmen.left - buehne.left;
  const oben = rahmen.top - buehne.top;
  const skalierung = rahmen.width / foto.breite;

  if (istGanzesFoto(foto)) {
    // Nichts zu maskieren: Die Bühne zeigt bereits das fertige Bild samt Freiraum.
    const modus = modusVon(foto.id);
    const namen = { schwarz: "schwarzem", weiss: "weißem", weich: "weichem" };
    $("stage-info").textContent = "Ganzes Foto im Format " + FORMATE[state.format].label
      + " · " + FORMATE[state.format].breite + " × " + FORMATE[state.format].hoehe
      + " px · Freiraum mit " + (namen[modus.fuellung] || "schwarzem") + " Hintergrund";
    $("crop-reset").classList.add("hidden");
    $("stage").classList.remove("is-draggable");
    return;
  }

  if (istPanoFoto(foto)) {
    zeichnePanoOverlay(overlay, foto, links, oben, rahmen, skalierung);
    return;
  }

  const fokus = aktuellerFokus();
  const crop = berechneCrop(foto.breite, foto.hoehe, FORMATE[state.format].aspect, fokus.x, fokus.y);
  const x = links + crop.x * skalierung;
  const y = oben + crop.y * skalierung;
  const b = crop.breite * skalierung;
  const h = crop.hoehe * skalierung;

  const maske = (l, t, w, hh) => {
    const div = document.createElement("div");
    div.className = "mask";
    Object.assign(div.style, { left: l + "px", top: t + "px", width: w + "px", height: hh + "px" });
    overlay.appendChild(div);
  };
  maske(links, oben, rahmen.width, y - oben);
  maske(links, y + h, rahmen.width, oben + rahmen.height - (y + h));
  maske(links, y, x - links, h);
  maske(x + b, y, links + rahmen.width - (x + b), h);

  for (let i = 1; i <= 2; i++) {
    const senkrecht = document.createElement("div");
    senkrecht.className = "grid-line";
    Object.assign(senkrecht.style,
      { left: (x + b * i / 3) + "px", top: y + "px", width: "1px", height: h + "px" });
    overlay.appendChild(senkrecht);

    const waagerecht = document.createElement("div");
    waagerecht.className = "grid-line";
    Object.assign(waagerecht.style,
      { left: x + "px", top: (y + h * i / 3) + "px", width: b + "px", height: "1px" });
    overlay.appendChild(waagerecht);
  }

  const ecke = (l, t, borders) => {
    const div = document.createElement("div");
    div.className = "corner";
    Object.assign(div.style, { left: l + "px", top: t + "px" }, borders);
    overlay.appendChild(div);
  };
  ecke(x, y, { borderLeftWidth: "2px", borderTopWidth: "2px" });
  ecke(x + b - 14, y, { borderRightWidth: "2px", borderTopWidth: "2px" });
  ecke(x, y + h - 14, { borderLeftWidth: "2px", borderBottomWidth: "2px" });
  ecke(x + b - 14, y + h - 14, { borderRightWidth: "2px", borderBottomWidth: "2px" });

  const format = FORMATE[state.format];
  const verschiebbar = crop.breite < foto.breite || crop.hoehe < foto.hoehe;
  $("stage-info").textContent = format.label + " · Ausschnitt " + crop.breite + " × " + crop.hoehe
    + " px · Ausgabe " + format.breite + " × " + format.hoehe + " px"
    + (verschiebbar ? " · zum Verschieben im Bild ziehen" : " · füllt das Bild komplett aus");
  $("crop-reset").classList.toggle("hidden", !verschiebbar);
  $("stage").classList.toggle("is-draggable", verschiebbar);
}

/**
 * Verschiebt den Ausschnitt um eine Mausbewegung in Bildschirmpixeln.
 * Beim Pano-Split lässt sich nur das Band senkrecht verschieben — waagerecht ist durch
 * die Schnittkanten alles festgelegt.
 */
function verschiebeAusschnitt(dxBildschirm, dyBildschirm, startFokus) {
  const foto = aktuellesFoto();
  const bild = $("stage-image");
  if (!foto || !bild.getBoundingClientRect().width) return;

  const skalierung = bild.getBoundingClientRect().width / foto.breite;
  const nurSenkrecht = istPanoFoto(foto);
  const neuX = nurSenkrecht ? startFokus.x : startFokus.x + (dxBildschirm / skalierung) / foto.breite;
  const neuY = startFokus.y + (dyBildschirm / skalierung) / foto.hoehe;
  setzeFokus(klemme(neuX, 0, 1), klemme(neuY, 0, 1));
  zeichneOverlay(foto);
}

/** Das Band mit den Schnittkanten: oben und unten abgedunkelt, was wegfällt. */
function zeichnePanoOverlay(overlay, foto, links, oben, rahmen, skalierung) {
  const anzahl = modusVon(foto.id).anzahl;
  const aspekt = carouselAspect();
  const band = berechneBand(foto.breite, foto.hoehe, anzahl, aspekt, aktuellerFokus().y);

  const y = oben + band.oben * skalierung;
  const h = band.hoehe * skalierung;

  const maske = (t, hh) => {
    if (hh <= 0.5) return;
    const div = document.createElement("div");
    div.className = "mask";
    Object.assign(div.style,
      { left: links + "px", top: t + "px", width: rahmen.width + "px", height: hh + "px" });
    overlay.appendChild(div);
  };
  maske(oben, y - oben);
  maske(y + h, oben + rahmen.height - (y + h));

  for (let i = 0; i < anzahl; i++) {
    const x = links + band.kanten[i] * skalierung;
    if (i > 0) {
      const linie = document.createElement("div");
      linie.className = "divider";
      Object.assign(linie.style, { left: x + "px", top: y + "px", height: h + "px" });
      overlay.appendChild(linie);
    }
    const chip = document.createElement("div");
    chip.className = "slice-chip";
    chip.textContent = i + 1;
    Object.assign(chip.style, { left: (x + 8) + "px", top: (y + 8) + "px" });
    overlay.appendChild(chip);
  }

  const rand = (t) => {
    const div = document.createElement("div");
    div.className = "grid-line";
    Object.assign(div.style,
      { left: links + "px", top: t + "px", width: rahmen.width + "px", height: "2px" });
    overlay.appendChild(div);
  };
  rand(y);
  rand(y + h - 2);

  const sliceBreite = band.kanten[1] - band.kanten[0];
  const nutzung = Math.round((band.hoehe / foto.hoehe) * 100);
  $("stage-info").textContent = anzahl + " nahtlose Ausschnitte · je " + sliceBreite + " × "
    + band.hoehe + " px · " + FORMATE[state.format].label + " · nutzt " + nutzung
    + " % der Bildhöhe · zum Verschieben senkrecht ziehen";
  $("crop-reset").classList.remove("hidden");
  $("stage").classList.add("is-draggable");
}

/* --------------------------------------------------------------- Vorschau */

/**
 * Die Bilder des Posts in der Reihenfolge, in der Instagram sie zeigt: ein einzeln
 * eingestelltes Foto gibt einen Eintrag, ein Pano-Split so viele Einträge wie Ausschnitte.
 */
function carouselEintraege() {
  const aspekt = carouselAspect();
  if (!FORMATE[state.format].carousel) {
    // Eine Story ist ein einzelnes Bild — aber auch dort kann das ganze Foto hinein,
    // mit Freiraum oben und unten.
    const foto = aktuellesFoto();
    if (!foto) return [];
    const fokus = fokusVon(foto.id);
    const modus = modusVon(foto.id);
    const gemeinsam = { foto: foto, fokusX: fokus.x, fokusY: fokus.y, aspekt: aspekt };
    return [istGanzesFoto(foto)
      ? Object.assign({}, gemeinsam, { art: "ganz", fuellung: modus.fuellung })
      : Object.assign({}, gemeinsam, { art: "ausschnitt" })];
  }

  const eintraege = [];
  for (const foto of state.fotos) {
    const fokus = fokusVon(foto.id);
    const modus = modusVon(foto.id);
    const art = wirksameArt(foto);
    const gemeinsam = { foto: foto, fokusX: fokus.x, fokusY: fokus.y, aspekt: aspekt };

    if (art === "pano") {
      for (let i = 1; i <= modus.anzahl; i++) {
        eintraege.push(Object.assign({}, gemeinsam,
          { art: "pano", anzahl: modus.anzahl, sliceIndex: i }));
      }
    } else if (art === "ganz") {
      eintraege.push(Object.assign({}, gemeinsam, { art: "ganz", fuellung: modus.fuellung }));
    } else {
      eintraege.push(Object.assign({}, gemeinsam, { art: "ausschnitt" }));
    }
  }
  return eintraege;
}

function eintragBildUrl(eintrag) {
  const basis = "/api/fotos/" + eintrag.foto.id;
  if (eintrag.art === "pano") {
    return basis + "/pano/" + eintrag.sliceIndex + "?anzahl=" + eintrag.anzahl
      + "&zielAspect=" + eintrag.aspekt + "&fokusY=" + eintrag.fokusY.toFixed(4);
  }
  if (eintrag.art === "ganz") {
    return basis + "/ganz?seitenverhaeltnis=" + eintrag.aspekt
      + "&fuellung=" + encodeURIComponent(eintrag.fuellung);
  }
  return basis + "/crop?seitenverhaeltnis=" + eintrag.aspekt
    + "&fokusX=" + eintrag.fokusX.toFixed(4) + "&fokusY=" + eintrag.fokusY.toFixed(4);
}

function zeichneVorschau() {
  const dots = $("preview-dots");

  $("preview-ort").textContent = state.ort ? state.ort.name : "";
  zeichneVorschauText();
  zeichneCarouselZeile();

  const eintraege = carouselEintraege();
  // Der Bildrahmen bekommt das Seitenverhältnis des Posts fest zugewiesen. Sonst springt
  // die Vorschau beim Nachladen kurz auf die Maße des alten Bildes.
  $("post-image").style.aspectRatio = String(carouselAspect());

  // Eine Story hat keine Feed-Hülle: kein Herz, kein Kommentarsymbol, keine Caption
  // darunter. Was dort oben und unten liegt, zeigen stattdessen die schraffierten Bänder.
  $("screen").classList.toggle("ist-story", !FORMATE[state.format].carousel);

  if (eintraege.length === 0) {
    $("preview-spur").innerHTML = "";
    $("post-image").classList.remove("kann-wischen");
    dots.classList.add("hidden");
    $("preview-zaehler").classList.add("hidden");
    $("preview-info").textContent = "";
    $("preview-groesse").textContent = "";
    $("preview-hinweis").textContent = "";
    zeichnePfeile(0);
    return;
  }

  if (state.vorschauIndex >= eintraege.length) state.vorschauIndex = 0;
  const eintrag = eintraege[state.vorschauIndex];

  fuelleSpur(eintraege);
  setzeSpur(state.vorschauIndex, 0, false);
  $("post-image").classList.toggle("kann-wischen", eintraege.length > 1);

  zeichnePunkte(eintraege.length, state.vorschauIndex, (i) => {
    state.vorschauIndex = i;
    zeichneVorschau();
  });
  dots.classList.toggle("hidden", eintraege.length < 2);
  const zaehler = $("preview-zaehler");
  zaehler.textContent = (state.vorschauIndex + 1) + "/" + eintraege.length;
  zaehler.classList.toggle("hidden", eintraege.length < 2);
  zeichnePfeile(eintraege.length);
  zeichneSafeArea();

  const format = FORMATE[state.format];
  const nummer = state.fotos.indexOf(eintrag.foto) + 1;
  const herkunft = eintrag.art === "pano"
    ? "Foto " + nummer + ", Ausschnitt " + eintrag.sliceIndex + " von " + eintrag.anzahl
    : eintrag.art === "ganz" ? "Foto " + nummer + ", ganz" : "Foto " + nummer;
  const eigenes = eintrag.foto.breite / eintrag.foto.hoehe;
  const beschnitten = eintrag.art === "ausschnitt" && Math.abs(eigenes - eintrag.aspekt) > 0.02;

  $("preview-info").textContent =
    (eintraege.length > 1 ? "Bild " + (state.vorschauIndex + 1) + " von " + eintraege.length + " · " : "")
    + herkunft + " · " + format.label
    + (beschnitten ? " · aus " + eigenes.toFixed(2) + " beschnitten" : "");

  $("preview-groesse").textContent = format.breite + " × " + format.hoehe + " px";
  $("preview-hinweis").textContent = vorschauHinweis(eintraege.length);
}

/**
 * Sagt, worauf bei dieser Vorschau Verlass ist und worauf nicht. Das Bild ist die exportierte
 * Datei, also verlässlich; wie viel Caption Instagram zeigt, hängt dagegen vom Gerät ab.
 */
function vorschauHinweis(anzahl) {
  if (!FORMATE[state.format].carousel) {
    return "Story: Die schraffierten Bänder (oben 14 %, unten 20 %) verdeckt Instagram mit "
      + "Profilzeile, Antwortfeld und Musikleiste.";
  }
  if (anzahl > PANO.MAX_BILDER) {
    return "Über Metricool lassen sich höchstens " + PANO.MAX_BILDER + " Bilder je Carousel "
      + "planen — in der App selbst wären es inzwischen 20.";
  }
  return anzahl > 1
    ? "Alle " + anzahl + " Bilder haben dasselbe Format — Instagram zwingt dem Carousel das "
      + "Seitenverhältnis des ersten Bildes auf."
    : "Gezeigt wird die Datei, die exportiert wird — kein nachgebauter Zuschnitt.";
}

/**
 * Baut die Spur neu, aber nur wenn sich wirklich etwas geändert hat — sonst würden beim
 * Wischen alle Bilder neu geladen und die Vorschau flackerte.
 */
function fuelleSpur(eintraege) {
  const spur = $("preview-spur");
  const adressen = eintraege.map(eintragBildUrl);
  if (spur.dataset.adressen === adressen.join("|")) return;
  spur.dataset.adressen = adressen.join("|");

  spur.innerHTML = "";
  for (let i = 0; i < adressen.length; i++) {
    const bild = document.createElement("img");
    bild.className = "spur-bild";
    bild.draggable = false;
    bild.alt = "Bild " + (i + 1) + " von " + adressen.length;
    // Der Zeitstempel erzwingt ein Neuladen, sonst zeigte der Browser den alten Zuschnitt.
    bild.src = adressen[i] + "&t=" + Date.now();
    spur.appendChild(bild);
  }
}

/** Schiebt die Spur auf das Bild {@code index}, optional um {@code versatz} Pixel versetzt. */
function setzeSpur(index, versatz, sanft) {
  const spur = $("preview-spur");
  const breite = $("post-image").getBoundingClientRect().width || 1;
  spur.classList.toggle("sanft", !!sanft);
  spur.style.transform = "translateX(" + (-index * breite + versatz) + "px)";
}

function zeichnePunkte(anzahl, aktiv, beiKlick) {
  const dots = $("preview-dots");
  dots.innerHTML = "";
  for (let i = 0; i < anzahl; i++) {
    const punkt = document.createElement("span");
    if (i === aktiv) punkt.classList.add("is-active");
    punkt.addEventListener("click", () => beiKlick(i));
    dots.appendChild(punkt);
  }
}

/** Vor und zurück im Carousel — wie das Wischen in der App. */
function zeichnePfeile(anzahl) {
  const flaeche = $("post-image");
  flaeche.querySelectorAll(".swipe").forEach((el) => el.remove());
  if (anzahl < 2) return;

  const macheKnopf = (richtung, zeichen) => {
    const knopf = document.createElement("button");
    knopf.type = "button";
    knopf.className = "swipe swipe-" + richtung;
    knopf.textContent = zeichen;
    knopf.addEventListener("click", () => blaettere(richtung === "vor" ? 1 : -1, anzahl));
    flaeche.appendChild(knopf);
  };
  macheKnopf("zurueck", "‹");
  macheKnopf("vor", "›");
}

function blaettere(schritt, anzahl) {
  state.vorschauIndex = klemme(state.vorschauIndex + schritt, 0, anzahl - 1);
  zeichneVorschau();
}

/**
 * Bei Stories verdeckt die Instagram-Oberfläche oben rund 14 % (Profilzeile) und unten
 * rund 20 % (Caption, Link, Antwortfeld, Musikleiste) — unten also deutlich mehr.
 */
function zeichneSafeArea() {
  const flaeche = $("post-image");
  flaeche.querySelectorAll(".safe-area").forEach((el) => el.remove());
  if (state.format !== "STORY_9_16") return;

  for (const seite of ["oben", "unten"]) {
    const band = document.createElement("div");
    band.className = "safe-area safe-" + seite;
    band.title = seite === "oben"
      ? "Rund 14 % — hier liegen Profilbild und Name"
      : "Rund 20 % — hier liegen Caption, Antwortfeld und Musikleiste";
    flaeche.appendChild(band);
  }
}

/**
 * Caption wie im Feed: Der Kontoname steht in derselben Zeile davor, danach zeigt Instagram
 * rund 125 Zeichen und bricht mit „… mehr" ab. Die Hashtags zählen mit, deshalb stehen sie
 * hier im selben Text. Die 125 sind ein Richtwert — je nach Gerät und Zeilenumbruch kann
 * Instagram schon etwas früher abschneiden.
 */
function zeichneVorschauText() {
  const caption = (state.caption || "").trim();
  const tags = state.hashtags.join(" ");
  const voll = [caption, tags].filter(Boolean).join("\n\n");

  const zeile = $("preview-caption");
  zeile.textContent = "";
  const name = document.createElement("span");
  name.className = "caption-name";
  name.textContent = "vothography";

  if (!voll) {
    zeile.append(name, document.createTextNode("Noch keine Caption"));
    $("preview-tags").textContent = "";
    return;
  }

  const GRENZE = 125;
  if (voll.length <= GRENZE) {
    zeile.append(name, document.createTextNode(voll));
    $("preview-tags").textContent = "";
    return;
  }

  let schnitt = voll.slice(0, GRENZE);
  const letztesLeerzeichen = schnitt.lastIndexOf(" ");
  if (letztesLeerzeichen > GRENZE - 25) schnitt = schnitt.slice(0, letztesLeerzeichen);
  const mehr = document.createElement("span");
  mehr.className = "caption-mehr";
  mehr.textContent = "… mehr";
  zeile.append(name, document.createTextNode(schnitt), mehr);
  $("preview-tags").textContent = (voll.length - schnitt.length)
    + " Zeichen sind erst nach dem Antippen von „mehr“ zu sehen";
}

/* -------------------------------------------------------------- Stammorte */

async function ladeOrte() {
  const suche = $("ort-suche").value.trim();
  const parameter = new URLSearchParams();
  if (suche) parameter.set("q", suche);
  if (state.fotoId) parameter.set("fotoId", state.fotoId);

  try {
    state.orte = await api("/api/stammorte?" + parameter.toString());
  } catch (fehler) {
    state.orte = [];
  }
  $("ort-liste-kopf").textContent = suche
    ? "TREFFER IN DEINER LISTE"
    : "NÄCHSTE STAMMORTE · GPS AUS DEM FOTO";
  zeichneOrte();
}

function zeichneOrte() {
  const liste = $("ort-liste");
  liste.innerHTML = "";

  if (state.orte.length === 0) {
    const leer = document.createElement("p");
    leer.className = "hint";
    leer.textContent = "Noch kein Stammort mit diesem Namen — unten anlegen.";
    liste.appendChild(leer);
    return;
  }

  for (const ort of state.orte) {
    const zeile = document.createElement("div");
    zeile.className = "ort-zeile" + (state.ort && state.ort.id === ort.id ? " is-active" : "");

    const text = document.createElement("div");
    const name = document.createElement("div");
    name.className = "ort-name";
    name.textContent = ort.name;
    const meta = document.createElement("div");
    meta.className = "ort-meta";
    meta.textContent = ort.kategorie + (ort.entfernungKm != null
      ? " · " + ort.entfernungKm.toFixed(1) + " km vom Aufnahmeort" : "");
    text.append(name, meta);

    zeile.appendChild(text);
    zeile.addEventListener("click", () => {
      state.ort = ort;
      if (ort.region) {
        state.region = ort.region;
        zeichneRegionen();
      }
      zeichneOrte();
      zeichneVorschau();
    });
    liste.appendChild(zeile);
  }
}

async function legeOrtAn() {
  const name = $("ort-suche").value.trim();
  if (!name) {
    zeigeMeldung("Bitte oben den Namen des Ortes eintippen.", true);
    return;
  }
  const foto = aktuellesFoto();
  const neuerOrt = {
    name: name,
    kategorie: "Eigener Ort",
    latitude: foto ? foto.latitude : null,
    longitude: foto ? foto.longitude : null
  };
  try {
    const ort = await api("/api/stammorte", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(neuerOrt)
    });
    $("ort-suche").value = "";
    state.ort = ort;
    await ladeOrte();
    zeichneVorschau();
  } catch (fehler) {
    zeigeMeldung("Der Ort konnte nicht gespeichert werden: " + fehler.message, true);
  }
}

/* -------------------------------------------------------------- Hashtags */

async function ladeHashtagBank() {
  try {
    state.bank = await api("/api/hashtags");
  } catch (fehler) {
    state.bank = { gruppen: [], accounts: [] };
    return;
  }
  zeichneGenreChips();
  zeichneRegionen();
}

/**
 * Baut die Regionsauswahl aus der Hashtag-Bank. Vorher standen die drei Gegenden fest im
 * HTML — eine neue regionale Gruppe in hashtags.json war damit nicht erreichbar, es hätte
 * jedes Mal eine Code-Änderung gebraucht. Jetzt genügt der Eintrag in der Bank.
 */
function zeichneRegionen() {
  const auswahl = $("hashtag-region");
  auswahl.innerHTML = "";
  const keine = document.createElement("option");
  keine.value = "";
  keine.textContent = "kein lokaler Bezug";
  auswahl.appendChild(keine);

  for (const gruppe of state.bank.gruppen) {
    if (gruppe.typ !== "REGIONAL" || !gruppe.region) continue;
    const option = document.createElement("option");
    option.value = gruppe.region;
    option.textContent = gruppe.name;
    auswahl.appendChild(option);
  }

  // Eine Gegend, die es in der Bank nicht gibt, lässt sich nicht auswählen — dann steht hier
  // wieder „kein lokaler Bezug". Nur solange die Bank noch nicht geladen ist, bleibt die
  // gemerkte Gegend stehen: sonst würde ein Entwurf, der vor der Bank ankommt, seine Gegend
  // genau hier verlieren.
  const geladen = state.bank.gruppen.length > 0;
  const vorhanden = Array.from(auswahl.options).some((o) => o.value === state.region);
  if (geladen && !vorhanden) state.region = "";
  auswahl.value = vorhanden ? state.region : "";
}

function zeichneGenreChips() {
  const behaelter = $("genre-chips");
  behaelter.innerHTML = "";
  for (const gruppe of state.bank.gruppen.filter((g) => g.typ === "GENRE")) {
    const chip = document.createElement("span");
    chip.className = "chip is-tag" + (state.genres.includes(gruppe.id) ? " is-active" : "");
    chip.textContent = gruppe.name;
    chip.addEventListener("click", () => {
      state.genres = state.genres.includes(gruppe.id)
        ? state.genres.filter((id) => id !== gruppe.id)
        : state.genres.concat(gruppe.id);
      zeichneGenreChips();
    });
    behaelter.appendChild(chip);
  }
}

/** Holt den Regelvorschlag vom Server und zeigt ihn nach Kategorien getrennt. */
async function holeHashtagVorschlag() {
  const parameter = new URLSearchParams();
  const region = state.region;
  if (region) parameter.set("region", region);
  for (const genre of state.genres) parameter.append("genres", genre);
  parameter.set("presse", $("hashtag-presse").checked ? "true" : "false");
  parameter.set("reichweite", $("hashtag-reichweite").checked ? "true" : "false");

  let vorschlag;
  try {
    vorschlag = await api("/api/hashtags/vorschlag?" + parameter.toString());
  } catch (fehler) {
    zeigeMeldung("Vorschlag fehlgeschlagen: " + fehler.message, true);
    return;
  }

  // Ergänzen statt überschreiben: Von Hand eingefügte Hashtags bleiben erhalten, der
  // Vorschlag kommt hinten dran. Wer von vorne anfangen will, drückt vorher „Leeren".
  const bilanz = fuegeHashtagHinzu(vorschlag.alleTags.join(" "));
  $("hashtag-hinweis").textContent = vorschlag.hinweis;

  const bloecke = $("hashtag-bloecke");
  bloecke.innerHTML = "";
  for (const block of vorschlag.bloecke) {
    const titel = document.createElement("div");
    titel.className = "block-titel";
    titel.textContent = block.gruppeName;
    const chips = document.createElement("div");
    chips.className = "chips";
    for (const tag of block.tags) {
      chips.appendChild(tagChip(tag));
    }
    bloecke.append(titel, chips);
  }

  const accountBereich = $("account-bereich");
  const accountChips = $("account-chips");
  accountChips.innerHTML = "";
  for (const handle of vorschlag.accounts) {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = handle;
    accountChips.appendChild(chip);
  }
  accountBereich.classList.toggle("hidden", vorschlag.accounts.length === 0);

  zeichneHashtags();
  zeichneVorschau();

  zeigeMeldung("Vorschlag: " + bilanzText(bilanz) + " Insgesamt jetzt "
    + state.hashtags.length + ".", bilanz.abgewiesen > 0);
}

/** Chip im Vorschlagsblock: an- und abwählbar, damit nichts ungeprüft mitläuft. */
function tagChip(tag) {
  const chip = document.createElement("span");
  const setze = () => {
    chip.className = "chip is-tag" + (istSchonDabei(tag) ? " is-active" : "");
  };
  chip.textContent = tag;
  chip.title = "An- oder abwählen";
  setze();
  chip.addEventListener("click", () => {
    const klein = tag.toLowerCase();
    if (istSchonDabei(tag)) {
      state.hashtags = state.hashtags.filter((t) => t.toLowerCase() !== klein);
    } else if (state.hashtags.length >= HASHTAG.MAX) {
      zeigeMeldung("Instagram nimmt höchstens " + HASHTAG.MAX
        + " Hashtags — entferne zuerst einen anderen.", true);
      return;
    } else {
      state.hashtags = state.hashtags.concat(tag);
    }
    setze();
    zeichneHashtags();
    zeichneVorschau();
  });
  return chip;
}

async function kopiereHashtags() {
  if (state.hashtags.length === 0) return;
  const text = state.hashtags.join(" ");
  try {
    await navigator.clipboard.writeText(text);
    $("hashtag-hinweis").textContent = state.hashtags.length + " Hashtags in die Zwischenablage kopiert.";
  } catch (fehler) {
    // Ohne Clipboard-Freigabe (etwa über http im Heimnetz) bleibt der Weg über Markieren.
    window.prompt("Zum Kopieren markieren und Strg+C drücken:", text);
  }
}

/**
 * Zerlegt eine Eingabe in einzelne Hashtags. Gedacht fürs Einfügen eines ganzen Blocks:
 * Vorher wurden schlicht alle Leerzeichen entfernt, aus „#a #b #c" wurde dadurch der eine
 * unbrauchbare Tag „#a#b#c". Getrennt wird an Leerzeichen, Komma, Semikolon, Zeilenumbruch
 * — und zusätzlich vor jedem #, damit auch ein bereits zusammengeschriebener Block wieder
 * auseinanderfällt.
 */
function zerlegeHashtags(eingabe) {
  return String(eingabe || "")
    .split(/[\s,;]+|(?=#)/)
    // Erlaubt sind Buchstaben (auch Umlaute), Ziffern und der Unterstrich — mehr nimmt
    // Instagram in einem Hashtag nicht an.
    .map((teil) => teil.replace(/^#+/, "").replace(/[^\p{L}\p{N}_]/gu, ""))
    .filter(Boolean)
    .map((tag) => "#" + tag);
}

/**
 * Instagram unterscheidet bei Hashtags nicht zwischen groß und klein: #Shanghai und
 * #shanghai sind derselbe Tag. Beim Vergleichen muss das berücksichtigt werden, sonst
 * stehen am Ende beide Schreibweisen im Post.
 */
function istSchonDabei(tag) {
  const klein = tag.toLowerCase();
  return state.hashtags.some((vorhanden) => vorhanden.toLowerCase() === klein);
}

/**
 * Fügt Hashtags hinzu und meldet zurück, was damit passiert ist: wie viele neu sind,
 * wie viele schon dastanden und wie viele an der Obergrenze abgewiesen wurden.
 */
function fuegeHashtagHinzu(eingabe) {
  const bilanz = { neu: 0, doppelt: 0, abgewiesen: 0 };
  for (const tag of zerlegeHashtags(eingabe)) {
    if (istSchonDabei(tag)) {
      bilanz.doppelt++;
    } else if (state.hashtags.length >= HASHTAG.MAX) {
      bilanz.abgewiesen++;
    } else {
      state.hashtags.push(tag);
      bilanz.neu++;
    }
  }
  if (bilanz.neu > 0) {
    zeichneHashtags();
    zeichneVorschau();
  }
  return bilanz;
}

/** Eine Zeile, die sagt, was beim Hinzufügen herausgekommen ist. */
function bilanzText(bilanz) {
  const teile = [];
  if (bilanz.neu > 0) teile.push(bilanz.neu + " übernommen");
  if (bilanz.doppelt > 0) teile.push(bilanz.doppelt + " doppelt");
  if (bilanz.abgewiesen > 0) {
    teile.push(bilanz.abgewiesen + " nicht mehr möglich (Instagram nimmt höchstens "
      + HASHTAG.MAX + " Hashtags)");
  }
  return (teile.length ? teile.join(", ") : "nichts geändert") + ".";
}

function zeichneHashtags() {
  const behaelter = $("hashtag-chips");
  behaelter.innerHTML = "";
  for (const tag of state.hashtags) {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = tag;
    chip.title = "Zum Entfernen klicken";
    chip.addEventListener("click", () => {
      state.hashtags = state.hashtags.filter((eintrag) => eintrag !== tag);
      zeichneHashtags();
      zeichneVorschau();
    });
    behaelter.appendChild(chip);
  }
  const anzahl = state.hashtags.length;
  const zahl = $("hashtag-zahl");
  let zusatz = "";
  let zustand = "";
  if (anzahl >= HASHTAG.MAX) {
    zusatz = " · Obergrenze erreicht";
    zustand = " zahl-grenze";
  } else if (anzahl > HASHTAG.ZIEL_MAX) {
    zusatz = " · über der Zielgröße, max. " + HASHTAG.MAX;
    zustand = " zahl-zuviel";
  } else if (anzahl >= HASHTAG.ZIEL_MIN) {
    zustand = " zahl-gut";
  }
  zahl.textContent = anzahl + " von " + HASHTAG.ZIEL_MIN + "–" + HASHTAG.ZIEL_MAX + zusatz;
  zahl.className = "faint" + zustand;

  // Eigene Zeile, wenn es eng wird — der Zähler allein wird leicht übersehen.
  const warnung = $("hashtag-warnung");
  if (anzahl >= HASHTAG.MAX) {
    warnung.textContent = "Du bist bei " + HASHTAG.MAX + " Hashtags. Mehr nimmt Instagram "
      + "nicht an — weitere werden nicht übernommen, bis du welche entfernst.";
  } else if (anzahl > HASHTAG.ZIEL_MAX) {
    warnung.textContent = anzahl + " Hashtags sind erlaubt (bis " + HASHTAG.MAX
      + "), liegen aber über deiner Zielgröße von " + HASHTAG.ZIEL_MIN + " bis "
      + HASHTAG.ZIEL_MAX + ". Relevanz schlägt Menge.";
  } else {
    warnung.textContent = "";
  }
  warnung.classList.toggle("hidden", warnung.textContent === "");
}

/* ---------------------------------------------------------------- Archiv */

/**
 * Das Archiv ist die Gegenseite zum Löschen: Ein veröffentlichter Post wandert mitsamt
 * Caption, Hashtags und Ort in einen eigenen Ordner, und der Arbeitsbereich wird leer.
 * Die Originale kommen nur auf Wunsch mit — und lassen sich später einzeln wieder löschen,
 * ohne dass der Post verloren geht.
 */
/** Sagt im Archiv-Reiter, was das Archivieren gerade bedeuten würde. */
function archivHinweis() {
  const anzahl = carouselEintraege().length;
  if (anzahl === 0) {
    return "Zurzeit ist kein Post offen — lade zuerst Fotos im Reiter „Zuschnitt\u201c.";
  }
  const mitOriginalen = $("archiv-originale").checked;
  return anzahl + " fertige Bild(er) wandern mitsamt Caption, Hashtags und Ort ins Archiv"
    + (mitOriginalen
      ? ", dazu " + state.fotos.length + " Originalfoto(s) — die lassen sich dort später einzeln löschen."
      : ". Die Originale werden dabei gelöscht.")
    + " Danach ist der Arbeitsbereich leer.";
}

async function ladeArchiv() {
  let liste;
  try {
    liste = await api("/api/archiv");
  } catch (fehler) {
    $("archiv-liste").textContent = "Archiv nicht lesbar: " + fehler.message;
    return;
  }
  zeichneArchiv(liste || []);
}

function mb(bytes) {
  if (!bytes) return "0 MB";
  return bytes < 1024 * 1024
    ? Math.max(1, Math.round(bytes / 1024)) + " KB"
    : (bytes / 1024 / 1024).toFixed(1) + " MB";
}

function zeichneArchiv(liste) {
  const behaelter = $("archiv-liste");
  behaelter.innerHTML = "";

  if (liste.length === 0) {
    const leer = document.createElement("p");
    leer.className = "hint";
    leer.textContent = "Noch nichts archiviert. Oben einen Namen eintragen und „Archivieren\u201c "
      + "drücken, sobald ein Post veröffentlicht ist.";
    behaelter.appendChild(leer);
    return;
  }

  for (const eintrag of liste) {
    const post = eintrag.post;
    const karte = document.createElement("div");
    karte.className = "archiv-post";

    const kopf = document.createElement("div");
    kopf.className = "archiv-kopf";
    const name = document.createElement("span");
    name.className = "archiv-name";
    name.textContent = post.name;
    const meta = document.createElement("span");
    meta.className = "archiv-meta";
    meta.textContent = post.id.slice(0, 10) + " · " + post.bilder.length + " Bild(er) · "
      + mb(eintrag.bytes)
      + (post.originale.length > 0
        ? " davon " + mb(eintrag.originalBytes) + " Originale" : " · ohne Originale");
    kopf.append(name, meta);
    karte.appendChild(kopf);

    const streifen = document.createElement("div");
    streifen.className = "archiv-bilder";
    for (const bild of post.bilder) {
      const img = document.createElement("img");
      img.src = "/api/archiv/" + encodeURIComponent(post.id) + "/bild/" + encodeURIComponent(bild.datei);
      img.alt = bild.datei;
      img.title = bild.art === "pano"
        ? "Pano-Ausschnitt " + bild.sliceIndex + " von " + bild.anzahl
        : bild.art === "ganz" ? "ganzes Foto" : "Ausschnitt";
      streifen.appendChild(img);
    }
    karte.appendChild(streifen);

    if (post.caption) {
      const text = document.createElement("div");
      text.className = "archiv-caption";
      text.textContent = post.caption;
      karte.appendChild(text);
    }
    if (post.hashtags.length > 0 || post.ort) {
      const tags = document.createElement("div");
      tags.className = "archiv-tags";
      tags.textContent = [post.ort, post.hashtags.join(" ")].filter(Boolean).join(" · ");
      karte.appendChild(tags);
    }

    const knoepfe = document.createElement("div");
    knoepfe.className = "archiv-knoepfe";
    if (post.originale.length > 0) {
      const weg = document.createElement("button");
      weg.type = "button";
      weg.className = "ghost";
      weg.textContent = "Originale löschen (" + mb(eintrag.originalBytes) + ")";
      weg.title = "Fertige Bilder, Caption und Hashtags bleiben erhalten";
      weg.addEventListener("click", () => loescheArchivOriginale(post));
      knoepfe.appendChild(weg);
    }
    const ganzWeg = document.createElement("button");
    ganzWeg.type = "button";
    ganzWeg.className = "ghost gefahr";
    ganzWeg.textContent = "Post löschen";
    ganzWeg.addEventListener("click", () => loescheArchivPost(post));
    knoepfe.appendChild(ganzWeg);
    karte.appendChild(knoepfe);

    behaelter.appendChild(karte);
  }
}

async function archiviere() {
  const eintraege = carouselEintraege();
  if (eintraege.length === 0) {
    zeigeMeldung("Es ist kein Post offen, der sich archivieren ließe.", true);
    return;
  }
  const mitOriginalen = $("archiv-originale").checked;
  try {
    const eintrag = await api("/api/archiv", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: $("archiv-name").value.trim(),
        caption: state.caption,
        hashtags: state.hashtags,
        ort: state.ort ? state.ort.name : null,
        format: state.format,
        mitOriginalen: mitOriginalen,
        post: carouselWunsch()
      })
    });
    zeigeMeldung("Archiviert als „" + eintrag.post.name + "\u201c (" + mb(eintrag.bytes) + ")"
      + (mitOriginalen ? " samt Originalen." : ".")
      + " Der Arbeitsbereich ist jetzt leer.");
  } catch (fehler) {
    zeigeMeldung("Archivieren fehlgeschlagen: " + fehler.message, true);
    return;
  }

  // Der Server hat die Arbeitsfotos und den Entwurf bereits gelöscht — hier nur noch den
  // Rest zurücksetzen.
  setzeArbeitsbereichZurueck();
  $("archiv-name").value = "";
  entwurfVergessen();
  await ladeFotos();
  await ladeOrte();
  await ladeArchiv();
  // Der Hinweis stand noch auf dem gerade abgelegten Post — jetzt ist der Bereich leer.
  $("archiv-hinweis").textContent = archivHinweis();
}

async function loescheArchivOriginale(post) {
  if (!window.confirm("Die Originalfotos von „" + post.name + "\u201c löschen?\n\n"
      + "Die fertigen Post-Bilder, Caption und Hashtags bleiben erhalten.")) {
    return;
  }
  try {
    await api("/api/archiv/" + encodeURIComponent(post.id) + "/originale", { method: "DELETE" });
    zeigeMeldung("Originale von „" + post.name + "\u201c gelöscht.");
  } catch (fehler) {
    zeigeMeldung("Löschen fehlgeschlagen: " + fehler.message, true);
  }
  await ladeArchiv();
}

async function loescheArchivPost(post) {
  if (!window.confirm("Den archivierten Post „" + post.name + "\u201c vollständig löschen?\n\n"
      + "Bilder, Caption und Hashtags sind dann weg.")) {
    return;
  }
  try {
    await api("/api/archiv/" + encodeURIComponent(post.id), { method: "DELETE" });
    zeigeMeldung("Post „" + post.name + "\u201c aus dem Archiv gelöscht.");
  } catch (fehler) {
    zeigeMeldung("Löschen fehlgeschlagen: " + fehler.message, true);
  }
  await ladeArchiv();
}

/* ---------------------------------------------------------------- Export */

/**
 * Der Wunschzettel für den Server: alle Bilder des Posts in der Reihenfolge des Carousels,
 * dazu Caption, Hashtags und Ort. Der Text kommt mit, weil er sonst im ZIP fehlt — und ein
 * Ordner voller Bilder ohne den zugehörigen Text ist beim Hochladen nur die halbe Arbeit.
 */
function carouselWunsch() {
  return {
    seitenverhaeltnis: carouselAspect(),
    caption: state.caption,
    hashtags: state.hashtags,
    ort: state.ort ? state.ort.name : null,
    format: FORMATE[state.format].label,
    bilder: carouselEintraege().map((eintrag) => ({
      id: eintrag.foto.id,
      fokusX: eintrag.fokusX,
      fokusY: eintrag.fokusY,
      panoAnzahl: eintrag.art === "pano" ? eintrag.anzahl : null,
      sliceIndex: eintrag.art === "pano" ? eintrag.sliceIndex : null,
      fuellung: eintrag.art === "ganz" ? eintrag.fuellung : null
    }))
  };
}

function storyParameter() {
  const fokus = aktuellerFokus();
  return new URLSearchParams({
    format: state.format,
    fokusX: fokus.x.toFixed(4),
    fokusY: fokus.y.toFixed(4)
  }).toString();
}

/**
 * Lädt die fertigen Bilder als ZIP herunter. Die Anwendung läuft auf der NAS — ein Ordner
 * dort nützt beim Hochladen in Metricool nichts, gebraucht werden die Dateien hier.
 */
async function ladeZipHerunter() {
  const eintraege = carouselEintraege();
  if (eintraege.length === 0) return;

  const istStory = !FORMATE[state.format].carousel;
  const anfrage = istStory
    ? ["/api/fotos/" + state.fotoId + "/zip?" + storyParameter(), { method: "POST" }]
    : ["/api/fotos/carousel/zip", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(carouselWunsch())
      }];

  try {
    const antwort = await fetch(anfrage[0], anfrage[1]);
    if (!antwort.ok) throw new Error(await antwort.text() || ("Fehler " + antwort.status));

    const daten = await antwort.blob();
    const kopf = antwort.headers.get("Content-Disposition") || "";
    const treffer = kopf.match(/filename="?([^"]+)"?/);
    const adresse = URL.createObjectURL(daten);
    const link = document.createElement("a");
    link.href = adresse;
    link.download = treffer ? treffer[1] : "postpilot.zip";
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(adresse), 4000);

    zeigeMeldung(eintraege.length + " Bild(er) als ZIP heruntergeladen — im Download-Ordner "
      + "entpacken und in Metricool hochladen.");
  } catch (fehler) {
    zeigeMeldung("Herunterladen fehlgeschlagen: " + fehler.message, true);
  }
}

/** Legt dieselben Bilder zusätzlich im Datenordner der Anwendung ab. */
async function legeImDatenordnerAb() {
  const eintraege = carouselEintraege();
  if (eintraege.length === 0) return;

  try {
    const ergebnis = !FORMATE[state.format].carousel
      ? await api("/api/fotos/" + state.fotoId + "/export?" + storyParameter(), { method: "POST" })
      : await api("/api/fotos/carousel/export", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(carouselWunsch())
        });
    zeigeMeldung(ergebnis.dateien.length + " Datei(en) abgelegt in: " + ergebnis.ordner);
  } catch (fehler) {
    zeigeMeldung("Ablegen fehlgeschlagen: " + fehler.message, true);
  }
}

/** Rückmeldung, die man nicht übersehen kann — unabhängig von Reiter und Fensterbreite. */
function zeigeMeldung(text, istFehler) {
  const kasten = $("meldung");
  $("meldung-text").textContent = text;
  kasten.classList.toggle("ist-fehler", !!istFehler);
  kasten.classList.remove("hidden");
  window.clearTimeout(zeigeMeldung.uhr);
  if (!istFehler) {
    zeigeMeldung.uhr = window.setTimeout(() => kasten.classList.add("hidden"), 9000);
  }
}

/* ----------------------------------------------------------------- Info */

/** Zeigt, seit wann diese Instanz läuft — die Probe aufs Exempel nach einem Update. */
async function zeigeInfo() {
  try {
    const info = await api("/api/info");
    $("datenpfad").textContent = "gestartet " + info.gestartet + " · Daten: " + info.datenverzeichnis;
    $("datenpfad").title = "Läuft seit " + info.laufzeit
      + ". Zeigt das eine alte Uhrzeit, läuft noch die alte Fassung.";
  } catch (fehler) {
    $("datenpfad").textContent = "";
  }
}

/* ------------------------------------------------------------- Entwurf */

/**
 * Der Arbeitsstand lebte bisher ausschließlich im Browser. Ein F5, ein Klick auf
 * „Anleitung“ und zurück — und Format, Ausschnitte, Pano-Einteilung, Caption und Hashtags
 * waren weg, ohne jede Warnung. Der Export lieferte danach brav mittige Standardzuschnitte,
 * was wie ein Fehler im Zuschneiden aussah, aber nur daran lag, dass die Einstellungen
 * nicht mehr existierten. Deshalb liegt der Entwurf jetzt auf dem Server in
 * <code>data/entwurf.json</code>.
 *
 * <p>Gespeichert wird nicht an jeder einzelnen Stelle, an der sich etwas ändert — bei
 * einem Dutzend Schaltern und Reglern wird dabei garantiert einer vergessen, und der
 * Verlust fällt erst beim nächsten Neuladen auf. Stattdessen vergleicht ein Taktgeber
 * einmal pro Sekunde den aktuellen Stand mit dem zuletzt gesendeten und schickt nur,
 * wenn sich wirklich etwas unterscheidet.
 */
const ENTWURF_TAKT_MS = 1000;

/** Erst wenn der gespeicherte Entwurf angewandt ist, darf gesichert werden. */
let entwurfBereit = false;
let zuletztGesendet = null;

/** Alles, was einen Neustart der Seite überleben muss — in einer Zeichenkette. */
function entwurfText() {
  return JSON.stringify({
    version: 1,
    format: state.format,
    fotoId: state.fotoId,
    modus: state.modus,
    fokus: state.fokus,
    caption: state.caption,
    hashtags: state.hashtags,
    genres: state.genres,
    ort: state.ort,
    region: state.region,
    presse: $("hashtag-presse").checked,
    reichweite: $("hashtag-reichweite").checked,
    archivName: $("archiv-name").value,
    archivOriginale: $("archiv-originale").checked
  });
}

/**
 * Schickt den Stand zum Server. <code>dringend</code> wird beim Verlassen der Seite
 * gesetzt: <code>keepalive</code> sorgt dafür, dass der Browser die Anfrage noch zu Ende
 * bringt, auch wenn das Dokument längst abgebaut ist.
 */
function sendeEntwurf(text, dringend) {
  return fetch("/api/entwurf", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: text,
    keepalive: !!dringend
  }).catch((fehler) => console.warn("Entwurf nicht gespeichert:", fehler.message));
}

/** Ein Takt: nur senden, wenn sich gegenüber dem letzten Mal etwas geändert hat. */
function pruefeEntwurf(dringend) {
  if (!entwurfBereit) return;
  let text;
  try {
    text = entwurfText();
  } catch (fehler) {
    return;
  }
  if (text === zuletztGesendet) return;
  zuletztGesendet = text;
  sendeEntwurf(text, dringend);
}

/** Holt den gespeicherten Entwurf und setzt die Oberfläche darauf. */
async function ladeEntwurf() {
  let entwurf = null;
  try {
    entwurf = await api("/api/entwurf");
  } catch (fehler) {
    console.warn("Entwurf konnte nicht geladen werden:", fehler.message);
  }

  if (entwurf && entwurf.version === 1) {
    if (FORMATE[entwurf.format]) state.format = entwurf.format;
    state.modus = entwurf.modus && typeof entwurf.modus === "object" ? entwurf.modus : {};
    state.fokus = entwurf.fokus && typeof entwurf.fokus === "object" ? entwurf.fokus : {};
    state.caption = typeof entwurf.caption === "string" ? entwurf.caption : "";
    state.hashtags = Array.isArray(entwurf.hashtags) ? entwurf.hashtags : [];
    state.genres = Array.isArray(entwurf.genres) ? entwurf.genres : [];
    state.ort = entwurf.ort || null;

    $("caption").value = state.caption;
    state.region = typeof entwurf.region === "string" ? entwurf.region : "";
    $("hashtag-presse").checked = !!entwurf.presse;
    if (entwurf.reichweite !== undefined) {
      $("hashtag-reichweite").checked = !!entwurf.reichweite;
    }
    $("archiv-name").value = entwurf.archivName || "";
    $("archiv-originale").checked = !!entwurf.archivOriginale;

    for (const button of document.querySelectorAll(".format")) {
      button.classList.toggle("is-active", button.dataset.format === state.format);
    }
    // Bank und Orte laden nebenher; wer zuerst fertig ist, zeichnet mit dem Stand von
    // hier, der andere zeichnet danach noch einmal.
    zeichneGenreChips();
    zeichneRegionen();
    if (state.orte.length > 0) zeichneOrte();
    zeichneHashtags();
  }

  zuletztGesendet = entwurfText();
  entwurfBereit = true;
}

/** Startet den Taktgeber und die Sicherung beim Verlassen der Seite. */
function verdrahteEntwurf() {
  window.setInterval(() => pruefeEntwurf(false), ENTWURF_TAKT_MS);
  // Zwischen zwei Takten liegt eine Sekunde — wer sofort nach einer Änderung neu lädt oder
  // zur Anleitung wechselt, würde sie sonst verlieren.
  window.addEventListener("pagehide", () => pruefeEntwurf(true));
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") pruefeEntwurf(true);
  });
}

/**
 * Wirft den gespeicherten Entwurf weg. Der Server tut das beim Leeren des Arbeitsbereichs
 * und beim Archivieren schon selbst — hier wird nur der Vergleichsstand nachgezogen,
 * damit der nächste Takt den leeren Stand nicht gleich wieder hinschreibt.
 */
function entwurfVergessen() {
  zuletztGesendet = entwurfText();
}

/* ------------------------------------------------------------- Wischen */

/**
 * Durch das Carousel wischen wie am Handy: Die Spur folgt stufenlos dem Zeiger und rastet
 * beim Loslassen auf das nächstgelegene Bild ein. Über die Ränder hinaus zieht sie nur
 * gebremst mit, damit spürbar ist, dass dort Schluss ist.
 */
function verdrahteWischen() {
  const flaeche = $("post-image");

  /** Ab einem Drittel Bildbreite springt es weiter; ein schneller Wisch genügt auch kürzer. */
  const ANTEIL_ZUM_WEITERSPRINGEN = 0.33;
  const SCHNELL_PX_PRO_MS = 0.45;
  const MINDESTWEG_FUER_SCHWUNG = 0.08;
  const RANDBREMSE = 0.35;

  flaeche.addEventListener("pointerdown", (ereignis) => {
    const anzahl = carouselEintraege().length;
    if (anzahl < 2 || ereignis.button !== 0) return;
    ereignis.preventDefault();
    state.wischen = {
      startX: ereignis.clientX,
      startY: ereignis.clientY,
      startIndex: state.vorschauIndex,
      anzahl: anzahl,
      zeit: performance.now(),
      letzteX: ereignis.clientX,
      letzteZeit: performance.now(),
      tempo: 0,
      aktiv: false
    };
    flaeche.setPointerCapture(ereignis.pointerId);
  });

  flaeche.addEventListener("pointermove", (ereignis) => {
    const w = state.wischen;
    if (!w) return;
    const dx = ereignis.clientX - w.startX;

    // Erst ab ein paar Pixeln waagerecht als Wischen werten — sonst stiehlt die Geste
    // jeden Klick auf die Punkte und jedes senkrechte Scrollen.
    if (!w.aktiv) {
      if (Math.abs(dx) < 6 || Math.abs(dx) < Math.abs(ereignis.clientY - w.startY)) return;
      w.aktiv = true;
      flaeche.classList.add("wischt");
    }

    const jetzt = performance.now();
    if (jetzt > w.letzteZeit) {
      w.tempo = (ereignis.clientX - w.letzteX) / (jetzt - w.letzteZeit);
      w.letzteX = ereignis.clientX;
      w.letzteZeit = jetzt;
    }

    // Am ersten und letzten Bild gibt es nichts mehr zu zeigen — dort gebremst mitziehen.
    const amRand = (dx > 0 && w.startIndex === 0) || (dx < 0 && w.startIndex === w.anzahl - 1);
    setzeSpur(w.startIndex, amRand ? dx * RANDBREMSE : dx, false);
    zeigeWischZaehler(w, dx);
  });

  const loslassen = (ereignis) => {
    const w = state.wischen;
    if (!w) return;
    state.wischen = null;
    flaeche.classList.remove("wischt");
    if (ereignis.pointerId !== undefined && flaeche.hasPointerCapture(ereignis.pointerId)) {
      flaeche.releasePointerCapture(ereignis.pointerId);
    }
    if (!w.aktiv) return;

    const dx = ereignis.clientX - w.startX;
    const breite = flaeche.getBoundingClientRect().width || 1;
    const weitGenug = Math.abs(dx) > breite * ANTEIL_ZUM_WEITERSPRINGEN;
    // Tempo allein genügt nicht: Sonst springt schon ein 20-Pixel-Zupfer weiter, weil der
    // über eine sehr kurze Zeit gerechnet beliebig schnell ist.
    const schnellGenug = Math.abs(w.tempo) > SCHNELL_PX_PRO_MS
      && Math.abs(dx) > breite * MINDESTWEG_FUER_SCHWUNG;
    const schritt = (weitGenug || schnellGenug) ? (dx < 0 ? 1 : -1) : 0;

    const ziel = klemme(w.startIndex + schritt, 0, w.anzahl - 1);
    setzeSpur(ziel, 0, true);
    if (ziel !== state.vorschauIndex) {
      state.vorschauIndex = ziel;
      // Nach dem Einrasten die Beschriftung nachziehen; die Spur selbst steht schon.
      window.setTimeout(() => zeichneVorschau(), 260);
    } else {
      aktualisiereWischAnzeige(ziel, w.anzahl);
    }
  };
  flaeche.addEventListener("pointerup", loslassen);
  flaeche.addEventListener("pointercancel", loslassen);

  // Zeigerlose Bedienung: mit den Pfeiltasten durchblättern, wenn die Vorschau den Fokus hat.
  flaeche.tabIndex = 0;
  flaeche.addEventListener("keydown", (ereignis) => {
    const anzahl = carouselEintraege().length;
    if (anzahl < 2) return;
    if (ereignis.key === "ArrowRight") blaettere(1, anzahl);
    if (ereignis.key === "ArrowLeft") blaettere(-1, anzahl);
  });
}

/** Während des Wischens zeigen Zähler und Punkte schon das Bild, auf das man zusteuert. */
function zeigeWischZaehler(w, dx) {
  const breite = $("post-image").getBoundingClientRect().width || 1;
  const verschoben = Math.round((w.startIndex * breite - dx) / breite);
  aktualisiereWischAnzeige(klemme(verschoben, 0, w.anzahl - 1), w.anzahl);
}

function aktualisiereWischAnzeige(index, anzahl) {
  $("preview-zaehler").textContent = (index + 1) + "/" + anzahl;
  const punkte = $("preview-dots").children;
  for (let i = 0; i < punkte.length; i++) {
    punkte[i].classList.toggle("is-active", i === index);
  }
}

/* ------------------------------------------------------------ Verdrahtung */

function starte() {
  $("upload-input").addEventListener("change", (ereignis) => {
    hochladen(Array.from(ereignis.target.files));
    ereignis.target.value = "";
  });

  for (const button of document.querySelectorAll(".format")) {
    button.addEventListener("click", () => setFormat(button.dataset.format));
  }

  for (const button of document.querySelectorAll(".step")) {
    button.addEventListener("click", () => {
      for (const anderer of document.querySelectorAll(".step")) {
        anderer.classList.toggle("is-active", anderer === button);
      }
      for (const name of ["crop", "text", "archiv"]) {
        $("panel-" + name).classList.toggle("hidden", button.dataset.tab !== name);
      }
      if (button.dataset.tab === "crop") zeichneBuehne();
      if (button.dataset.tab === "archiv") {
        $("archiv-hinweis").textContent = archivHinweis();
        ladeArchiv();
      }
    });
  }

  $("pano-minus").addEventListener("click", () => aenderePanoAnzahl(-1));
  $("pano-plus").addEventListener("click", () => aenderePanoAnzahl(1));
  $("modus-ausschnitt").addEventListener("click", () => setzeModus("ausschnitt"));
  $("modus-ganz").addEventListener("click", () => setzeModus("ganz"));
  $("modus-pano").addEventListener("click", () => setzeModus("pano"));
  $("fuellung").addEventListener("change", (ereignis) => setzeFuellung(ereignis.target.value));

  // Ausschnitt mit der Maus (oder dem Finger) über dem Foto verschieben.
  const buehne = $("stage");
  buehne.addEventListener("pointerdown", (ereignis) => {
    // Beim ganzen Foto gibt es nichts zu verschieben — es ist ja vollständig im Bild.
    if (!state.fotoId || istGanzesFoto(aktuellesFoto())) return;
    ereignis.preventDefault();
    state.ziehen = {
      startX: ereignis.clientX,
      startY: ereignis.clientY,
      startFokus: aktuellerFokus()
    };
    buehne.classList.add("is-dragging");
    buehne.setPointerCapture(ereignis.pointerId);
  });

  buehne.addEventListener("pointermove", (ereignis) => {
    if (!state.ziehen) return;
    verschiebeAusschnitt(
      ereignis.clientX - state.ziehen.startX,
      ereignis.clientY - state.ziehen.startY,
      state.ziehen.startFokus);
  });

  const zieheEnde = (ereignis) => {
    if (!state.ziehen) return;
    state.ziehen = null;
    buehne.classList.remove("is-dragging");
    if (ereignis.pointerId !== undefined && buehne.hasPointerCapture(ereignis.pointerId)) {
      buehne.releasePointerCapture(ereignis.pointerId);
    }
    // Erst jetzt neu rendern lassen — währenddessen wäre es ein Aufruf pro Mausbewegung.
    zeichneVorschau();
  };
  buehne.addEventListener("pointerup", zieheEnde);
  buehne.addEventListener("pointercancel", zieheEnde);

  $("crop-reset").addEventListener("click", () => {
    setzeFokus(0.5, 0.5);
    zeichneBuehne();
    zeichneVorschau();
  });

  $("ort-suche").addEventListener("input", () => ladeOrte());
  $("ort-anlegen").addEventListener("click", () => legeOrtAn());
  $("ort-keiner").addEventListener("click", () => {
    state.ort = null;
    zeichneOrte();
    zeichneVorschau();
  });

  $("caption").addEventListener("input", (ereignis) => {
    state.caption = ereignis.target.value;
    zeichneVorschau();
  });

  $("hashtag-region").addEventListener("change", (ereignis) => {
    state.region = ereignis.target.value;
  });

  $("hashtag-vorschlag").addEventListener("click", () => holeHashtagVorschlag());
  $("hashtag-kopieren").addEventListener("click", () => kopiereHashtags());
  $("hashtag-leeren").addEventListener("click", () => {
    state.hashtags = [];
    $("hashtag-bloecke").innerHTML = "";
    $("hashtag-hinweis").textContent = "";
    zeichneHashtags();
    zeichneVorschau();
  });

  $("hashtag-eingabe").addEventListener("keydown", (ereignis) => {
    if (ereignis.key === "Enter") {
      ereignis.preventDefault();
      const bilanz = fuegeHashtagHinzu(ereignis.target.value);
      ereignis.target.value = "";
      if (bilanz.abgewiesen > 0) zeigeMeldung(bilanzText(bilanz), true);
    }
  });

  // Ein eingefügter Block wird sofort übernommen — ohne dass man erst Enter drücken muss.
  $("hashtag-eingabe").addEventListener("paste", (ereignis) => {
    const ablage = ereignis.clipboardData || window.clipboardData;
    if (!ablage) return;
    const tags = zerlegeHashtags(ablage.getData("text"));
    if (tags.length < 2) return;
    ereignis.preventDefault();
    const bilanz = fuegeHashtagHinzu(tags.join(" "));
    ereignis.target.value = "";
    zeigeMeldung("Eingefügt: " + bilanzText(bilanz), bilanz.abgewiesen > 0);
  });

  verdrahteWischen();

  $("export-button").addEventListener("click", () => ladeZipHerunter());
  $("ablegen-button").addEventListener("click", () => legeImDatenordnerAb());
  $("neuer-post").addEventListener("click", () => neuerPost());
  $("archiv-ablegen").addEventListener("click", () => archiviere());
  $("archiv-originale").addEventListener("change", () => {
    $("archiv-hinweis").textContent = archivHinweis();
  });
  $("meldung-zu").addEventListener("click", () => $("meldung").classList.add("hidden"));

  window.addEventListener("resize", () => {
    const foto = aktuellesFoto();
    if (foto) zeichneOverlay(foto);
    // Die Spur ist in Pixeln verschoben — nach einer Größenänderung stimmt sie sonst nicht mehr.
    if (carouselEintraege().length > 0) setzeSpur(state.vorschauIndex, 0, false);
  });

  zeigeInfo();
  // Reihenfolge ist hier wichtig: Erst der Entwurf, dann die Fotos. Format, Ausschnitte und
  // Spielart je Foto bestimmen, was gezeichnet wird — lädt man die Fotos vorher, zeichnet
  // die Seite einmal den Standardzustand und der Entwurf käme zu spät.
  ladeEntwurf()
    .then(() => ladeFotos())
    .catch((fehler) => console.error("Fotos konnten nicht geladen werden:", fehler))
    .finally(() => verdrahteEntwurf());
  ladeOrte();
  ladeHashtagBank();
  zeichneHashtags();
}

document.addEventListener("DOMContentLoaded", starte);
