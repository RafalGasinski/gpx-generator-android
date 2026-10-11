# GPX Generator Android — starter

Szkielet osobnej aplikacji Android do generowania punktów kontrolnych i przekazywania trasy do OsmAnd.

## Ważne ograniczenie API
Publiczne AIDL `calculateRoute(CalculateRouteParams)` przyjmuje start, metę i punkty pośrednie, ale zwraca tylko `boolean` (czy żądanie zostało przyjęte), a nie geometrię wyznaczonej trasy. To wystarcza do przekazania trasy do OsmAnd, ale nie do automatycznej oceny dziesiątek wariantów na podstawie geometrii wynikowej. Najpierw należy sprawdzić AIDL na zainstalowanej wersji OsmAnd.

Źródła:
- https://osmand.net/docs/technical/osmand-api-sdk/
- https://github.com/osmandapp/OsmAnd/blob/master/OsmAnd-api/src/net/osmand/aidlapi/IOsmAndAidlInterface.aidl
- https://github.com/osmandapp/OsmAnd/blob/master/OsmAnd-api/src/net/osmand/aidlapi/calculateroute/CalculateRouteParams.java

To jest szkielet, nie gotowa wersja z działającym AIDL. Szczegóły integracji są w `docs/INTEGRATION_NOTES.md`.

## Licencja oryginału
Podczas sprawdzenia nie znaleziono `LICENSE` w głównym katalogu oryginalnego repozytorium. Publiczny GitHub sam w sobie nie daje prawa do kopiowania/relicencjonowania kodu. Ustal prawa do oryginalnego kodu przed przeniesieniem go do tej aplikacji.
