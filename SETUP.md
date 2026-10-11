# PostPilot einrichten — Schritt für Schritt

Diese Anleitung erklärt beide Wege von Grund auf: einmal lokal auf dem PC, einmal als
Dauerläufer auf der Synology. Du brauchst kein Vorwissen über Docker oder Webserver.

## Kurz vorweg: was hier eigentlich läuft

Die Anwendung ist ein einziges Java-Programm, das einen kleinen Webserver mitbringt.
Startest du es, horcht es auf Port 8080 und liefert die Oberfläche an den Browser aus.
Es gibt keinen externen Webserver, keine Datenbank und nichts zu registrieren.
„Port 8080" ist dabei nur eine Hausnummer auf deinem Rechner — `http://localhost:8080`
heißt „auf diesem Rechner, Tür 8080".

Alle Daten — Fotos, Stammorte, fertige Zuschnitte — liegen in **einem** Ordner. Wo der
liegt, steuerst du mit einer einzigen Einstellung. Das ist der wichtigste Unterschied
zwischen den beiden Varianten: lokal liegt er im Benutzerprofil, im Container zeigt er
auf einen Ordner der NAS.

---

# Variante A — lokal auf dem PC

## A1. Java installieren

Du brauchst ein JDK 21. Falls `java -version` in der Eingabeaufforderung schon
`21.x` meldet, überspring diesen Schritt. Sonst hol dir Temurin 21 von
[adoptium.net](https://adoptium.net) und installiere es mit den Vorgaben.

Prüfen: Eingabeaufforderung öffnen (Windows-Taste, `cmd` tippen, Enter) und eingeben:

```
java -version
```

Es muss `openjdk version "21...` erscheinen. Steht dort 17 oder 8, ist noch eine ältere
Version voreingestellt — dann in der Systemsteuerung unter „Umgebungsvariablen" `JAVA_HOME`
auf den Ordner von Java 21 zeigen lassen.

## A2. Das Programm bauen

Aus dem Quellcode wird eine einzelne Datei `postpilot.jar`. Zwei Wege, such dir einen aus.

**Weg 1 — in IntelliJ, ohne weitere Installation.** IntelliJ bringt Maven mit.

1. IntelliJ öffnen, „Open" wählen, den entpackten Projektordner auswählen (den mit der
   `pom.xml` darin), mit „Trust Project" bestätigen.
2. Rechts am Rand auf den Reiter „Maven" klicken.
3. Dort `postpilot` → `Lifecycle` aufklappen und auf `package` doppelklicken.
4. Beim ersten Mal lädt Maven die Bibliotheken aus dem Internet — das dauert ein paar
   Minuten. Am Ende steht unten „BUILD SUCCESS".

