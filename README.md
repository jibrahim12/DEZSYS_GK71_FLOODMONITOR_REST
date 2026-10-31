# DEZSYS GK71 - FloodMonitor REST

Das Projekt ist ein Hochwasser-Frühwarnsystem mit Spring Boot. Die Messstationen liefern Daten über eine REST-Schnittstelle, die sowohl JSON als auch XML über Content Negotiation unterstützt. Die Werte werden im Hintergrund simuliert, Warnstufen werden berechnet und ein Webclient mit jQuery visualisiert alles in einer Tabelle.

---

## 1. Startanleitung

Voraussetzung ist ein installiertes Java 17 (oder neuer).

Projekt über das Terminal starten:

./gradlew bootRun

Sobald der Server läuft, sind folgende Seiten erreichbar:

- Webclient Dashboard: http://localhost:8082
- Swagger UI (OpenAPI Doku): http://localhost:8082/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8082/v3/api-docs

Alle 12 Tests ausführen:

./gradlew test

---

## 2. Architektur und Datenmodell

Das Projekt ist in mehrere Schichten aufgeteilt, damit die Logik sauber getrennt bleibt:

- **model:** Hier liegen alle Datenklassen wie Station, Location, Measurement, StationStats und Alert. Dazu gehören auch die Enums WarningLevel und StationStatus. Alle Klassen haben XML-Annotationen von Jackson bekommen, damit die Ausgabe in XML und JSON gleich aussieht.
- **repository:** Die Klasse StationRepository speichert die Stationen und Messwerte im Arbeitsspeicher mit ConcurrentHashMap und CopyOnWriteArrayList, damit es keine Probleme mit Threads gibt, während der Simulator Werte reinschreibt.
- **service:** Der SimulationService kümmert sich um die zeitgesteuerte Berechnung der Werte und Warnstufen. Der StationService übernimmt das Filtern, Sortieren und die Statistikberechnung.
- **controller:** Im StationController liegen die REST-Endpunkte unter /api/v1. Er nimmt Anfragen an und entscheidet anhand des Accept-Headers, ob JSON oder XML geliefert wird.
- **exception:** Mit GlobalExceptionHandler und der Annotation ControllerAdvice werden Fehler wie 404 Not Found oder 400 Bad Request abgefangen, damit der Client saubere Fehlermeldungen ohne Stacktrace bekommt.
- **static:** Beinhaltet die index.html mit CSS und jQuery für den Webclient.

---

## 3. Simulationsregeln und Warnstufen

Die Simulation läuft alle 10 Sekunden automatisch im Hintergrund ab:

- Der Wasserstand kann niemals unter 0 fallen. Bei starkem Regen (über 3 mm/h) steigt der Wasserstand an. Wenn es nicht regnet, sinkt er langsam wieder.
- Ein höherer Wasserstand sorgt automatisch für eine höhere Durchflussmenge (ungefähr Wasserstand mal 2.3).
- Neue Messwerte bauen immer auf dem vorherigen Wert auf, damit sich die Werte nicht völlig zufällig verändern.
- Der Akku sinkt langsam ab. Sobald der Akkustand unter 15 Prozent fällt, schaltet die Station automatisch auf MAINTENANCE.
- Wenn eine Station OFFLINE ist, schaltet die Warnstufe auf UNKNOWN.
- Ansonsten wird die Warnstufe strikt nach Schwellenwerten berechnet: NORMAL wenn der Pegel unter dem Warnwert liegt, WARNING ab dem Warnwert und CRITICAL ab dem kritischen Grenzwert der jeweiligen Station.
---

## 4. REST-Schnittstelle und Content Negotiation

### Stationen abrufen
Standardmäßig liefert der Endpunkt JSON zurück:

curl -H "Accept: application/json" http://localhost:8082/api/v1/stations

Wenn XML angefordert wird:

curl -H "Accept: application/xml" http://localhost:8082/api/v1/stations

### Aktuellste Messung
Den neuesten Stand einer Station abfragen:

curl -H "Accept: application/json" http://localhost:8082/api/v1/stations/ST-001/measurements/latest

### Historische Messwerte mit Filter
Messwerte im Zeitraum mit Limit abrufen:

curl -G "http://localhost:8082/api/v1/stations/ST-001/measurements" --data-urlencode "from=2026-01-01T00:00:00Z" --data-urlencode "to=2026-12-31T23:59:59Z" --data-urlencode "limit=5"

### Fehlerfälle testen
Unbekannte Station liefert 404 Not Found:

curl -i http://localhost:8082/api/v1/stations/ST-999

Ungültiges Limit liefert 400 Bad Request:

curl -i "http://localhost:8082/api/v1/stations/ST-001/measurements?limit=2000"

Nicht unterstütztes Format liefert 406 Not Acceptable:

curl -i -H "Accept: application/pdf" http://localhost:8082/api/v1/stations

---

## 5. Webclient Screenshot

![Webclient Dashboard](docs/screenshots/Screenshot%202026-10-31%20015010.png)

---

## 6. Beantwortung der Fragestellungen

### 1. Was sind die Hauptmerkmale von JSON?
JSON steht für JavaScript Object Notation und ist ein textbasiertes Format, das extrem leichtgewichtig und einfach zu lesen ist. Daten werden in Schlüssel-Wert-Paaren und Arrays organisiert. Weil es direkt aus JavaScript kommt, können moderne Webbrowser und Clients JSON ohne zusätzliche Parser sofort verarbeiten. Es unterstützt Standard-Datentypen wie Strings, Zahlen, Booleans, Arrays und Objekte.
(Quelle: ECMA-404 The JSON Data Interchange Standard)

