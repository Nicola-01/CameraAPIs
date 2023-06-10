# CameraAPIs
Progetto corso Embedded 9CFU - UNIPD


# Note varie provvisore
Se non vengono accettati i premessi prima compare il popup a schermo, se quello viene chiuso senza prima accettarli allora viene aperta una nuova activity che blocca l'accesso alla camera

La rotazione nella main Activity è stata bloccata, in questo modo non si deve ricreare l'activity ogni volta che si gira lo schermo, i tasti e la rotazione dell'immagini restano sempre attivi, indipendemente dalle impostazioni sulla rotazione del telefono, quindi l'orientamento della foto è sempre quello corretto, mentre per i video la rotazione con cui viene catturrato corrisponde a quella iniziale di quando si preme il tasto rec, quindi se si inizia il video il portrait e si gira in land questultimo resta comunque orientato in verticale, il blocco della rotazione però comporta che le schermate popup che copaiono a schermo dopo non si adattano al orientamento attuale del telefono ma restano sempre in portrait, questo non accade per la schermataq delle impostazioni e nel activity di quando non si accettano i permessi.
