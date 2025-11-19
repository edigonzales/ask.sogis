## Mögliche Fragen

- Wos sind die Gewässerschutzzonen in Grenchen?
-> Wie weiss er welcher MCP er aufrufen muss????


- zeige eine karte mit den adressen und grundstücken in trimbach
- zeige den grössten pool in trimbach
- wer ist der vermessungsingenieur für winznau?
- meine pergola ist auf der karte nicht eingetragen, was muss ich tun?
- wer gibt mir auskunft zu bauten ausserhalb der bauzone in winznau?
- solarzellen potential für moosacker 5 in winznau



Wo ist Rüttenen?
         Features Found:
•	Rüttenen (SO) (Gemeinde) | Height: 612.7m
Gibt es eine Schule
•	Es scheint ein Problem mit der Suche nach Schulen in Rüttenen zu geben. Die verfügbaren Daten konnten keine Informationen liefern.
Wo ist ein KKW?
•	Könnten Sie bitte klären, welches KKW (Kernkraftwerk) Sie meinen? Es gibt mehrere in der Schweiz, wie zum Beispiel:
o	KKW Gösgen: In der Nähe von Däniken, Kanton Solothurn
o	KKW Leibstadt: In Leibstadt, Kanton Aargau
o	KKW Beznau: In Döttingen, Kanton Aargau
o	KKW Mühleberg: In Mühleberg, Kanton Bern (stillgelegt)
Bitte geben Sie an, welches spezifische KKW Sie suchen, damit ich Ihnen genauere Informationen geben kann

Solothurn Grundstück 100 gibt es das?
•	Es gibt keine spezifischen Informationen über ein Grundstück mit der Nummer 100 in Solothurn in den verfügbaren Daten.

Wo ist der Rötihof Solothurn?
•	Features Found:
o	Rötihof (SO) - Solothurn (Poi) | Height: 440m


### Kartenaktionen / API

#### Kartenausschnitt verändern (Pan und Zoom)

- Wahrscheinlich jeweils die Aktion auf eine "Wo ist etwas"-Frage.
- Die "Wo-ist-etwas"-Frage kann natürlich extrem vielfältig gestellt werden, z.B. "Zeige mir eine Karte mit ..." oder "Gibt es eine Schule in ..." 
- -> Intent-Classifier muss hier die Aktion eruieren. Eventuell auch mit einer simpleren Logik (zusätzlich).


#### Karten(ebenen) laden

- Aktion auf die Fragen "Zeige eine Karte ..." oder "Lade die Karte ...". Eventuell auch auf "Wo ist ein KKW?". Vielleicht unterscheiden "Wo ist Adresse o.ä." vs "Wo ist 'Fachobjekt'".

#### Keine Aktion

- Wenn LLM nachfragen muss oder rein inhaltiche Fragen wie "Wer ist Nachführungsgeometer in Winznau?".


#### Karten(ebenen) entfernen

- Wahrscheinlich nur auf konkrete Aufforderung seitens Benutzer.


### Ablauf

1. Benutzer tippt Frage ein.
2. Intent-Classifier für anschliessende Kartenaktion. 
3. Je nach Kartenaktion muss der Prompt für den MCP-Tool Call geschrieben werden (aber nicht welches MCP-Tool).
3. LLM entscheided selbständig welche(s) MCP-Tool(s) es einsetzt. (wie geht das programmatisch, wenn es z.B. zwei Calls machen muss? Bin ich dafür verantwortlich?)
4. MCP-Tool liefert Antwort, z.B. eine Coordinate.
5. Webanwendung weiss (bei Eindeutigkeit) mit Kartenaktion und Coordinate, was zu tun ist.