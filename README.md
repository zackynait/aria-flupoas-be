# FLUPER 2.0 — backend API

Backend iniziale per il prototipo in `../fe/fluper2`. Le API sono versionate sotto `/api/v1`; specifica OpenAPI e Swagger UI sono esposte dall'applicazione.

## Login IDPC (integrazione Shibboleth)

Il flusso è predisposto ma rimane disabilitato finché non viene configurato il Service Provider Shibboleth dell'applicazione. ECM usa la libreria proprietaria `idpc-sp-integration-shibboleth` 2.2.03; non è inclusa in questo progetto, quindi questo backend si integra con uno SP Shibboleth esterno configurato per POAS/FLUPER e non costruisce SAML/XML né tratta direttamente l'asserzione.

1. Il FE richiama `window.FluperApi.login()` che naviga a `GET /api/v1/auth/login`.
2. Il backend redirige al login handler locale dello SP (`/Shibboleth.sso/Login`) con `target` uguale a `IDPC_CALLBACK_URL`.
3. Lo SP usa i metadati IDPC (`https://idpcwrapper.crs.lombardia.it/PublisherMetadata/SSOService`) e l'IdP registrato (il link `mainauth/SSLAuthServlet` è l'endpoint di login, non un endpoint API da chiamare con `fetch`). Dopo l'SSO, il reverse proxy protegge il callback e inoltra gli attributi autenticati.
4. `GET /api/v1/auth/callback` accetta solo gli attributi e il marker segreto iniettati dal proxy fidato; crea una sessione HTTP e redirige al FE. Il cookie è HttpOnly, SameSite=Lax, 30 minuti; impostare `IDPC_SECURE_COOKIE=true` in HTTPS.
5. `GET /api/v1/auth/me` restituisce codice fiscale e username della sessione; `POST /api/v1/auth/logout` la invalida.

Configurare prima nel reverse proxy/SP: endpoint `/Shibboleth.sso/Login`, callback HTTPS registrato, mapping `saml_attribute_codicefiscale` e `saml_attribute_nomeutente` (come `attribute-mapper.xml` ECM), rimozione di header omonimi provenienti dal client e iniezione di `X-IDPC-SP-VERIFIED` con un segreto. Il callback deve essere raggiungibile soltanto attraverso quel proxy/SP. `nomeUtente` può arrivare come `idpc.<username>`; il backend rimuove il prefisso `idpc.` come fa ECM.

Abilitazione tramite variabili d'ambiente (esempio da completare con URL/secret dell'ambiente):

```powershell
$env:IDPC_ENABLED = "true"
$env:IDPC_SP_LOGIN_URL = "https://<host-applicativo>/Shibboleth.sso/Login"
$env:IDPC_CALLBACK_URL = "https://<host-applicativo>/api/v1/auth/callback"
$env:IDPC_FRONTEND_URL = "https://<host-frontend>/index.html"
$env:IDPC_TRUSTED_PROXY_SECRET = "<secret-da-secret-manager>"
$env:IDPC_SECURE_COOKIE = "true"
mvn spring-boot:run
```

In locale il callback IDPC non funziona senza un SP registrato e raggiungibile dal browser. Non passare mai codice fiscale/username dal FE come parametri di login. Questa base autentica l'identità SSO, ma non implementa ancora il lookup POAS dei ruoli/enti: prima di esporre dati o funzioni reali va collegato il servizio autorizzativo POAS.

## Versioni

- Java 21 (LTS)
- Spring Boot 3.4.2
- Spring Web MVC e Bean Validation gestiti dal BOM Spring Boot
- Springdoc OpenAPI 2.8.4
- Maven Wrapper non ancora incluso; usare Maven 3.9+.

## Connessione SAS 9.4 tramite IOM

Il backend può usare il client Java IOM di SAS 9.4. Le dipendenze SAS sono nel profilo Maven opzionale `sas-iom` e vengono attivate con `-Dsas.iom.enabled=true`. I JAR sono proprietari e non sono distribuiti nel repository: recuperarli dall'installazione SAS Integration Technologies autorizzata per l'ambiente e installarli nel repository Maven locale o aziendale. Il profilo usa gli stessi artifact dichiarati dal backend legacy:

- `com.sas:sas-svc-connection:9.4` (Connection Factory IOM, proxy IOM e driver JDBC SAS `MVAConnection`)
- `com.sas:sas-core:9.4`
- `com.sas:sas-security-sspi:9.4`

Per registrarli nel repository Maven locale, eseguire `mvn install:install-file` per ciascun JAR, indicando il rispettivo `-Dfile`, `-DgroupId=com.sas`, `-DartifactId` e `-Dversion=9.4`, con `-Dpackaging=jar`. Per esempio:

