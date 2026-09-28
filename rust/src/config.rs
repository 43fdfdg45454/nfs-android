//! A stored server turned into the client core's configuration.

use crate::identity::{Identity, tls};
use crate::types::{Security, Server, Transport};
use crate::{Result, other};
use nfs_client::Config;
use nfs_rpc::{Auth, SysCred};
use nfs_tunnel::client::Tunnel;
use std::sync::Arc;

const NFS_PORT: u16 = 2049;

pub async fn config(
    server: &Server,
    roots: &[Vec<u8>],
    identity: Option<&Arc<dyn Identity>>,
) -> Result<Config> {
    let security = match server.security {
        Security::None => nfs_client::Security::None,
        Security::Tls => nfs_client::Security::tls(tls(roots, None)?, &server.host)?,
        Security::MutualTls => {
            let identity =
                identity.ok_or_else(|| other("mutual TLS needs a client certificate"))?;
            nfs_client::Security::tls(tls(roots, Some(identity))?, &server.host)?
        }
    };
    let transport = match &server.transport {
        Transport::Tcp => nfs_client::Transport::Tcp(format!("{}:{}", server.host, server.port)),
        Transport::Quic => {
            let mut outer = tls(roots, identity)?;
            outer.alpn_protocols = vec![b"h3".to_vec()];
            let endpoint = nfs_tunnel::quic::client(outer, Default::default()).map_err(other)?;
            let host = server.host.trim_matches(['[', ']']);
            // As the core does for TCP: nobody answering (the name included) shows in 4 s.
            let lookup = tokio::net::lookup_host((host, server.port));
            let address = tokio::time::timeout(std::time::Duration::from_secs(4), lookup)
                .await
                .map_err(|_| other(format!("{host}: no answer to the name lookup")))?
                .map_err(other)?
                .next();
            nfs_client::Transport::Quic(Arc::new(Tunnel {
                endpoint,
                gateway: address.ok_or_else(|| other(format!("{host} does not resolve")))?,
                server_name: host.to_owned(),
                // Required by CONNECT, ignored by the gateway: it always goes to nfsd by itself.
                authority: match host.contains(':') {
                    true => format!("[{host}]:{NFS_PORT}"),
                    false => format!("{host}:{NFS_PORT}"),
                },
                header: None,
                send_request: Default::default(),
            }))
        }
    };
    let cred = SysCred {
        machine: "android".into(),
        uid: server.uid,
        gid: server.gid,
        gids: server.gids.clone(),
    };
    let mut config = Config::new(transport, security, Auth::Sys(cred), server.owner.clone());
    if server.connections > 0 {
        config.connections = server.connections as usize;
    }
    Ok(config)
}
