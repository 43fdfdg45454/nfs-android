//! Symbolic links followed inside the export, as a kernel mount would: relative ones, and absolute
//! ones under the export's path on the server. What points elsewhere is out of NFS's reach. A link
//! to its own directory or one above it (a loop for anything walking folders) only if allowed.

use crate::mount::Mount;
use crate::{NfsError, Result};
use nfs_client::Fh;
use nfs_client::attr::{Attrs, FileType};
use std::collections::VecDeque;

/// As Linux's limit: past it, a loop.
const MAX_LINKS: usize = 40;

enum Step {
    Name(String),
    /// Where a followed link's target ends: the directories above that link.
    End(Vec<Fh>),
}

fn steps(path: &str) -> impl Iterator<Item = Step> + '_ {
    path.split('/').filter(|n| !n.is_empty() && *n != ".").map(|n| Step::Name(n.to_owned()))
}

fn out_of_reach(path: &str, why: &str) -> NfsError {
    NfsError::NotFound { reason: format!("{path}: {why}") }
}

/// An absolute target (without its first '/') as a path under the export, if it is in it.
pub(crate) fn under_export(export: &str, absolute: &str) -> Option<String> {
    let export = export.trim_matches('/');
    if export.is_empty() {
        return Some(absolute.to_owned());
    }
    let rest = absolute.strip_prefix(export)?;
    (rest.is_empty() || rest.starts_with('/')).then(|| rest.trim_start_matches('/').to_owned())
}

impl Mount {
    /// `path`'s handle and attributes, the links on the way followed, and a last one too if `last`.
    pub(crate) async fn resolve(&self, path: &str, last: bool) -> Result<(Fh, Attrs)> {
        self.resolve_all(path, last).await.map(|(_, fh, attrs)| (fh, attrs))
    }

    /// As `resolve`, with the directories on the way, links followed: the root first. Each
    /// stretch without a link is one call (`Client::walk`); a link costs a READLINK more.
    pub(crate) async fn resolve_all(&self, path: &str, last: bool) -> Result<(Vec<Fh>, Fh, Attrs)> {
        let client = self.engine.client();
        let mut dirs: Vec<(Fh, Option<Attrs>)> = vec![(client.root().clone(), None)];
        let mut todo: VecDeque<Step> = steps(path).collect();
        let mut links = 0;
        while !todo.is_empty() {
            // Names up to the next "..", and the ends of links among them (after how many names).
            let (mut batch, mut ends) = (Vec::new(), Vec::new());
            while let Some(step) = todo.pop_front() {
                match step {
                    Step::Name(n) if n == ".." && batch.is_empty() && ends.is_empty() => {
                        dirs.pop();
                        if dirs.is_empty() {
                            return Err(out_of_reach(path, "a link out of the export"));
                        }
                    }
                    Step::Name(n) if n == ".." => {
                        todo.push_front(Step::Name(n));
                        break;
                    }
                    Step::Name(n) => batch.push(n),
                    Step::End(above) => ends.push((batch.len(), above)),
                }
            }
            let start = dirs.len() - 1;
            let (found, error) = if batch.is_empty() {
                (0, None)
            } else {
                let (found, error) = client.walk(Some(&dirs[start].0), &batch.join("/")).await?;
                let n = found.len();
                dirs.extend(found.into_iter().map(|(fh, attrs)| (fh, Some(attrs))));
                (n, error)
            };
            let top_is_link = found > 0
                && dirs.last().and_then(|d| d.1.as_ref()).map(|a| a.kind)
                    == Some(FileType::Symlink);
            let ahead = found < batch.len() || todo.iter().any(|s| matches!(s, Step::Name(_)));
            let follow = top_is_link && (ahead || last);
            // The ends passed by: the directory reached there must not be one above its link.
            for (at, above) in
                ends.iter().filter(|(at, _)| *at < found || (*at == found && !follow))
            {
                if !self.follow_up && above.contains(&dirs[start + at].0) {
                    return Err(out_of_reach(path, "a link to a folder above it"));
                }
            }
            if !follow {
                if let Some(error) = error {
                    return Err(error.into());
                }
                continue;
            }
            links += 1;
            if links > MAX_LINKS {
                return Err(out_of_reach(path, "too many links (a loop)"));
            }
            let (link, _) = dirs.pop().expect("the link");
            let above: Vec<Fh> = dirs.iter().map(|(fh, _)| fh.clone()).collect();
            let target = client.readlink(&link).await?;
            let target = match target.strip_prefix('/') {
                Some(absolute) => {
                    dirs.truncate(1);
                    under_export(&self.export, absolute)
                        .ok_or_else(|| out_of_reach(path, "a link out of the export"))?
                }
                None => target,
            };
            // Then what came after the link: the ends waiting there, and the names not looked up.
            let mut rest: Vec<Step> = ends
                .into_iter()
                .filter(|(at, _)| *at >= found)
                .map(|(_, a)| Step::End(a))
                .collect();
            rest.extend(batch.into_iter().skip(found).map(Step::Name));
            for step in steps(&target)
                .chain([Step::End(above)])
                .chain(rest)
                .collect::<Vec<_>>()
                .into_iter()
                .rev()
            {
                todo.push_front(step);
            }
        }
        let (fh, attrs) = dirs.pop().expect("the root at least");
        let attrs = match attrs {
            Some(attrs) => attrs,
            None => client.getattr(&fh).await?,
        };
        Ok((dirs.into_iter().map(|(fh, _)| fh).collect(), fh, attrs))
    }
}

#[cfg(test)]
mod tests {
    use super::under_export;

    #[test]
    fn absolute_links_count_only_under_the_export() {
        assert_eq!(under_export("/srv/media", "srv/media/a/b").as_deref(), Some("a/b"));
        assert_eq!(under_export("/srv/media/", "srv/media").as_deref(), Some(""));
        assert_eq!(under_export("/srv/media", "srv/media-other/x"), None);
        assert_eq!(under_export("/srv/media", "srv"), None);
        assert_eq!(under_export("/srv/media", "etc/passwd"), None);
        assert_eq!(under_export("/", "etc/passwd").as_deref(), Some("etc/passwd"));
    }
}
