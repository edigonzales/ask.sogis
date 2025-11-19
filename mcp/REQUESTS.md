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
