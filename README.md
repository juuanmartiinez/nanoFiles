# NanoFiles

Sistema de compartición y transferencia de ficheros P2P para la asignatura **Redes de Comunicaciones** (Universidad de Murcia).  
Compuesto por un **servidor de directorio** (`Directory`) y varios **peers** (`NanoFiles`).

## Arquitectura

| Componente | Rol | Protocolo | Puerto |
|------------|-----|-----------|--------|
| **Directory** | Registra peers, cataloga ficheros y coordina descargas. | UDP | 6868 |
| **NanoFiles (peer)** | Cliente y servidor de ficheros simultáneamente. | UDP + TCP | TCP dinámico |

- **Protocolo de aplicación UDP:** peer ↔ directorio (parada y espera con retransmisión).
- **Protocolo de aplicación TCP:** peer ↔ peer (transferencia fiable de ficheros y listados).
- **Magic number / protocolo:** `123456789A`

## Funcionalidad

### Obligatoria
- `ping` – Comprueba conectividad y compatibilidad de protocolo con el directorio.
- `serve` – Registra el peer como servidor de ficheros en el directorio.
- `dirfiles` – Lista los ficheros disponibles en el directorio.
- `peers` – Muestra el censo de peers registrados.
- `peerfiles &lt;nickname&gt;` – Lista ficheros de un peer vía TCP.
- `peerdl &lt;nickname&gt; &lt;hash&gt;` – Descarga un fichero desde un peer vía TCP.

### Mejoras implementadas
- `quit` – Da de baja el peer en el directorio y detiene el servidor TCP.
- `dirdl &lt;subHash&gt;` – Descarga ficheros directamente desde el directorio (por bloques si es necesario).
- **Descarga por bloques (`dirdl` ampliado):** fragmentación numerada (`numseq`) y verificación de integridad.
- **Listado paginado (`dirfiles` ampliado):** soporta múltiples datagramas UDP cuando el listado no cabe en uno solo.

## Flujo de prueba rápido

1. Lanzar `Directory` (escucha UDP 6868).
2. Lanzar `NanoFiles &lt;host_directorio&gt;`.
3. En el peer:
   - `ping`
   - `serve`
   - `dirfiles`
   - `peers`
   - `dirdl &lt;subHash&gt;`
   - `peerfiles &lt;nickname&gt;`
   - `peerdl &lt;nickname&gt; &lt;hash_substring&gt;`
4. Verificar que el fichero descargado coincide en nombre y hash.

## Capturas

Disponibles en la memoria junto con trazas de **Wireshark** (three-way handshake TCP, datagramas UDP del directorio, etc.).

## Enlace al vídeo de demostración

- `https://youtu.be/NG65Lkd-1rs`
