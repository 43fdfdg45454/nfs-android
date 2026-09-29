# Decisiones para revisar

Decisiones vigentes tomadas sin consulta. Cada una dice qué se decidió y por qué.

1. **Id de la app:** `io.github.nfsandroid`, nombre "NFS".
2. **UI en Compose con tema propio** (índigo a cian, como el ícono; claro u oscuro según el
   sistema), en tres pestañas: Servidores (resumen en vivo y una tarjeta por servidor, con
   "Explorar" que abre el selector del sistema en su raíz), Actividad (red y cada servidor en
   detalle, compartir el log) y Ajustes (caché, notificación, acerca de). La edición ocupa toda la
   pantalla, por secciones con ayuda (conexión, seguridad, identidad, rendimiento, acceso, probar),
   con validación y confirmación al descartar o quitar. Los archivos se ven y se abren desde el
   selector del sistema (DocumentsProvider), no con un explorador propio.
3. **Servidores en `files/servers.json`** con un número de formato: las versiones siguientes
   tienen que seguir leyendo lo que guardaron las anteriores (campos nuevos con valor por defecto).
4. **Confianza TLS:** la app le pasa al núcleo las CA de `AndroidCAStore` (del sistema y del
   usuario). La configuración de red también confía en las del usuario.
5. **Certificado de cliente:** se elige con `KeyChain.choosePrivateKeyAlias`. La clave no sale de
   KeyChain: cada firma del handshake se hace en Kotlin (`KeyChainIdentity`). El mismo certificado
   sirve para el QUIC exterior del gateway y para el mTLS del export.
6. **Un montaje por servidor**, conectado al primer uso y cerrado sin uso a los 5 minutos (o lo
   que diga Avanzados). El
   servicio en primer plano (`specialUse`) corre mientras haya alguno.
7. **Owner del cliente NFS:** id de instalación (UUID guardado) + id del servidor.
8. **Un hilo por descriptor abierto** en el proxy de archivos: dos reproductores no se esperan.
9. **Firma del release:** con los secrets `NFS_KEYSTORE_BASE64` / `NFS_KEYSTORE_PASSWORD` de
   este repositorio (alias `nfs`); sin ellos, con la clave de debug del runner,
   que cambia en cada corrida (el APK no actualiza al anterior), y no se publica ningún release.
10. **Emulador de la CI:** API 34 x86_64; alcanza nfsd del runner en `10.0.2.2` (alias estándar
    del emulador; permitido en el chequeo de privacidad).
11. **Cerrar y abrir, entre apps:** Android libera el descriptor de escritura un momento después
    de que la app lo cierra, y lo último escrito recién llega al servidor ahí. Toda apertura de
    un documento espera hasta 3 s a que terminen sus escritores abiertos. Costo: abrir un archivo
    mientras otra app lo escribe demora hasta 3 s.
12. **Modos de apertura:** `r` lee; `w`/`wt` crean o vacían; `rw` lee y escribe en el lugar;
    `rwt` vacía y después lee y escribe; `wa` agrega al final (el proxy de archivos no conoce
    `O_APPEND`: el descriptor se entrega posicionado al final). `fsync` confirma en el servidor.
13. **Caché global:** una sola caché en disco (`cacheDir/nfs`, Android puede vaciarla si le falta
    espacio) para todos los servidores, de 4 GB por defecto (0 la desactiva; nunca deja menos de
    1 GB libre), con uso y botón para vaciarla; "Usar la caché local" la apaga por servidor. Un
    tamaño nuevo vale para las conexiones que se hagan después.
14. **Miniaturas:** imágenes (submuestreadas), videos (un cuadro cerca del primer segundo, leído
    por el núcleo sin proxy) y audio (la tapa), de a dos a la vez para no competir con la
    reproducción, guardadas por documento, fecha y tamaño.
15. **Cambios de otros clientes:** las carpetas listadas en los últimos 2 minutos se revisan cada
    15 s (un GETATTR cada una) y, si cambió su fecha de modificación, se avisa a quien las muestra.
    Pasados 2 minutos sin listar ninguna, no se revisa nada. nfsd no ofrece delegaciones de
    carpetas (`CB_NOTIFY`), así que no hay aviso del servidor.
16. **Cambio de red:** el monitor de la red por defecto pide al núcleo reconectar al instante
    cuando cambia la red o sus direcciones, y anota en el log cuándo Android bloquea la red de la
    app. El diagnóstico muestra la red, los servidores conectados, los avisos de camino de
    callbacks caído, y comparte los últimos 64 KB del log como texto.
17. **Servidor nuevo con UID/GID 1000:** el primer usuario habitual de un Linux (0 es root; con
    `root_squash` quedaría como nobody).
18. **Notificación técnica:** título con bajada y subida; texto con servidores, conexiones
    establecidas y llamadas en vuelo; expandida, una línea por servidor (transporte, conexiones,
    en vuelo, RTT y pérdida por QUIC, archivos abiertos, reconexiones). Se actualiza cada 2 s.
19. **Espacio en los selectores:** cada raíz publica espacio disponible y total (lo que muestran
    los gestores de archivos que lo leen). El último valor conocido se guarda; se vuelve a preguntar en segundo
    plano, a lo sumo cada 10 s, solo si el servidor ya está conectado.
