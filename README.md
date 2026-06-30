# ArchivoSync — Android

App Android (Kotlin moderno + Jetpack Compose) para **respaldar y compartir archivos** mediante varios mecanismos intercambiables: **API REST**, **almacenamiento en la nube** (S3 / FTP-SFTP / WebDAV / GCS) y **P2P** (estilo BitTorrent).

Forma parte de un sistema de tres repos:

| Repo | Rol |
| :--- | :--- |
| `pepe-android-files-administrator` | **Esta app** (cliente Android) |
| `pepe-api-files-administrator` | Backend REST (subida/listado) |
| `pepe-p2p-orquestrator` | Orquestador de transferencias P2P |

---

## Arquitectura

Clean Architecture en una sola capa de módulo, con tres anillos:

```
ui/            Compose + ViewModels (StateFlow)
  ├─ theme, i18n (ES/EN), components, navigation
  └─ screens/  dashboard · files · uploads · downloads · p2p · settings
domain/        Modelos puros + contratos (interfaces) + casos de uso
  ├─ model/        FileNode, TransferItem, P2pTransfer, AppSettings…
  ├─ repository/   SourceRepository, DestinationProvider, TransferRepository…
  └─ usecase/      BackupSelectionUseCase, TestConnectionUseCase, SeedSelectionUseCase
data/          Implementaciones
  ├─ local/        Room (checkpointing por archivo)
  ├─ settings/     DataStore (preferencias + secretos)
  ├─ remote/       Retrofit + OkHttp (subida con progreso por streaming)
  ├─ destination/  REST / Cloud / resolver  ← mecanismos enchufables
  ├─ source/       SAF (DocumentFile, scoped-storage)
  └─ repository/   Transfer / P2P
work/          WorkManager (BackupWorker en foreground service)
di/            Hilt (App, Database, Repository modules)
```

### Mecanismos de respaldo enchufables

Todo destino implementa `domain/repository/DestinationProvider`:

```kotlin
interface DestinationProvider {
    val channel: TransferChannel
    suspend fun test(settings): ConnectionResult
    suspend fun upload(settings, fileName, sizeBytes, input, onProgress): Result<String>
    suspend fun list(settings): Result<List<DownloadItem>>
}
```

`DestinationResolver` elige la implementación según `AppSettings.remoteType`. Añadir un mecanismo nuevo = una clase nueva + un caso en el resolver; **ni la UI ni el worker cambian**.

- `RestDestinationProvider` — HTTP. Listado vía Retrofit; subida vía `ProgressRequestBody` (streaming por chunks de 64 KiB, sin OOM en archivos de 200 MB+).
- `CloudDestinationProvider` — S3 / FTP-SFTP / WebDAV / GCS (transporte por proveedor enchufable).
- **P2P** — `P2pRepository` / `P2pRepositoryImpl` modela el enjambre (seed/leech, ratio, magnet) y delega el intercambio de piezas al orquestador externo.

### Resiliencia (respaldo en segundo plano)

- **WorkManager + Foreground Service** (`BackupWorker`): sobrevive al cierre de la app.
- **Checkpointing en Room**: cada archivo pasa `QUEUED → UPLOADING → DONE/FAILED`; al reanudar solo se procesa lo que no esté `DONE`.
- Progreso granular (~cada 5 %) y notificación de progreso.
- Archivos bloqueados/en uso → se marcan `FAILED` y la copia continúa.

### Acceso a archivos

Storage Access Framework con `DocumentFile` (`SafSourceRepository`): el usuario concede una carpeta por volumen (interno / SD / USB-OTG) con `OpenDocumentTree`, se persiste el permiso, y se recorre el árbol — **sin `MANAGE_EXTERNAL_STORAGE`** (compatible con políticas de Google Play).

---

## Stack

Kotlin 2.1 · Compose (BOM 2025.01) + Material 3 · Hilt · WorkManager · Room · DataStore · Retrofit/OkHttp + kotlinx.serialization · Navigation Compose · Coroutines/Flow. `minSdk 26`, `targetSdk/compileSdk 35`.

## Funcionalidades de UI

6 pantallas con barra de navegación inferior, bilingüe **ES/EN** (toggle en la barra superior) y acentos de color (azul/teal/púrpura/naranja):

- **Inicio** — estado de conexión, estadísticas, acciones rápidas, actividad reciente.
- **Archivos** — pestañas interno/SD, breadcrumbs, selección múltiple → barra de acción **Subir** o **Sembrar (P2P)**.
- **Subidas** — historial con filtros por estado, progreso y reintento.
- **Descargas** — archivos remotos disponibles + progreso.
- **P2P** — seed/leech, ratio, velocidades, magnet, pausar/reanudar.
- **Ajustes** — REST vs Nube, prueba de conexión, toggles generales, apariencia.

---

## Compilar

Requiere Android SDK (platform 35) y JDK 17.

```bash
# Generar local.properties con la ruta del SDK si no existe:
#   sdk.dir=/ruta/al/Android/Sdk
./gradlew :app:assembleDebug      # APK debug
./gradlew installDebug            # instalar en dispositivo/emulador
```

> Si clonas sin el wrapper jar, ejecútalo una vez con un Gradle instalado:
> `gradle wrapper --gradle-version 8.11.1` (o abre el proyecto en Android Studio).

---

## Configuración (en la app, pantalla Ajustes)

- **REST**: URL base, endpoint de listado (GET), endpoint de subida (POST), token.
- **Nube**: proveedor, host/bucket, access key, secret key.
- Los secretos viven en DataStore y se excluyen de los backups del sistema (`data_extraction_rules.xml`).

## Notas de seguridad

- Sin TLS no hay subida segura: usa HTTPS / SFTP / WebDAV-S.
- No se registran rutas completas ni contenido en Logcat.
- Permisos mínimos: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `WAKE_LOCK`.
