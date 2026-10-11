# Integracja z OsmAnd — notatki

## Potwierdzone publiczne API
`CalculateRouteParams` przyjmuje punkt startowy, końcowy i listy punktów pośrednich wraz z nazwami. `IOsmAndAidlInterface.calculateRoute(params)` zwraca `boolean`, nie obiekt geometrii trasy.

## Kolejność prac
1. Skonfigurować oficjalne AIDL API na podstawie przykładowej aplikacji OsmAnd.
2. Potwierdzić łączenie z właściwym pakietem OsmAnd zainstalowanym na telefonie.
3. Wysłać pojedyncze żądanie trasy i sprawdzić, czy OsmAnd wyświetla/wyznacza trasę.
4. Sprawdzić punkty pośrednie oraz różnice między OsmAnd i OsmAnd+.
5. Dodać zapis GPX i Android Sharesheet jako awaryjny, przewidywalny przepływ.
6. Dopiero później rozbudować algorytm do generowania kandydatów.

## Migracja z obecnego index.html
- `MinHeap`, `findPath`, `buildGraphFromOSM`: nie powinny dalej być głównym silnikiem trasowania.
- `fetchOSMData`: usunąć z głównej ścieżki routingu, aby nie odpytwać Overpass przy każdej trasie.
- `highwayCosts`: przenieść do oceny preferencji/kandydatów, o ile da się uzyskać odpowiednie dane.
- `exportToGPX`: przepisać na eksport Androidowy oraz udostępnianie pliku.
- Formularz HTML: przenieść do Compose.
