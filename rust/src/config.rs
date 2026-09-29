//! A stored server turned into the client core's configuration.

use crate::identity::{Identity, tls};
use crate::types::{Security, Server, Transport};
use crate::{Result, other};
use nfs_client::Config;
use nfs_rpc::{Auth, SysCred};
use nfs_tunnel::client::Tunnel;
use std::sync::Arc;

const NFS_PORT: u16 = 2049;

/// `identity` and `gateway_identity`: the client certificates for nfsd (mutual TLS) and for the
/// gateway (QUIC), each its own: the tunnel may ask for one where the export asks for none.
pub async fn config(
    server: &Server,
    roots: &[Vec<u8>],
    identity: Option<&Arc<dyn Identity>>,
    gateway_identity: Option<&Arc<dyn Identity>>,
) -> Result<Config> {
    // A name or an address; an IPv6 address may come in brackets or not.
    let host = server.host.trim_matches(['[', ']']);
    let security = match server.security {
        Security::None => nfs_client::Security::None,
        Security::Tls => nfs_client::Security::tls(tls(roots, None)?, host)?,
        Security::MutualTls => {
            let identity =
                identity.ok_or_else(|| other("mutual TLS needs a client certificate"))?;
            nfs_client::Security::tls(tls(roots, Some(identity))?, host)?
        }
    };
    // Both transports reach the server the same way: the core looks the name up at each new
    // connection (within its reach timeout) and tries the addresses in turn.
    let transport = match &server.transport {
        Transport::Tcp => nfs_client::Transport::Tcp(address(host, server.port)),
        Transport::Quic => {
            let mut outer = tls(roots, gateway_identity)?;
            outer.alpn_protocols = vec![b"h3".to_vec()];
            let endpoint = nfs_tunnel::quic::client(outer, Default::default()).map_err(other)?;
            nfs_client::Transport::Quic(Arc::new(Tunnel {
                endpoint,
                gateway: address(host, server.port),
                server_name: host.to_owned(),
                // Required by CONNECT, ignored by the gateway: it always goes to nfsd by itself.
                authority: address(host, NFS_PORT),
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
    config.umask = server.umask;
    config.rate = nfs_client::Rate::new(server.up_limit, server.down_limit);
    if server.connections > 0 {
        config.connections = server.connections as usize;
    }
    Ok(config)
}

/// `host:port`, with an IPv6 address in brackets.
fn address(host: &str, port: u16) -> String {
    match host.contains(':') {
        true => format!("[{host}]:{port}"),
        false => format!("{host}:{port}"),
    }
}
