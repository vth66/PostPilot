# PostPilot

Posting-Studio für die eigene Landschafts- und Nachtfotografie
([vothography](https://www.instagram.com/vothography/)): Fotos laden, auf Instagram-Formate
zuschneiden, breite Panoramen nahtlos in ein Carousel teilen, Caption und Hashtags
vorbereiten, einen Stammort wählen, das Ergebnis in einer realistischen Handy-Vorschau
prüfen, exportieren und den veröffentlichten Post archivieren.

Läuft als eigenständiges Programm mit eingebautem Webserver — bedient wird es im Browser.
Kein Hosting, kein fremder Dienst, keine Anmeldung, keine Daten außerhalb des eigenen Netzes.

**Einrichtung Schritt für Schritt: [SETUP.md](SETUP.md)** — lokal auf dem PC und als
Docker-Container auf einer Synology-NAS.

## Schnellstart

```
mvn package
java -jar target/postpilot.jar
```

Dann `http://localhost:8080` im Browser öffnen. Im Betrieb auf der NAS läuft dieselbe JAR
im Container unter `http://<nas>:8095`.

Die eingebaute Anleitung erreicht man in der Oberfläche über **? Anleitung** oben rechts
(`/hilfe.html`) — zwölf Kapitel von der ersten Minute bis zu den Instagram-Zahlen, die im
Programm verdrahtet sind.

## Was es kann

**Zuschnitt.** Das Format gilt für den ganzen Post, weil Instagram allen Bildern eines
Carousels das Seitenverhältnis des ersten Bildes aufzwingt: 4:5 (1080 × 1350), 1:1
(1080 × 1080) oder 9:16 als Story (1080 × 1920). Ausschnitt per Hand verschieben, mit
Drittel-Raster und abgedunkelter Maske.

**Drei Spielarten je Foto.**

| Spielart | Was passiert |
|---|---|
| Ausschnitt | größtmöglicher Zuschnitt im Postformat, um den Fokuspunkt gelegt |
| Ganzes Foto | das vollständige Bild eingepasst, Freiraum in Schwarz, Weiß oder weich |
| Pano-Split | ein breites Panorama als Folge nahtlos aneinandergrenzender Ausschnitte |

Dazu das **Duplizieren** eines Fotos: so liegen dasselbe Panorama einmal ganz (als Opener)
und einmal als Pano-Split in Groß im gleichen Carousel.

**Pano-Split.** Die Ausschnitte nehmen ein waagerechtes Band aus dem Bild, nicht die volle
Höhe — sonst wären sie bei einem 3:1-Panorama im 4:5-Format winzige Streifen. Die Anzahl ist
einstellbar innerhalb der Grenzen, die sich aus Bildmaßen und Zielformat ergeben: jeder
Ausschnitt muss zwischen 0,8 und 1,91 liegen, mindestens die halbe Bildhöhe nutzen, und ein
Carousel nimmt über die Programmierschnittstelle höchstens zehn Bilder. Die Kanten stoßen
pixelgenau aneinander, auch bei ungeraden Bildbreiten.

**Handy-Vorschau.** Instagram-Oberfläche im Telefonrahmen, stufenlos durchwischbar wie am
Gerät, mit Punkten unter dem Bild, der 125-Zeichen-Grenze der Caption und — bei der Story —
den Schutzzonen von 14 % oben und 20 % unten.

**Text und Hashtags.** Caption mit Live-Vorschau. Hashtag-Bank mit Genre- und Regionsbezug,
Vorschläge sind mit von Hand eingefügten Blöcken kombinierbar; Duplikate werden
groß-/kleinschreibungsunabhängig herausgefiltert. Der Zähler kennt die eigene Zielgröße
(12–18) und Instagrams harte Obergrenze von 30 und warnt an beiden Marken.

**Stammorte.** Aus einer JSON-Datei, durchsuchbar, sortiert nach Entfernung zum GPS-Punkt
des Fotos.

**Export.** Beide Wege rechnen über denselben Code:

* **Bilder herunterladen (ZIP)** — der Normalfall: `01.jpg`, `02.jpg` … in
  Carousel-Reihenfolge plus `post.txt` mit Caption, Hashtags, Ort, Format und einem
  fertigen Block zum Kopieren.
* **Im Datenordner ablegen** — dieselben Dateien zusätzlich nach
  `data/export/<Datum>_<Uhrzeit>_carousel/` auf der NAS.

**Arbeitsstand.** Format, Spielart, Ausschnitt je Foto, Caption, Hashtags und Ort werden
sekündlich in `data/entwurf.json` gesichert und beim Laden wieder angewandt. Ein Neuladen,
ein Seitenwechsel oder ein Neustart des Containers kostet nichts mehr.

**Archiv.** Ein veröffentlichter Post wandert nach `data/archiv/<Datum>_<Name>/` — fertige
Bilder, `post.json`, `post.txt` und auf Wunsch die Originale. Danach ist der Arbeitsbereich
leer. Originale und Post lassen sich später getrennt wieder löschen: Platz schaffen, ohne
die Erinnerung zu verlieren.

## Was fehlt

Terminplanung und direkte Metricool-Anbindung. Zu den zwei Einschränkungen, die den Weg
dorthin bestimmen, siehe den Schluss von [SETUP.md](SETUP.md).

Fotos mit gesetztem EXIF-Orientierungs-Flag (hochkant aufgenommen, vom Kameragehäuse
gedreht) werden noch unrotiert verarbeitet. Bei Landschaftsaufnahmen vom Stativ spielt das
keine Rolle, bei Hochformaten aus der Hand schon.

## Aufbau

```
model/     Datentypen: PanoSlice, PanoSplitResult, ProtectedRegion, CropFormat,
           Stammort, GeoPoint, ArchivPost, Hashtagbank, Hashtaggruppe, Accountgruppe
service/   Fachlogik ohne Spring: PanoSplitService, CropService, BildService, FotoStore,
           StammortStore, HashtagService, HashtagStore, ArchivService, EntwurfStore,
           ExifService, JpegWriter, AppPaths
config/    Verdrahtung der Bausteine
web/       REST-Schnittstelle für die Oberfläche
resources/static/   die Oberfläche ohne Framework: index.html, styles.css, app.js,
                    hilfe.html
```

Die Fachlogik kennt Spring nicht und lässt sich deshalb ohne laufenden Server testen:

```
mvn test
```

Geprüft werden vor allem die Pano-Split-Regeln — Nahtlosigkeit auch bei ungeraden
Bildbreiten, die erlaubten Seitenverhältnisse, die Formeln für kleinste und größte Anzahl
und der Motivschutz (lieber weniger und breitere Ausschnitte als eine Schnittkante durch
die Turmspitze) — sowie Zuschnitt, Einpassen, Hashtag-Regeln, Archiv und Entwurfsspeicher.

## Einstellungen

| Einstellung | Standard | Wofür |
|---|---|---|
| `server.port` | 8080 | Portnummer der Oberfläche |
| `postpilot.data-dir` | Benutzerprofil | Ordner für Fotos, Stammorte, Entwurf, Exporte, Archiv |

Beides per Startparameter (`--server.port=9090`) oder Umgebungsvariable (`SERVER_PORT`,
`POSTPILOT_DATA_DIR`) überschreibbar. Im Container ist `POSTPILOT_DATA_DIR=/data` gesetzt.

## Schnittstelle

| Aufruf | Zweck |
|---|---|
| `GET /api/fotos` | Liste der Fotos mit Maßen und GPS |
| `POST /api/fotos` | Foto hochladen (Formularfeld `datei`) |
| `DELETE /api/fotos` | Arbeitsbereich leeren |
| `DELETE /api/fotos/{id}` | einzelnes Foto entfernen |
| `POST /api/fotos/{id}/duplizieren` | Foto verdoppeln |
| `PUT /api/fotos/reihenfolge` | Carousel-Reihenfolge setzen |
| `GET /api/fotos/{id}/original` | Originalbild |
| `GET /api/fotos/{id}/crop?seitenverhaeltnis=&fokusX=&fokusY=` | Zuschnitt als JPEG |
| `GET /api/fotos/{id}/ganz?seitenverhaeltnis=&fuellung=` | ganzes Foto eingepasst |
| `GET /api/fotos/{id}/pano?anzahl=&zielAspect=` | Grenzen und Kanten des Pano-Splits |
| `GET /api/fotos/{id}/pano/{index}?anzahl=&zielAspect=&fokusY=` | einzelner Ausschnitt |
| `POST /api/fotos/carousel/zip` | ganzer Post als ZIP samt `post.txt` |
| `POST /api/fotos/carousel/export` | ganzer Post in den Export-Ordner |
| `GET`/`PUT`/`DELETE /api/entwurf` | Arbeitsstand lesen, sichern, verwerfen |
| `GET /api/hashtags` | Hashtag-Bank |
| `GET /api/hashtags/vorschlag?region=&genres=&presse=&reichweite=` | Vorschlag |
| `GET /api/stammorte?q=&fotoId=` | Orte suchen, nach Entfernung sortiert |
| `POST /api/stammorte` | Ort anlegen |
| `GET /api/archiv` | archivierte Posts |
| `POST /api/archiv` | Post archivieren und Arbeitsbereich leeren |
| `GET /api/archiv/{id}/bild/{datei}` | Bild aus dem Archiv |
| `DELETE /api/archiv/{id}/originale` | nur die Originale löschen |
| `DELETE /api/archiv/{id}` | Post vollständig löschen |
| `GET /api/info` | Startzeit, Laufzeit, Datenverzeichnis |
