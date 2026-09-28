//! One server's export, mounted: files and directories by path under it.

use crate::files::{ReadFile, WriteFile};
use crate::identity::Identity;
use crate::types::{Server, Space, Stat};
use crate::{Result, other};
use nfs_client::attr::SetAttrs;
use nfs_client::{Client, Create, Fh};
use nfs_engine::Engine;
use std::sync::Arc;

#[derive(uniffi::Object)]
pub struct Mount {
    pub(crate) engine: Arc<Engine>,
}

/// The parent directory's path and the last name of `path`.
fn split(path: &str) -> Result<(&str, &str)> {
    let path = path.trim_end_matches('/');
    let (parent, name) = path.rsplit_once('/').unwrap_or(("", path));
    if name.is_empty() { Err(other("a path with no name")) } else { Ok((parent, name)) }
}

#[uniffi::export(async_runtime = "tokio")]
impl Mount {
    /// `roots`: the CA certificates the app trusts (DER). `cache_bytes` 0 turns the disk cache off.
    #[uniffi::constructor]
    pub async fn connect(
        server: Server,
        roots: Vec<Vec<u8>>,
        identity: Option<Arc<dyn Identity>>,
        cache_dir: String,
        cache_bytes: u64,
    ) -> Result<Arc<Self>> {
        let config = crate::config::config(&server, &roots, identity.as_ref()).await?;
        let client = Client::connect(config, &server.export).await?;
        let cache = server.use_cache.then(|| crate::cache::get(&cache_dir, cache_bytes)).flatten();
        let read_ahead = u64::from(server.read_ahead_mb.clamp(16, 1024)) << 20;
        let config = nfs_engine::Config { read_ahead, cache, ..Default::default() };
        Ok(Arc::new(Self { engine: Engine::new(client, config) }))
    }

    pub async fn stat(&self, path: String) -> Result<Stat> {
        let (_, attrs) = self.engine.client().lookup(None, &path).await?;
        Ok(Stat::new(split(&path).map_or(String::new(), |(_, n)| n.to_owned()), &attrs))
    }

    pub async fn list(&self, path: String) -> Result<Vec<Stat>> {
        let dir = self.fh(&path).await?;
        Ok(self
            .engine
            .client()
            .readdir(&dir)
            .await?
            .iter()
            .map(|e| Stat::new(e.name.clone(), &e.attrs))
            .collect())
    }

    pub async fn mkdir(&self, path: String) -> Result<Stat> {
        let (parent, name) = split(&path)?;
        let (_, attrs) =
            self.engine.client().mkdir(&self.fh(parent).await?, name, &SetAttrs::default()).await?;
        Ok(Stat::new(name.to_owned(), &attrs))
    }

    /// A file or an empty directory.
    pub async fn remove(&self, path: String) -> Result<()> {
        let (parent, name) = split(&path)?;
        Ok(self.engine.remove(&self.fh(parent).await?, name).await?)
    }

    pub async fn rename(&self, from: String, to: String) -> Result<()> {
        let ((from_dir, from), (to_dir, to)) = (split(&from)?, split(&to)?);
        let (from_fh, to_fh) = (self.fh(from_dir).await?, self.fh(to_dir).await?);
        Ok(self.engine.rename(&from_fh, from, &to_fh, to).await?)
    }

    pub async fn read(&self, path: String) -> Result<Arc<ReadFile>> {
        Ok(Arc::new(ReadFile::new(self.engine.read(&self.fh(&path).await?).await?)))
    }

    /// A new file (failing if it exists when `exclusive`), or an existing one emptied.
    pub async fn create(&self, path: String, exclusive: bool) -> Result<Arc<WriteFile>> {
        let (parent, name) = split(&path)?;
        let create = if exclusive {
            Create::Guarded(Default::default())
        } else {
            Create::Unchecked(SetAttrs { size: Some(0), ..Default::default() })
        };
        let (writer, _) = self.engine.create(&self.fh(parent).await?, name, create).await?;
        Ok(Arc::new(WriteFile::new(writer)))
    }

    /// An existing file, to read and change in place (after emptying it, with `truncate`).
    pub async fn edit(&self, path: String, truncate: bool) -> Result<Arc<WriteFile>> {
        let fh = self.fh(&path).await?;
        if truncate {
            let empty = SetAttrs { size: Some(0), ..Default::default() };
            self.engine.client().setattr(&fh, &empty).await?;
        }
        Ok(Arc::new(WriteFile::new(self.engine.write(&fh, true).await?)))
    }

    /// The device changed networks: connections are made anew now, on the new one.
    pub async fn network_changed(&self) {
        self.engine.client().network_changed().await;
    }

    /// How many times the server said it could not call back (diagnosis).
    pub fn callbacks_down(&self) -> u32 {
        self.engine.client().callbacks_down()
    }

    pub async fn space(&self) -> Result<Space> {
        let (available, free, total) = self.engine.client().space().await?;
        Ok(Space { available, free, total })
    }

    /// Closes the session's connections (named so, not `close`: Kotlin objects have one).
    pub fn disconnect(&self) {
        self.engine.client().close();
    }
}

impl Mount {
    async fn fh(&self, path: &str) -> Result<Fh> {
        if path.trim_matches('/').is_empty() {
            return Ok(self.engine.client().root().clone());
        }
        Ok(self.engine.client().lookup(None, path).await?.0)
    }
}