**Weg 2 — auf der Kommandozeile.** Setzt ein installiertes Maven voraus
([maven.apache.org](https://maven.apache.org/download.cgi), entpacken, `bin`-Ordner in
die `PATH`-Variable eintragen).

```
cd C:\dev\postpilot
mvn package
```

Fertig ist in beiden Fällen die Datei `target\postpilot.jar`.

## A3. Starten

Eingabeaufforderung im Projektordner öffnen und eingeben:

```
java -jar target\postpilot.jar
```

Im Fenster rauschen ein paar Zeilen durch. Wichtig ist die Zeile mit
`Tomcat started on port 8080` und darunter die Angabe deines Datenverzeichnisses.
Wenn beim ersten Start die Windows-Firewall fragt, reicht „Abbrechen" beziehungsweise
„Zugriff verweigern" — für `localhost` braucht es keine Freigabe.

Jetzt im Browser aufrufen:

```
http://localhost:8080
```

Die JAR-Datei kannst du übrigens hinlegen, wohin du willst — sie ist selbstständig.
Ein Doppelklick startet sie allerdings ohne sichtbares Fenster; nimm lieber den Weg
über eine kleine Startdatei (siehe A6).

## A4. Beenden

Im Konsolenfenster `Strg + C` drücken oder das Fenster schließen. Damit ist die
Anwendung weg; die Daten bleiben natürlich liegen.

## A5. Wo deine Daten liegen

```
%APPDATA%\PostPilot\
   fotos\           die hochgeladenen Originale
   export\          die fertigen Zuschnitte, nach Datum sortiert
   stammorte.json   deine Ortsliste, ganz normal im Editor bearbeitbar
```

`%APPDATA%` kannst du im Explorer direkt in die Adresszeile tippen. Der Ordner wird beim
ersten Start angelegt, die Stammorte-Datei mit fünf Beispielorten aus deiner Ecke gefüllt.

## A6. Einstellungen, die du vielleicht brauchst

Alles lässt sich beim Start mitgeben, ohne im Code etwas zu ändern:

```
:: anderer Port, falls 8080 belegt ist
java -jar postpilot.jar --server.port=9090

:: Daten woanders ablegen, etwa auf einer zweiten Platte
java -jar postpilot.jar --postpilot.data-dir=D:\PostPilot
```

Damit du das nicht jedes Mal tippst, leg neben die JAR-Datei eine Textdatei
`start-postpilot.cmd` mit diesem Inhalt:

```
@echo off
start "" http://localhost:8080
java -jar "%~dp0postpilot.jar"
```

Ein Doppelklick darauf startet die App und öffnet gleich den Browser. Wenn sie bei jedem
Anmelden bereitstehen soll: `Windows-Taste + R`, `shell:startup` eingeben und eine
Verknüpfung dieser Datei in den Ordner legen.

## A7. Wenn etwas klemmt

*„Port 8080 was already in use"* — ein anderes Programm hat die Hausnummer belegt.
Starte mit `--server.port=9090` und ruf `http://localhost:9090` auf.

*„UnsupportedClassVersionError"* — es läuft noch ein altes Java. Prüf `java -version`
(siehe A1).

*Die Seite bleibt weiß* — schau ins Konsolenfenster. Steht dort eine rote Fehlermeldung,
schick sie mir; sonst hilft `Strg + F5` im Browser.

---

# Variante B — auf der Synology DS218+ (DSM 7.1)

Ausführliche Fassung mit allen Klickwegen: `EINRICHTUNG.html`, Abschnitt B.

**Kein Docker Desktop nötig.** Statt ein eigenes Container-Abbild zu bauen, wird ein fertiges
Java-Abbild geladen und die `postpilot.jar` aus einem Ordner hineingereicht. Das spart den
Build auf der schwachen Hardware und kommt ohne den Container Manager aus, den es erst ab
DSM 7.2 gibt.

1. **Docker-Paket** im Paket-Zentrum installieren (auf DSM 7.1 heißt es noch „Docker“).
   Es legt den freigegebenen Ordner `docker` an; fehlt er, in der Systemsteuerung anlegen.
2. **Ordner anlegen:** `docker/postpilot/app` (für das Programm) und `docker/postpilot/data`
   (für Fotos, Stammorte, Exporte).
3. **Bauen und kopieren:** auf dem PC `mvn package`, dann `target/postpilot.jar` nach
   `docker/postpilot/app/` hochladen. Der Dateiname muss so bleiben.
4. **Abbild laden:** Docker → Registrierung → `eclipse-temurin` suchen → Tag `21-jre` wählen
   (rund 200 MB).
   Meldung „Registrierung konnte nicht abgefragt werden“? Das ist fast immer DNS auf der NAS:
   Systemsteuerung → Netzwerk → Netzwerkschnittstelle → LAN → Manuell konfigurieren, DNS auf die
   Router-Adresse oder `9.9.9.9` setzen; außerdem Uhrzeit prüfen. Kommt die NAS ins Internet und
   nur die Suche hakt, per SSH `sudo docker pull eclipse-temurin:21-jre`.
5. **Container anlegen:** Abbild markieren → Starten → Name `postpilot` → Erweiterte
   Einstellungen:
   - Automatischen Neustart aktivieren
   - Volume: `docker/postpilot/app` → `/app` (schreibgeschützt), `docker/postpilot/data` → `/data`
   - Port: lokal `8095`, Container `8080`, TCP
   - Umgebung: Befehl `java -Xmx768m -jar /app/postpilot.jar`,
     Variablen `POSTPILOT_DATA_DIR=/data` und `TZ=Europe/Berlin`
6. **Aufrufen:** `http://ITwareNAS:8095` (oder die IP der NAS).
7. **Aktualisieren:** neue JAR hochladen, Container neu starten. `data` bleibt unangetastet.

Dasselbe per SSH in einem Befehl:

```
sudo docker run -d --name postpilot --restart unless-stopped \
  -p 8095:8080 \
  -v /volume1/docker/postpilot/app:/app:ro \
  -v /volume1/docker/postpilot/data:/data \
  -e POSTPILOT_DATA_DIR=/data -e TZ=Europe/Berlin \
  eclipse-temurin:21-jre java -Xmx768m -jar /app/postpilot.jar
```

**Häufige Fehler:** Container startet und stoppt sofort → Dateiname oder Pfad im Befehl falsch.
Seite lädt nicht → Ports vertauscht (außen 8095, innen 8080). Nichts wird gespeichert →
Schreibrechte auf `data` prüfen. Protokoll: Container → Details → Protokoll.

`Dockerfile` und `docker-compose.yml` liegen weiterhin im Projekt. Sie werden erst interessant,
wenn die App auf DSM 7.2+ oder kräftigerer Hardware läuft — dann baut der Container Manager die
Anwendung selbst.

---

# Beide Varianten gleichzeitig?

Geht problemlos — es sind zwei getrennte Installationen mit getrennten Daten. Praktisch
ist die Aufteilung: auf dem PC entwickeln und ausprobieren, auf der NAS die Fassung, die
du wirklich benutzt.

# Was noch nicht drin ist

Die Planung über Metricool fehlt bewusst noch. Dafür sind zwei Dinge zu klären: Metricool
holt Bilder nur von einer öffentlich erreichbaren Adresse ab, und einen Instagram-Standort
nimmt es nur bei automatischer Veröffentlichung an. Bis dahin ist der Weg: in PostPilot
zuschneiden, exportieren, und die Dateien aus dem `export`-Ordner von Hand in den
Metricool-Planer ziehen.
