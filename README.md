# nfs-android

[![CI](https://github.com/43fdfdg45454/nfs-android/actions/workflows/ci.yml/badge.svg)](https://github.com/43fdfdg45454/nfs-android/actions/workflows/ci.yml)
[![Security](https://github.com/43fdfdg45454/nfs-android/actions/workflows/security.yml/badge.svg)](https://github.com/43fdfdg45454/nfs-android/actions/workflows/security.yml)
[![Release](https://img.shields.io/github/v/release/43fdfdg45454/nfs-android)](https://github.com/43fdfdg45454/nfs-android/releases/latest)

An Android app (13+) that mounts NFSv4.2 exports and offers their files to every other app
through the system file picker: VLC, the system player, a file manager or a gallery open, seek
and save files on the server as if they were local. Built for streaming audio and video and for
uploads over a VPN with latency and loss; on the local network it just goes as fast as the link.

The protocol work lives in [nfs-core](https://github.com/43fdfdg45454/nfs-core) (Rust): over a
VPN it reads at ~10 MB/s where a single NFS connection gets 0.4 MB/s, seeks in a video in about
0.1 s, and never stalls a 1080p player.

## Install

Download `NFS-<version>.apk` from the [latest release](https://github.com/43fdfdg45454/nfs-android/releases/latest).
Every release is signed with the same key, so each one installs over the last; an updater that
follows GitHub releases (FFUpdater, Obtainium) keeps it current.

## Add a server

Tap **Add server**. Each section of the form explains its options:

- **Connection**
  - **TCP · direct** to nfsd (port 2049): for the local network.
  - **QUIC · gateway**: through the [nfs-core gateway](https://github.com/43fdfdg45454/nfs-core/blob/master/docs/gateway.md)
    next to nfsd (its UDP port, 443 by default): for the VPN or any link with loss. Many
    streams in one connection, and it recovers at once when the phone changes networks.
  - The export's path, as in a mount (`/` for the root).
- **Network**: any, or only a VPN or a Wi-Fi, optionally the one that gives the phone an address in
  a subnet ("Use current" fills it in). Without that network the server fails at once instead of
  making apps wait.
- **Security**: none, TLS or mTLS, as the export's `xprtsec` asks. The client certificate is a
  `.p12` installed in Settings › Security › Encryption & credentials › Install a certificate; its
  key never leaves Android's key store. The gateway always asks for one.
- **Identity**: the UID/GID your files belong to on the server (with `all_squash` it does not
  matter), and the permissions of what you create: standard (644, folders 755), group (664, 775),
  private (600, 700) or any umask.
- **Performance**: connections, read-ahead (256 MB by default) and the local cache.
- **Advanced**: edge cases, with sensible defaults. Following symbolic links to their own folder
  or one above it (off: apps that go through whole folders would loop through them); copies made
  by the server (on; off, the file manager copies through the phone, with progress); disconnecting
  when unused (after 5 minutes; 1, 15, 60 or never); how files written whole reach the server:
  Android's file proxy (by default: each write has a cost, slow for apps that write in small
  pieces, but the app knows when the server has it all) or a local copy uploaded as it grows (the
  app writes a real file at full speed; the upload's progress and any error show in a
  notification); and the server's log: on or off, its level (errors, warnings, info or detail) and
  what each category holds.

**Test the connection** tries it before saving. Then the server shows up in the file picker of
every app, and **Browse** opens it in the system's Files app.

## Using it

- **Servers**: live throughput, and each server's connections, round trip and free space. A switch
  turns a server off without deleting it.
- **Activity**: every connected server in detail (connections up, calls in flight, reconnections,
  RTT, loss, memory), and **Share the log**.
- **Settings**: the local cache for every server (4 GB by default), the notification, about.
- While a server is connected, a notification shows what the connections are doing; Android needs
  it to let players reach the server from the background.
- In any file manager, copying or moving within one server is done by the server: a move is a
  rename (instant, whatever the size) and a copy a clone (instant on XFS or btrfs) or a copy on the
  server's own disk; nothing crosses the network. A file already in the local cache copies to
  another server without reading it from the first one again.
- Symbolic links inside the export are followed (relative ones, and absolute ones under the
  export's path on the server); a link out of the export, in a loop or to a folder above it shows
  but does not open. A link grants nothing its target's permissions do not: CI checks escapes,
  loops, chains and permissions with a plain user.

## When something goes wrong

- The **Activity** tab and the server's card say what failed ("No answer", "Waiting for its
  network").
- `Android/data/io.github.nfsandroid/files/nfs-log.txt` has one event per line: time, level,
  category, server, event and `key=value` fields. What each server logs is set in its Advanced
  section (info by default: connections, uploads and every error); network changes and the
  foreground service are always logged. `crash-log.txt` next to it has any crash. Both are what
  to attach to an issue (they contain your servers' names: look before posting).
- Each upload leaves a line in the `uploads` category: size, time, speed, how it went (`via`) and
  where the time went (`writing` while the app had it open, `core` waiting on the network or the
  server, `closing` after the app closed it). Thumbnails that take over 2 s or cannot be made
  leave a warning with the bytes they read. An upload through a local copy that fails after the
  app let go also shows in a notification.
- Some file managers do not show the free space of storages added through the system picker; the
  system's Files app and this app's cards do.

## Server side

nfsd with NFSv4.2: `insecure` in the export (Android uses high ports), enough threads
(`threads=64` in `[nfsd]` of `/etc/nfs.conf` for many connections at once), and for TLS `tlshd`
with a certificate whose SAN has the name the phone uses. The gateway's guide has the rest.

## Privacy and security

The app talks only to your servers: no analytics, no accounts, and its data is excluded from
Android's backups. Only TLS 1.3; certificates checked against the system's and the user's CAs.
CI runs CodeQL on the Kotlin and Rust code, `cargo deny` on the bridge, a secrets scan of the
history, and Android lint's security checks fail the release build.

## Building

- `app/`: Kotlin and Jetpack Compose (servers, the `DocumentsProvider`, the foreground service).
- `rust/`: the bridge to nfs-core with UniFFI, pinned to an nfs-core commit; `ci/core.sh` builds
  `libnfscore.so` (arm64-v8a, x86_64) and the Kotlin bindings into `:core`.
- CI builds everything and runs the provider on an emulator against nfsd, as another app would.
  Versions come from the history (GitVersion: each commit on `master` is a patch; `+semver: minor`
  bumps more); every green push to `master` publishes a release.

Nothing needs building by hand: push to your own copy of the repository and the APK is in the
run's artifacts. Plans and
decisions are in `CLAUDE.md` and `DECISIONES.md` (in Spanish).

## License

GNU General Public License v3.0; see [LICENSE](LICENSE).
