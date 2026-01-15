```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"sessionId":"CA7AAAEB-5DBD-47E8-87DB-60FAB31DC150","userMessage":"Zeige mir die Adresse Langendorfrasse 19b in Solothurn."}' \
  http://localhost:8080/api/chat | python3 -m json.tool
```

```
curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"sessionId":"CA7AAAEB-5DBD-47E8-87DB-60FAB31DC150","userMessage":"Zeige mir die Adresse Langendorfrasse 19b."}' \
  http://localhost:8080/api/chat | python3 -m json.tool
```



### uc 1
- Zeige mir burgunderstrasse 19 in solothurn
- Ah, ich meinte nummer 9

### uc 2
- zeige mir die gewässerschutzzonen in Grenchen

### uc 3
- welche karten zum thema wasser hast du?
- Lade karte "ch.afu..."

### uc 4
- gehe zu ...
- wie tief kann ich hier für eine erdwärmesonde bohren?

- Wie tief kann ich an der Koordinate 2605919, 1229245 für eine Erdwärmesonde bohren?

- Wie tief kann ich an der Langendorfstrasse 19b in Solothurn für eine Erdwärmesonde bohren?


### uc 5
- gehe zur langendorfstrasse in solothurn
-> 25 Treffer
-> Wie umgehen? Entweder Strasse suchen und anzeigen oder wie ux/ui wenn mehrere Treffer?

### uc 6
- Erstelle mir einen Öreb-Auszung vom Grundstück 168 in Messen

### uc 7
- Erstelle mir einen Öreb-Auszug an der Adresse Langendorfstrasse 19b in Solothurn.
(adresse -> coord -> egrid -> auszug)

### uc 8
- Mache mir einen Grundbuchplan des Grundstücks 123 in Messen.
  (grundstücksnummer + gemeinde -> egrid/geometry -> grundbuchplan PDF)
  
- Ich möchte einen Grundbuchplan für die Langendorfstrasse 19b in Solothurn.