### 2. Was sind die Hauptmerkmale von XML?
XML ist eine Auszeichnungssprache, die hierarchisch über öffnende und schließende Tags aufgebaut ist. Im Vergleich zu JSON ist XML strikter und unterstützt komplexe Validierungen über XML-Schemata (XSD) oder DTDs. Außerdem bietet XML Namensräume (Namespaces), wodurch sich Datenstrukturen aus verschiedenen Quellen sauber kombinieren lassen, ohne dass es Namenskonflikte bei Tags gibt.
(Quelle: W3C XML Recommendation)

### 3. Wie unterscheidet die Applikation in diesem Beispiel, ob die Rückgabewerte in JSON oder XML zurückgegeben werden?
Die Entscheidung läuft über HTTP Content Negotiation. Wenn ein Client eine Anfrage schickt, setzt er den HTTP-Header Accept entweder auf application/json oder application/xml. Im StationController ist produces auf beide Formate eingestellt. Spring Boot liest den Header aus und wählt automatisch den passenden Jackson-Konverter aus: für XML den MappingJackson2XmlHttpMessageConverter und für JSON den normalen Jackson-Konverter. Fehlt der Header, wird standardmäßig JSON zurückgegeben.
(Quelle: Spring Boot Web Documentation, Content Negotiation)

### 4. Was zeichnet ein REST Interface aus und warum wird es heute sehr oft als standardisierte Schnittstelle verwendet?
Ein REST-Interface basiert auf den Architekturprinzipien von Roy Fielding. Die wichtigsten Merkmale sind Zustandslosigkeit (jeder Request enthält alle nötigen Informationen), die Trennung von Client und Server sowie ein einheitliches Interface, bei dem Ressourcen über URIs angesprochen werden. REST wird heute fast überall verwendet, weil es direkt auf den bewährten Standards des HTTP-Protokolls aufsetzt, von jedem System verstanden wird und keine komplizierten Zusatzprotokolle wie damals SOAP benötigt.
(Quelle: Roy Fielding, Architectural Styles and the Design of Network-based Software Architectures)

### 5. Was ist der Unterschied zwischen den HTTP-Methoden GET, POST, PUT und PATCH?
GET ist eine reine Leseoperation, die sicher und idempotent ist, also die Daten auf dem Server nicht verändert. POST wird genutzt, um neue Ressourcen anzulegen oder Daten zur Verarbeitung an den Server zu senden; POST ist weder sicher noch idempotent. PUT ersetzt eine vorhandene Ressource komplett durch den mitgeschickten Inhalt und ist idempotent, da mehrfaches Ausführen zum selben Zustand führt. PATCH führt im Gegensatz zu PUT nur eine teilweise Änderung an einer bestehenden Ressource durch.
(Quelle: RFC 7231 und RFC 5789 HTTP Semantics)

### 6. Ich will eine neue Request-Methode / Pfad in meinem REST Interface zur Verfügung stellen, welche Stellen des Quellcodes muss ich verändern?
Zuerst muss man im StationController eine neue Methode anlegen und mit der passenden Mapping-Annotation versehen, zum Beispiel mit PostMapping oder DeleteMapping. Falls dafür Fachlogik oder Filter nötig sind, ergänzt man die passende Methode im StationService. Braucht man neue Felder oder neue Speicheroperationen, passt man zusätzlich das Datenmodell und das StationRepository an. Zuletzt sollte man in den Integrationstests einen neuen Testfall schreiben, um den neuen Pfad per MockMvc abzutesten.

### 7. Kann man OpenAPI und Swagger vergleichen? Wenn ja, dann vergleichen Sie beide Themengebiete.
Ja, man kann die beiden gut vergleichen, weil sie eng zusammengehören: OpenAPI ist die herstellerunabhängige Spezifikation, also die formale Beschreibung, wie eine REST-API in YAML oder JSON dokumentiert werden muss. Swagger hingegen ist die konkrete Tool-Sammlung von SmartBear, die diesen Standard verwendet. Ein bekanntes Werkzeug daraus ist die Swagger UI, die aus der OpenAPI-Spezifikation eine interaktive Webseite im Browser generiert, auf der man Endpunkte direkt ausprobieren kann.
(Quelle: OpenAPI Initiative)

### 8. Wo liegt im folgenden Code-Snippet der Fehler? Begründe die Antwort.
Der erste Fehler ist die Verwendung von GetMapping für das Anlegen einer Station. Nach HTTP-Standard muss GET seiteneffektfrei sein und darf nichts auf dem Server speichern; stattdessen müsste hier PostMapping verwendet werden. Der zweite Fehler ist die URL: Im REST-Design nutzt man Substantive und keine Verben wie add in der URI. Der Pfad sollte einfach /stations lauten. Der Dritte Fehler ist der Rückgabewert: Ein einfaches String-Feedback mit Status 200 ist unsauber; korrekt wäre der Status 201 Created zusammen mit der neu erstellten Ressource im Body.

---

## 7. Reflexion und Prompt-Dokumentation

- Reflexion: Die größte Hürde war die korrekte Konfiguration von Jackson XML, damit Listen mit umschließenden Tags serialisiert werden und die Struktur exakt mit JSON übereinstimmt. Das Auslagern in eigene Wrapper-Klassen hat das Problem sauber gelöst. Auch die Trennung von ControllerAdvice und Servicelogik sorgt für sauberen Code ohne Stacktraces.
- Prompt-Dokumentation: Das Projekt wurde schrittweise mit Unterstützung entwickelt, um Architektur, physikalische Simulationsformeln, Exception-Handling, Tests und den Webclient strukturiert umzusetzen.