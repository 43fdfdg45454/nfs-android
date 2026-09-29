# CLAUDE.md — nfs-android

Lineamientos vigentes.

## Proyecto

App Android que monta exports NFSv4.2 y ofrece sus archivos a cualquier otra app a través del
selector del sistema, con prioridad en el streaming de audio y video y en las subidas por una VPN
con pérdida. Licencia GPL-3.0.

- El protocolo, los transportes, el pipeline de bloques y la caché están en **nfs-core**
  (`43fdfdg45454/nfs-core`, Rust, sin nada propio de Android). Su `CLAUDE.md` describe la
  arquitectura y el plan completo. Todo lo propio de Android vive acá, incluido el puente al
  núcleo.

## Plan (etapas de nfs-android)

Empieza cuando nfs-core pasa su etapa 1 (validación del transporte) y la API del núcleo de la
etapa 2 está estable.

5. **App** (en curso): Kotlin y Jetpack Compose, Android 13+ (API 33). Servidores con host, puerto, export,
   transporte (QUIC o TCP, fijo por servidor), seguridad (ninguna, TLS, mTLS), certificado de
   KeyChain, certificado del gateway, UID/GID, solo lectura, caché y lectura adelantada. Servicio en
   primer plano con su notificación, monitor de red, diagnóstico, `nfs-log.txt` y `crash-log.txt`.
   Firma y release.
6. **DocumentsProvider**: una raíz por servidor; listar, abrir con saltos
   (`openProxyFileDescriptor`), crear, escribir, renombrar, borrar, miniaturas y aviso de cambios.
   Pasa si VLC y el reproductor del sistema abren, saltan y reproducen por la VPN en un teléfono
   real.
7. **Uso diario**: en el teléfono, con mediciones.

## Código

- `app/src/main/kotlin/io/github/nfsandroid/`: `data` (servidores, `servers.json` con formato),
  `core` (montajes, confianza TLS, identidad de KeyChain, conexión), `service` (servicio en primer
  plano), `provider` (DocumentsProvider: ids `servidor:ruta`, proxy de descriptores), `ui`
  (Compose), `log` (`nfs-log.txt`: una línea por evento con nivel, categoría, servidor y campos
  `clave=valor`, nivel por servidor en Avanzados; `crash-log.txt`).
- `rust/` (nfs-ffi): el puente al núcleo con UniFFI (montaje, archivos, identidad de KeyChain con
  firma delegada). Depende de nfs-core por git a un commit fijo (`rev` en `rust/Cargo.toml`): se
  adelanta a propósito, con la CI de nfs-core en verde. `ci/core.sh <parte>` compila `libnfscore.so`
  (cargo-ndk, `arm64-v8a` o `x86_64`) o genera los bindings de Kotlin (`bindings`) en `:core`;
  en la CI, cada parte en su runner a la vez.
- Versiones con GitVersion (`GitVersion.yml`, `ci/version.sh`): cada commit en `master` es un parche
  más desde la última etiqueta `v*`; `+semver: minor` o `+semver: major` en el mensaje suben más.
  Cada push a `master` con la CI en verde publica un release de GitHub con el APK (`ci/release.sh`).
- CI: privacidad, núcleo (fmt, clippy, `.so` y bindings), compilación (APK `NFS-<versión>.apk`) y
  emulador (API 34, desde un snapshot en caché) contra nfsd del runner con los scripts y fixtures
  de nfs-core: un emulador por grupo de clases de `ci/shards.txt` a la vez (`ci/shards.sh` exige
  cada clase en un solo grupo), APK compilados mientras arranca y `am instrument` sin Gradle
  (`ci/emulator.sh <grupo>`). Las pruebas usan el provider por `ContentResolver` como otra app: lo
  básico (`ProviderTest`), los escenarios del motor por el proxy de archivos con el reproductor
  estricto (`ScenariosTest`, tiempos informados), modos de apertura, miniaturas y caché
  (`ModesTest`), avisos de cambios de otro cliente (`WatchTest`), copiar y mover en el servidor sin
  tráfico (`TransfersTest`), enlaces simbólicos y rutas (`LinksTest`), seguridad de los enlaces
  (`LinkEscapesTest`: nada fuera del export ni en bucle; `LinkRightsTest`: permisos como por la
  ruta real con un usuario común, borrar o copiar un enlace conserva su destino; árboles de
  `ci/links.sh`) y subidas con escrituras de 8 KiB por una copia local y por el proxy, y lo que una
  app reescribe o acorta antes de cerrar (`UploadTest`, con la línea de cada subida en el log),
  y el formato y los niveles del log (`LogTest`). Resultados por logcat (`nfs-test`) a una
  anotación por grupo.
- Seguridad (`security.yml`, en cada push y a diario): `cargo deny` sobre `rust/`, secretos en el
  historial (`ci/secrets.sh`), zizmor sobre los workflows, CodeQL (Kotlin, Rust, Actions); los
  chequeos de seguridad de Android lint son fatales en el release. Acciones fijadas por commit.
- Decisiones tomadas sin consulta: `DECISIONES.md`.

## Forma de trabajo

- **Nada se ejecuta en la computadora del usuario.** Todo corre en el contenedor de la nube o en
  GitHub Actions.
