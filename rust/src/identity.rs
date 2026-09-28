//! TLS as Android wants it: the CAs the app trusts (system and user, given by the app), and a
//! client certificate whose private key stays in the KeyChain: every signature the handshake
//! needs is asked of the app.

use crate::{NfsError, Result, other};
use rustls::client::ResolvesClientCert;
use rustls::pki_types::CertificateDer;
use rustls::sign::{CertifiedKey, Signer, SigningKey};
use rustls::{ClientConfig, RootCertStore, SignatureAlgorithm, SignatureScheme};
use std::sync::Arc;

#[uniffi::export(with_foreign)]
pub trait Identity: Send + Sync {
    /// The certificate chain, leaf first, DER.
    fn chain(&self) -> Vec<Vec<u8>>;
    /// The key's algorithm: "EC" or "RSA".
    fn algorithm(&self) -> String;
    /// Signs `message` with `scheme`, named as rustls names it (such as "ECDSA_NISTP256_SHA256").
    fn sign(&self, scheme: String, message: Vec<u8>) -> std::result::Result<Vec<u8>, NfsError>;
}

struct Key(Arc<dyn Identity>, SignatureAlgorithm);
struct Sign(Arc<dyn Identity>, SignatureScheme);
#[derive(Debug)]
struct Resolver(Arc<CertifiedKey>);

impl std::fmt::Debug for Key {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "KeyChain key ({:?})", self.1)
    }
}

impl std::fmt::Debug for Sign {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "KeyChain signer ({:?})", self.1)
    }
}

impl SigningKey for Key {
    fn choose_scheme(&self, offered: &[SignatureScheme]) -> Option<Box<dyn Signer>> {
        use SignatureScheme as S;
        let preferred: &[S] = match self.1 {
            SignatureAlgorithm::ECDSA => &[S::ECDSA_NISTP256_SHA256, S::ECDSA_NISTP384_SHA384],
            _ => &[S::RSA_PSS_SHA256, S::RSA_PKCS1_SHA256],
        };
        let scheme = preferred.iter().find(|s| offered.contains(s))?;
        Some(Box::new(Sign(self.0.clone(), *scheme)))
    }

    fn algorithm(&self) -> SignatureAlgorithm {
        self.1
    }
}

impl Signer for Sign {
    fn sign(&self, message: &[u8]) -> std::result::Result<Vec<u8>, rustls::Error> {
        self.0
            .sign(format!("{:?}", self.1), message.to_vec())
            .map_err(|e| rustls::Error::General(e.to_string()))
    }

    fn scheme(&self) -> SignatureScheme {
        self.1
    }
}

impl ResolvesClientCert for Resolver {
    fn resolve(&self, _: &[&[u8]], _: &[SignatureScheme]) -> Option<Arc<CertifiedKey>> {
        Some(self.0.clone())
    }

    fn has_certs(&self) -> bool {
        true
    }
}

/// A rustls client configuration trusting `roots` (DER), presenting `identity` if any.
pub fn tls(roots: &[Vec<u8>], identity: Option<&Arc<dyn Identity>>) -> Result<ClientConfig> {
    let mut store = RootCertStore::empty();
    store.add_parsable_certificates(roots.iter().map(|der| CertificateDer::from(der.clone())));
    let builder = ClientConfig::builder().with_root_certificates(store);
    let Some(identity) = identity else { return Ok(builder.with_no_client_auth()) };
    let chain: Vec<_> = identity.chain().into_iter().map(CertificateDer::from).collect();
    if chain.is_empty() {
        return Err(other("the client certificate has no chain"));
    }
    let algorithm = if identity.algorithm() == "EC" {
        SignatureAlgorithm::ECDSA
    } else {
        SignatureAlgorithm::RSA
    };
    let key = CertifiedKey::new(chain, Arc::new(Key(identity.clone(), algorithm)));
    Ok(builder.with_client_cert_resolver(Arc::new(Resolver(Arc::new(key)))))
}
