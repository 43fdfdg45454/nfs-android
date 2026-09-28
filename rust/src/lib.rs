//! The client core as the Android app sees it (UniFFI, Kotlin): a mount of one server's export,
//! files by path, and errors an app can act on.

mod cache;
mod config;
mod copy;
mod files;
mod identity;
mod links;
mod mount;
mod stats;
mod types;

pub use files::{ReadFile, WriteFile};
pub use identity::Identity;
pub use mount::Mount;
pub use stats::{MountStats, Traffic};
pub use types::{Kind, Security, Server, Space, Stat, Transport};

uniffi::setup_scaffolding!();

/// What went wrong, with the kind an app shows differently. The field is not `message`: Kotlin's
/// exceptions have one already.
#[derive(Debug, uniffi::Error)]
pub enum NfsError {
    NotFound {
        reason: String,
    },
    PermissionDenied {
        reason: String,
    },
    AlreadyExists {
        reason: String,
    },
    NoSpace {
        reason: String,
    },
    /// The server does not answer, or the network is gone.
    Unreachable {
        reason: String,
    },
    Other {
        reason: String,
    },
}

impl std::fmt::Display for NfsError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let (Self::NotFound { reason }
        | Self::PermissionDenied { reason }
        | Self::AlreadyExists { reason }
        | Self::NoSpace { reason }
        | Self::Unreachable { reason }
        | Self::Other { reason }) = self;
        f.write_str(reason)
    }
}

impl std::error::Error for NfsError {}

impl From<nfs_client::Error> for NfsError {
    fn from(error: nfs_client::Error) -> Self {
        use std::io::ErrorKind as K;
        let reason = error.to_string();
        match std::io::Error::from(error).kind() {
            K::NotFound => Self::NotFound { reason },
            K::PermissionDenied | K::ReadOnlyFilesystem => Self::PermissionDenied { reason },
            K::AlreadyExists => Self::AlreadyExists { reason },
            K::StorageFull => Self::NoSpace { reason },
            K::TimedOut | K::ConnectionAborted => Self::Unreachable { reason },
            _ => Self::Other { reason },
        }
    }
}

pub type Result<T> = std::result::Result<T, NfsError>;

pub(crate) fn other(message: impl std::fmt::Display) -> NfsError {
    NfsError::Other { reason: message.to_string() }
}
