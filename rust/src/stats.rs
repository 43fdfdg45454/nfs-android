//! What a mount's connections are doing now, and the bytes all of them carried: for the
//! notification and the activity screen.

use crate::mount::Mount;

#[derive(uniffi::Record)]
pub struct MountStats {
    /// QUIC streams through the gateway, or else TCP connections to nfsd.
    pub quic: bool,
    /// Connections (or streams) the mount keeps, how many are up, and calls waiting on them.
    pub lanes: u32,
    pub alive: u32,
    pub in_flight: u32,
    /// Connections made since connecting: more than `lanes` means reconnections.
    pub connects: u32,
    /// The QUIC path to the gateway (0 when not over QUIC or not connected): round trip in
    /// milliseconds, congestion window in bytes, packets sent and lost.
    pub rtt_ms: u32,
    pub cwnd: u64,
    pub packets_sent: u64,
    pub packets_lost: u64,
    /// Files being read now, and memory their pieces take.
    pub open_files: u32,
    pub memory: u64,
    /// How many times the server said it could not call back.
    pub callbacks_down: u32,
}

/// Bytes sent and received by every connection of the app so far.
#[derive(uniffi::Record)]
pub struct Traffic {
    pub sent: u64,
    pub received: u64,
}

#[uniffi::export]
pub fn traffic() -> Traffic {
    let (sent, received) = nfs_rpc::traffic();
    Traffic { sent, received }
}

#[uniffi::export]
impl Mount {
    pub fn stats(&self) -> MountStats {
        let (engine, client) = (&self.engine, self.engine.client());
        let s = client.stats();
        let path = s.path;
        MountStats {
            quic: s.quic,
            lanes: s.lanes as u32,
            alive: s.alive as u32,
            in_flight: s.in_flight as u32,
            connects: s.connects,
            rtt_ms: path.map_or(0, |p| p.rtt.as_millis() as u32),
            cwnd: path.map_or(0, |p| p.cwnd),
            packets_sent: path.map_or(0, |p| p.sent),
            packets_lost: path.map_or(0, |p| p.lost),
            open_files: engine.open_files() as u32,
            memory: engine.memory().0,
            callbacks_down: client.callbacks_down(),
        }
    }
}
