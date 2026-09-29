//! Copies made by the server: CLONE (instant where the file system shares blocks) or COPY (the
//! server copies on its own disk), a whole directory too. Nothing crosses the network.

use crate::mount::{Mount, join, split};
use crate::{NfsError, Result};
use nfs_client::attr::{FileType, SetAttrs};
use nfs_client::{Create, Error, File, READ_ACCESS, Status, WRITE_ACCESS};
use std::future::Future;
use std::pin::Pin;

/// COPY in steps: each is one call, which must not take too long for the server to answer.
const STEP: u64 = 64 << 20;

/// What the server says when it cannot do a copy (other file systems, no such operation).
fn cannot(error: &Error) -> bool {
    matches!(error, Error::Nfs(Status::NOTSUPP | Status::XDEV | Status::INVAL))
}

#[uniffi::export(async_runtime = "tokio")]
impl Mount {
    /// A copy of `from` (a file, or a directory with all it holds) at `to`, which must not exist.
    /// False, with nothing left behind, when the server cannot copy it: the caller copies through
    /// the device instead.
    pub async fn server_copy(&self, from: String, to: String) -> Result<bool> {
        if self.resolve(&to, false).await.is_ok() {
            return Err(NfsError::AlreadyExists { reason: format!("{to} exists") });
        }
        let (_, source, attrs) = self.resolve_all(&from, true).await?;
        // Into itself, by its name or through a link: the copy would copy itself without end.
        let (mut above, parent, _) = self.resolve_all(split(&to)?.0, true).await?;
        above.push(parent);
        if attrs.kind == FileType::Directory && above.contains(&source) {
            return Err(crate::other(format!("{from} cannot be copied into itself")));
        }
        let copied = self.copy_tree(&from, &to, attrs.kind, attrs.mode).await;
        if !matches!(copied, Ok(true)) {
            self.remove_tree(&to).await.ok();
        }
        copied
    }
}

impl Mount {
    /// As `cp -r`: links inside a directory are copied as links; sockets and devices are skipped.
    fn copy_tree<'a>(
        &'a self,
        from: &'a str,
        to: &'a str,
        kind: FileType,
        mode: u32,
    ) -> Pin<Box<dyn Future<Output = Result<bool>> + Send + 'a>> {
        Box::pin(async move {
            let client = self.engine.client();
            let (parent, name) = split(to)?;
            let mode = SetAttrs { mode: Some(mode & 0o777), ..Default::default() };
            match kind {
                FileType::Regular => self.copy_file(from, to, mode).await,
                FileType::Directory => {
                    client.mkdir(&self.fh(parent).await?, name, &mode).await?;
                    for entry in client.readdir(&self.fh(from).await?).await? {
                        let (from, to) = (join(from, &entry.name), join(to, &entry.name));
                        let (kind, mode) = (entry.attrs.kind, entry.attrs.mode);
                        if !self.copy_tree(&from, &to, kind, mode).await? {
                            return Ok(false);
                        }
                    }
                    Ok(true)
                }
                FileType::Symlink => {
                    let target = client.readlink(&self.resolve(from, false).await?.0).await?;
                    client.symlink(&self.fh(parent).await?, name, &target).await?;
                    Ok(true)
                }
                _ => Ok(true),
            }
        })
    }

    async fn copy_file(&self, from: &str, to: &str, mode: SetAttrs) -> Result<bool> {
        let client = self.engine.client();
        let (parent, name) = split(to)?;
        let (fh, attrs) = self.resolve(from, true).await?;
        let dir = self.fh(parent).await?;
        let (dest, _) = client.create(&dir, name, Create::Guarded(mode), WRITE_ACCESS).await?;
        let source = client.open(&fh, READ_ACCESS).await?;
        let copied = copy_data(&source, &dest, attrs.size).await;
        source.close().await.ok();
        dest.close().await?;
        copied
    }

    fn remove_tree<'a>(
        &'a self,
        path: &'a str,
    ) -> Pin<Box<dyn Future<Output = Result<()>> + Send + 'a>> {
        Box::pin(async move {
            let (fh, attrs) = self.resolve(path, false).await?;
            if attrs.kind == FileType::Directory {
                for entry in self.engine.client().readdir(&fh).await? {
                    self.remove_tree(&join(path, &entry.name)).await?;
                }
            }
            let (parent, name) = split(path)?;
            Ok(self.engine.remove(&self.fh(parent).await?, name).await?)
        })
    }
}

/// CLONE, else COPY step by step; false if the server can do neither.
async fn copy_data(source: &File, dest: &File, size: u64) -> Result<bool> {
    match source.clone_to(dest, 0, 0, 0).await {
        Ok(()) => return Ok(true),
        Err(error) if cannot(&error) => {}
        Err(error) => return Err(error.into()),
    }
    let mut offset = 0;
    while offset < size {
        match source.copy_to(dest, offset, offset, STEP.min(size - offset)).await {
            Ok(0) => return Err(crate::other("the server copied nothing")),
            Ok(n) => offset += n,
            Err(error) if offset == 0 && cannot(&error) => return Ok(false),
            Err(error) => return Err(error.into()),
        }
    }
    dest.commit().await?;
    Ok(true)
}