- Siempre sobre la rama `master`.
- Respuestas en español; explicaciones completas cuando se pide detalle, sin relleno. Código,
  comentarios y mensajes de commit en inglés.
- Las sugerencias del usuario se evalúan antes de seguirlas: si no parecen el camino correcto, se
  dice por qué.
- Salida mínima: generar solo lo necesario, sin texto de más.
- Antes de cada CI: qué cambió, qué se espera y cuánto tardará aproximadamente.
- Antes de un análisis o de una tanda de cambios: qué se investiga y qué se busca encontrar; al
  terminar, qué se encontró.
- Commits con las líneas de atribución que indique la sesión.
- `README.md` útil y al día para quien llega al repositorio: qué es y por qué (con las mediciones),
  cómo instalarlo, configurarlo y usarlo, qué hacer cuando algo falla, qué garantiza (pruebas,
  seguridad) y cómo se desarrolla. Se actualiza en el mismo commit que cambia algo de eso. En
  inglés, como el código; sin datos de la instalación del usuario (ejemplos con `example.net`).

## Privacidad: el repositorio es público

- **Terminantemente prohibido** publicar detalles de la instalación del usuario: IPs, nombres de
  host o dominios, rutas del servidor o de los exports, usuarios y UID/GID reales, modelo o sistema
  del teléfono, proveedor o ancho de banda contratado, topología de la red o de la VPN,
  certificados, claves, contraseñas o tokens. Vale para código, pruebas, documentación, mensajes
  de commit, issues, PRs y anotaciones o logs de la CI.
- Los ejemplos usan solo direcciones y nombres reservados para documentación: `192.0.2.0/24`,
  `198.51.100.0/24`, `203.0.113.0/24`, `2001:db8::/32` y `example.net` (RFC 5737, 3849, 2606).
  Las redes simuladas de la CI usan direcciones propias de la prueba.
- Certificados y claves de prueba: se generan en la CI en cada corrida y se descartan. Las claves
  reales van solo como secrets de GitHub.
- Lo que el usuario cuente de su servidor, su red o sus logs se usa en la conversación y nunca
  termina en un archivo, un commit ni un mensaje. Estos lineamientos hablan de "el servidor" y de
  "un teléfono" en general.
- Antes de cada commit, revisar el diff buscando esos datos. Si algo se filtró, se corrige y se
  reescribe el historial (force push) en el momento.

## Buena experiencia de uso

Los límites son de buena experiencia, no de "funciona". Toda prueba de rendimiento los exige; donde
los fija la plataforma y no el código (la CPU del emulador), se informan sin exigirlos.

- Abrir un archivo (hasta el primer byte): ≤ 1,5 s.
- Salto dentro de un video: ≤ 1 s en promedio y ≤ 3 s siempre.
- Cerrar un archivo: ≤ 300 ms.
- Volver a algo ya visto: ≤ 100 ms (sale de la caché).
- Reproducción sin cortes: lector a 1 MB/s (1080p) con 2 s de colchón.
- Todo eso por el perfil VPN de referencia: 100 ms de RTT, 0,3 % de pérdida, MTU 1420, 100 Mb/s.

## Estilo de código

- La menor cantidad de código posible.
- Archivos de hasta 100 líneas. Es un límite blando: se pasa solo si dividir el archivo empeora su
  lectura.
- Responsabilidades claras: cada archivo, clase, objeto y modelo hace una cosa.

## Pruebas y CI

- Las pruebas tienen que ser lo más rápidas posible: se reutiliza todo lo que se pueda (entorno,
  servidor, fixtures, compilaciones en caché) y se paraleliza al máximo (matrices, shards, jobs).
- Los minutos de CI no tienen costo en un repositorio público: se usan todos los recursos que
  hagan falta, priorizando siempre el tiempo total por sobre el costo. 20 minutos es el máximo
  aceptable.
- Después de cada push, consultar el estado cada 30 segundos, nunca con esperas largas. Antes de
  ponerse a esperar, explicarle al usuario en un par de palabras qué cambió y qué se espera.
- Los logs de Actions no se pueden leer (403): los resultados se publican como anotaciones
  (`::notice` / `::error`, saltos de línea como `%0A`, sin comas en el título).
- En los scripts: `set -o pipefail` delante de cualquier `| tee`; con `sudo`, pasar `PATH` y
  `GITHUB_ACTIONS` explícitamente.

## Lo que hay que saber de Android

- Android (15+ siempre; antes, con ahorro de datos o restricciones de batería) bloquea las
  conexiones nuevas de una app en segundo plano, y la app lo está mientras un reproductor muestra
  el video. Hace falta un **servicio en primer plano** (`specialUse`) mientras haya conexiones, que
  se inicia con la app en pantalla; si Android lo rechaza, se anota en el log.
- Los cierres inesperados se guardan en `Android/data/<paquete>/files/crash-log.txt` y el detalle de
  la red en `nfs-log.txt`: se trabaja sin adb.
- El certificado de cliente sale de `KeyChain` y la clave nunca sale de ahí (en rustls, con firma
  delegada). Se confía en las CA del sistema y del usuario.
- La configuración guardada de los servidores lleva un marcador de formato y sigue leyendo las
  versiones anteriores.
- Los límites de buena experiencia se exigen en las pruebas del host (nfs-core). En el emulador
  se informan: ahí los fijan su CPU y el proxy FUSE.