```sh
mvn install:install-file -Dfile=/percorso/sas.svc.connection.jar -DgroupId=com.sas -DartifactId=sas-svc-connection -Dversion=9.4 -Dpackaging=jar
mvn install:install-file -Dfile=/percorso/sas.core.jar -DgroupId=com.sas -DartifactId=sas-core -Dversion=9.4 -Dpackaging=jar
mvn install:install-file -Dfile=/percorso/sas.security.sspi.jar -DgroupId=com.sas -DartifactId=sas-security-sspi -Dversion=9.4 -Dpackaging=jar
```

Avviare poi il backend con `mvn -Dsas.iom.enabled=true spring-boot:run`. Il profilo non si attiva per la build ordinaria. Verificare con l'amministratore SAS che i JAR distribuiti siano quelli previsti per la versione manutentiva installata e compatibili con Java 21 prima di abilitare IOM nell'ambiente applicativo.

Le versioni sono una baseline del prototipo e vanno confrontate con la Spring Boot Reference Implementation ARIA e con la versione aziendale di Springline 2 prevista per l'ambiente di rilascio.

## Avvio

```sh
mvn spring-boot:run
```

Frontend statico: configurare Live Server sulla porta 5500. Se la porta differisce, impostare `FLUPER_CORS_ALLOWED_ORIGINS` (origini separate da virgola, per esempio `http://localhost:5501`). In sviluppo è consentita anche l'origine `null` usata dalle pagine aperte direttamente con `file://`; rimuoverla dagli ambienti condivisi o di produzione. Backend su `http://localhost:8080`. Dopo aver modificato la configurazione CORS, riavviare l'applicazione.

## Endpoint iniziali

- `GET /api/v1/health`
- `GET /api/v1/catalogo`
- `GET /api/v1/rilevazioni`
- `GET /api/v1/enti` e `GET /api/v1/enti/tipologie`
- `GET /api/v1/conferimenti`
- `GET /api/v1/notifiche`
- `GET /api/v1/documenti`
- `GET /api/v1/assistenza`
- `GET /api/v1/governo/metriche`
- `GET /api/v1/reportistica`
- `GET /api-docs` e `/swagger-ui.html`

## Upload dei flussi A/B

Per uno schema nuovo eseguire `db/ddl_flussi_ab.sql`; se è già stato applicato il DDL staging precedente, usare `db/ddl_flussi_ab_migration.sql`. La migrazione elimina la colonna BLOB che conservava il file originale e crea la tabella per le righe normalizzate.

Il FE scarica il modello specifico con `GET /api/v1/conferimenti/templates/A` o `/B`. `POST /api/v1/conferimenti/upload` riceve multipart `flusso` (`A` o `B`), `periodo` (`AAAA-MM`), `tipoInvio` (`TOTALE` o `PARZIALE`) e `file`. Accetta `.xls` e `.xlsx` fino a 20 MB, verifica intestazioni, chiavi riga, periodo, duplicati e presenza dei dati; gli invii validi vengono salvati come righe in `FLUPER_RIGA_INVIO`, con i valori del tracciato in `DATI_JSON`.

Il template A esclude `COGNOME`, `NOME`, `SESSO` e `DATA_NASCITA`; il backend scarta gli stessi campi anche se riceve un vecchio file AS-IS che li contiene. Il codice fiscale resta la chiave identificativa. Il file originale non viene conservato: restano i metadati del file, le righe sanificate e le anomalie. I controlli non coprono ancora tutta la validazione di dominio, i controlli incrociati o la qualità SAS. L'upload richiede la sessione applicativa autenticata.

I dati attuali sono fixture dimostrative in memoria, ricavate dalle schermate del prototipo. Non sono dati persistenti e non simulano autenticazione, upload, validazione o workflow transazionali.

## Integrazione FE

`api-client.js` fornisce un client minimale basato su `fetch`. Caricarlo prima di `app.js`; la variabile `window.FluperApi` espone i metodi di lettura. L'URL base è configurabile tramite `window.FLUPER_API_BASE_URL` prima del caricamento del client.

## Springline 2 e rilascio ARIA

La guida Springline 2 e la Spring Boot Reference Implementation vanno applicate nella pipeline/ambiente ARIA con le dipendenze e gli starter aziendali autorizzati. La cartella delle linee guida non contiene gli artifact Maven Springline né un repository privato: non si inventano coordinate o versioni. Verificare con la piattaforma ARIA la release Springline supportata prima della promozione. La baseline non è dichiarata certificata ARIA.
