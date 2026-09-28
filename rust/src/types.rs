//! Values that cross to Kotlin.

use nfs_client::attr::{Attrs, FileType};

#[derive(uniffi::Enum)]
pub enum Transport {
    Tcp,
    /// Through the gateway at the server's host and (UDP) port.
    Quic,
}

#[derive(uniffi::Enum, PartialEq)]
pub enum Security {
    None,
    Tls,
    MutualTls,
}

/// One server, as the app stores it.
#[derive(uniffi::Record)]
pub struct Server {
    pub host: String,
    pub port: u16,
    pub export: String,
    pub transport: Transport,
    pub security: Security,
    pub uid: u32,
    pub gid: u32,
    pub gids: Vec<u32>,
    /// Stable per installation and server: two clients with one owner would each look like the
    /// other restarting to the server.
    pub owner: String,
    /// 0 for the transport's default (4 streams over QUIC, 8 connections over TCP).
    pub connections: u32,
    pub read_ahead_mb: u32,
    pub use_cache: bool,
}

#[derive(uniffi::Enum)]
pub enum Kind {
    File,
    Directory,
    Symlink,
    Other,
}

#[derive(uniffi::Record)]
pub struct Stat {
    pub name: String,
    pub kind: Kind,
    pub size: u64,
    pub mode: u32,
    /// Milliseconds since the epoch.
    pub modified: i64,
    pub fileid: u64,
}

impl Stat {
    pub fn new(name: String, a: &Attrs) -> Self {
        let kind = match a.kind {
            // Unreadable in a listing: an export (or mount point) that needs other security.
            _ if a.error != 0 => Kind::Directory,
            FileType::Regular => Kind::File,
            FileType::Directory => Kind::Directory,
            FileType::Symlink => Kind::Symlink,
            FileType::Other(_) => Kind::Other,
        };
        let modified = a.modified.secs * 1000 + i64::from(a.modified.nanos / 1_000_000);
        Self { name, kind, size: a.size, mode: a.mode, modified, fileid: a.fileid }
    }
}

/// Bytes available to this client, free, and total.
#[derive(uniffi::Record)]
pub struct Space {
    pub available: u64,
    pub free: u64,
    pub total: u64,
}
