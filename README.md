# <img src="https://github.com/Nicola-01/CameraAPIs/blob/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Logo" width="60" height="60"> CameraPaw
## Introduzione 
Progetto sviluppato per il corso di `Programmazione di sistemi Embedded da 9CFU` presso l'Università degli studi di Padova.

Questo progetto utilizza CameraX per creare una applicazione fotocamera, chiamata **CameraPaw**.

## Funzioni
L'applicazione implementa tutte le funzioni necessarie per un utilizzo completo della fotocamera, questo è un breve elenco:
- Cambio camera tra anteriore e posteriore
- Cambio camera automatico tra grand angolare e untra grand angolare in base al valore di zoom, **non supportato da tutti i dispositivi**
- Flash On/Off/Automatico per le foto e possibilità di accenderlo e spegnerlo in manuale in modalità video
- Messa a fuoco tramite "tap to focus", quindi dove si preme sulla preview la camera mette a fuoco
- Timer regolabile per il tempo di scatto, spento, 3, 5 o 10 secondi
- Lettura codice QrCode, dopo la lettura viene mostrato un PopUp a schermo che in base al tipo di Qr cambiano le funzioni, tipi di Qr supportati: link a siti, testo e reti wifi
- È possibile modificare le impostazioni in base alle proprie esigenze
- Accesso a funziono avanzate: HDR, Bokeh e Night, **non supportato da tutti i dispositivi**

## Permessi
Per accedere alla fotocamera è necessario accettare sia il permesso del utilizzo della fotocamera che del microfono, nel caso non venissero forniti verrà mostrato un PopUp a schermo come primo avviso e se chiuso senza aver dato le autorizzazioni allora si verrà rindirizzati in un altra Activity che non permette l'utilizzo dell'app

## Avviso scelte implementative
Per la realizzazione di questa applicazione abbiamo deciso di manterne l'orientamento dell'applicazione verticale.
Questo ci ha permesso di mantenere i tasti in posizione fissa, quindi, ad esempio, il tasto di scatto è sempre nella parte inferiore dello schermo, mentre i tasti che consentono funzioni particolari, e solitamente meno usati sono stati posizionati nella zona posteriore, in questo modo quando si tiene il telefono per scattare una foto, dato che solitamente le fotocamere sono nel lato superiore del dispositivo non si rischia di mettere la mano su obbiettivo.
Inoltre è stato imposatato che i tasti e i PopUp vengano sempre livellati rispetto l'orizzonte, in modo che si loro che la foto siano comunque orientati correttamente quando si scatta una foto o registra un video.
**Nota Bene** L'orientamento delle altre activity invece dipende effettivamente dallo stato di rotazione di android.

## Shortcut
Nel launcher dell'app sono state aggiunte delle shortcut per facilitare l'utilizzo di determinate operazioni

## Output
Le immagini e i video ottenute dall'applicazione vengono salvati nella galleria nel percorso `DCIM/CameraPaw`

## Report e immagini esempio
Al interno del progetto è presente una cartella, nominata [Other](https://github.com/Nicola-01/CameraAPIs/tree/main/Other/) contenente il report e delle immagini di esempio ottenute tramite la nostra applicazione.

## Avviso compatibilità
Il corretto funzionamento è stato eseguito sul dispositivo **_Samsung Galaxy S21_**, mentre sugli altri dispositivi del gruppo non c'è piena compatibilità con tutte le funzioni, ma è comunque progettata per non crashare e permettere di usufruire delle funzioni non segnate sopra nel elenco

## Autori
- Nicola Busato
- Nicolas Brentel
- Tommaso Leoni