20. **Versiones con GitVersion** (`GitVersion.yml`, flujo trunk-based): cada commit en `master` es
    un parche más desde la última etiqueta (`+semver: minor` o `major` en el mensaje suben más);
    `versionCode` = mayor·10⁶ + menor·10³ + parche. Cada push a `master` con todo en verde publica
    un release `vX.Y.Z` con el APK firmado y los commits desde el anterior. Empieza en 1.0.0 (`next-version`): las etiquetas las crea cada release.
21. **Servidor deshabilitado:** se conserva con su configuración, sale de los selectores de
    archivos y no se conecta por ningún camino (abrir, listar, vigilar carpetas). Deshabilitarlo
    cierra sus conexiones en el momento, aunque haya archivos abiertos.
22. **Servidor que no contesta (la VPN caída):** sin primera respuesta en 4 s falla; lo lento pero vivo tiene hasta 15 s, y cada llamada del
    provider 20 s; tras una falla, durante 30 s todo pedido a ese servidor falla al instante, y un
    cambio de red (la VPN que vuelve) lo borra para reintentar enseguida. Cada servidor conecta por
    su lado: uno que no contesta no demora a los demás. Su tarjeta lo muestra.
23. **Red de cada servidor:** cualquiera, VPN o Wi-Fi/cable, y opcionalmente la subred que esa red
    le da al teléfono (cuál VPN, cuál Wi-Fi: Android no deja ver de qué app es la VPN, y el nombre
    del Wi-Fi pide el permiso de ubicación). Sin esa red el servidor falla al instante; si se va con
    el servidor conectado, se cierra. Dos Wi-Fi con la misma subred cuentan como la misma red.
24. **Batería sin tocar el rendimiento:** las estadísticas en vivo se calculan solo mientras hay
    servidores conectados o una pantalla las muestra; la notificación se vuelve a publicar solo si
    cambió y, con la pantalla apagada, solo si cambió la cantidad de servidores conectados. El
    núcleo lee por adelantado en tandas y QUIC manda pings cada 25 s (DECISIONES de nfs-core).
25. **Seguridad en la CI** (`security.yml`, en cada push y a diario): `cargo deny` sobre el puente,
    secretos en el historial (gitleaks), zizmor sobre los workflows, CodeQL (Kotlin, Rust, Actions)
    y los chequeos de seguridad de Android lint como fatales en el release. Workflows con acciones
    fijadas por commit, sin credenciales guardadas y de solo lectura salvo el release. Solo TLS 1.3.
26. **Sin backup ni transferencia de datos de la app:** la lista de servidores tiene sus
    direcciones y el nombre del certificado de cliente; al cambiar de teléfono se cargan de nuevo.
27. **Permisos de lo que se crea, por servidor** (Identidad): tres opciones en vez de un número,
    como máscara (umask) para el núcleo: estándar `022` (644 y 755, por defecto), grupo `002`
    (664 y 775, para carpetas compartidas con setgid), privado `077` (600 y 700) y "otro", una
    máscara en octal con los permisos que resultan a la vista (por ejemplo `027`: 640 y 750).
28. **Copiar y mover dentro de un servidor, en el servidor** (`copyDocument`, `moveDocument`):
    mover es un RENAME; copiar, CLONE y si no, COPY en tramos de 64 MiB (carpetas enteras, los
    enlaces como enlaces). Si el servidor no puede, "no soportado" sin dejar nada, y el gestor de
    archivos copia por su cuenta; entre servidores, siempre así. Un nombre ocupado en el destino
    da "nombre (1).ext", nunca se pisa. Hasta 30 min por copia (una llamada, sin progreso).
29. **Enlaces simbólicos seguidos dentro del export**, como un montaje del kernel: relativos y
    absolutos bajo la ruta del export en el servidor; hasta 40 saltos. Uno fuera del export, en
    bucle o a su propia carpeta o una de más arriba (salvo en Avanzados) se lista como el enlace
    que es y no se abre. Cada tramo sin enlaces es una llamada (`Client::walk`); un enlace, un
    READLINK más.
30. **La ruta de un documento sale de su id** (`findDocumentPath`), sin llamar al servidor.
31. **Escrituras consecutivas juntadas en 1 MiB** antes del núcleo (`Gather`): los gestores de
    archivos escriben de a 8 KiB y cada llamada al núcleo cuesta más que eso. Se vuelcan antes de
    leer, de un fsync, de escribir en otro lugar y al cerrar; un error aparece en esa llamada.
32. **Sección "Avanzados"** por servidor: casos borde con lo sensato por defecto. Seguir los
    enlaces a su propia carpeta o a una de más arriba (apagado: bucles para lo que recorre
    carpetas); copias en el servidor (encendido; apagado, el gestor copia con progreso); desconectar
    sin uso a los 1, 5 (por defecto), 15 o 60 minutos, o nunca; escritura directa (gestores de
    archivos por defecto, siempre o nunca).
34. **Escritura directa por un tubo:** un archivo abierto para escribirse entero ("w", "wt") por
    un gestor de archivos conocido (por el paquete que llama) recibe un tubo en vez del proxy de
    archivos: medido en un teléfono, el proxy cuesta ~1,2 ms por escritura (6-7 MB/s con escrituras
    de 8 KiB por una Wi-Fi que sube a ~37 MB/s). El tubo no permite moverse ni sincronizar: las
    demás apps siguen con el proxy. Cada subida deja una línea en el log con su velocidad.
33. **Copiar una carpeta dentro de sí misma**, por su nombre o por un enlace, se rechaza
    comparando las carpetas reales del camino (los handles), no los nombres.
