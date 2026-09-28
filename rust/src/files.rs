//! Open files, as the app's file descriptors use them. The file proxy calls the blocking forms
//! from a thread of its own: one native call each, where the async forms (a UniFFI future, polled
//! through callbacks into the JVM) cost 15-20 ms per call on the emulator, which capped reads of
//! 128 KiB at about 3 MB/s.

use crate::{Result, other};
use nfs_engine::{Reader, Writer};
use tokio::sync::Mutex;

use tokio::runtime::Handle;

#[derive(uniffi::Object)]
pub struct ReadFile(Reader, Handle);

impl ReadFile {
    /// Made where the engine runs (an async call): the blocking forms wait on that runtime.
    pub fn new(reader: Reader) -> Self {
        Self(reader, Handle::current())
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl ReadFile {
    pub fn size(&self) -> u64 {
        self.0.size()
    }

    /// Up to `len` bytes at `offset`; fewer only at the end of the file.
    pub async fn read_at(&self, offset: u64, len: u32) -> Result<Vec<u8>> {
        Ok(self.0.read_at(offset, len as usize).await?.to_vec())
    }

    /// `read_at`, blocking the calling thread (never one of the engine's).
    pub fn read_blocking(&self, offset: u64, len: u32) -> Result<Vec<u8>> {
        self.1.block_on(self.read_at(offset, len))
    }
}

#[derive(uniffi::Object)]
pub struct WriteFile(Mutex<Option<Writer>>, Handle);

impl WriteFile {
    /// Made where the engine runs (an async call): the blocking forms wait on that runtime.
    pub fn new(writer: Writer) -> Self {
        Self(Mutex::new(Some(writer)), Handle::current())
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl WriteFile {
    pub async fn write_at(&self, offset: u64, data: Vec<u8>) -> Result<()> {
        let writer = self.0.lock().await;
        Ok(writer.as_ref().ok_or_else(|| other("closed"))?.write_at(offset, &data).await?)
    }

    /// `write_at`, blocking the calling thread (never one of the engine's).
    pub fn write_blocking(&self, offset: u64, data: Vec<u8>) -> Result<()> {
        self.1.block_on(self.write_at(offset, data))
    }

    /// `read_at`, blocking the calling thread (never one of the engine's).
    pub fn read_blocking(&self, offset: u64, len: u32) -> Result<Vec<u8>> {
        self.1.block_on(self.read_at(offset, len))
    }

    /// Up to `len` bytes at `offset`, what was written included (a file from `Mount::edit`).
    pub async fn read_at(&self, offset: u64, len: u32) -> Result<Vec<u8>> {
        let writer = self.0.lock().await;
        Ok(writer.as_ref().ok_or_else(|| other("closed"))?.read_at(offset, len).await?.to_vec())
    }

    /// What was written so far is on the server's stable storage when this returns (fsync).
    pub async fn sync(&self) -> Result<()> {
        let writer = self.0.lock().await;
        Ok(writer.as_ref().ok_or_else(|| other("closed"))?.flush().await?)
    }

    /// Everything written is on the server's stable storage when this returns.
    pub async fn finish(&self) -> Result<()> {
        let writer = self.0.lock().await.take();
        Ok(writer.ok_or_else(|| other("closed"))?.close().await?)
    }
}
