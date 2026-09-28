//! Generates the Kotlin bindings from the built library (ci/core.sh).

fn main() {
    uniffi::uniffi_bindgen_main()
}
