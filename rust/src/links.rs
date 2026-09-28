//! Symbolic links followed inside the export, as a kernel mount would: relative ones, and absolute
//! ones under the export's path on the server. What points elsewhere is out of NFS's reach.

use crate::mount::Mount;
use crate::{NfsError, Result};
use nfs_client::attr::{Attrs, FileType};
use nfs_client::{Error, Fh, Status};
use std::collections::VecDeque;

/// As Linux's limit: past it, a loop.
const MAX_LINKS: usize = 40;

fn out_of_reach(path: &str, why: &str) -> NfsError {
    NfsError::NotFound { reason: format!("{path}: {why}") }
}

impl Mount {
    /// `path`'s handle and attributes, the links on the way followed, and a last one too if `last`.
    pub(crate) async fn resolve(&self, path: &str, last: bool) -> Result<(Fh, Attrs)> {
        let client = self.engine.client();
        // One call when no link is on the way (nearly always): the server refuses to look past one.
        match client.lookup(None, path).await {
            Ok((fh, attrs)) if !last || attrs.kind != FileType::Symlink => return Ok((fh, attrs)),
            Ok(_) | Err(Error::Nfs(Status::SYMLINK | Status::NOTDIR)) => {}
            Err(error) => return Err(error.into()),
        }
        let root = client.root().clone();
        let mut dirs = vec![(root.clone(), client.getattr(&root).await?)];
        let mut names: VecDeque<String> = path.split('/').map(str::to_owned).collect();
        let mut links = 0;
        while let Some(name) = names.pop_front() {
            match name.as_str() {
                "" | "." => continue,
                ".." if dirs.len() == 1 => {
                    return Err(out_of_reach(path, "a link out of the export"));
                }
                ".." => {
                    dirs.pop();
                    continue;
                }
                _ => {}
            }
            let (fh, attrs) = client.lookup(Some(&dirs[dirs.len() - 1].0), &name).await?;
            if attrs.kind != FileType::Symlink || (!last && names.iter().all(String::is_empty)) {
                dirs.push((fh, attrs));
                continue;
            }
            links += 1;
            if links > MAX_LINKS {
                return Err(out_of_reach(path, "too many links (a loop)"));
            }
            let target = client.readlink(&fh).await?;
            let target = match target.strip_prefix('/') {
                Some(absolute) => {
                    dirs.truncate(1);
                    self.under_export(absolute)
                        .ok_or_else(|| out_of_reach(path, "a link out of the export"))?
                }
                None => target,
            };
            target.split('/').rev().for_each(|part| names.push_front(part.to_owned()));
        }
        Ok(dirs.pop().expect("the root at least"))
    }

    /// An absolute target (without its first '/') as a path under the export, if it is in it.
    fn under_export(&self, absolute: &str) -> Option<String> {
        let export = self.export.trim_matches('/');
        if export.is_empty() {
            return Some(absolute.to_owned());
        }
        let rest = absolute.strip_prefix(export)?;
        (rest.is_empty() || rest.starts_with('/')).then(|| rest.trim_start_matches('/').to_owned())
    }
}
