//! Couche Application (Cas d'usage orchestrant les ports du domaine).

pub mod auth_use_cases;
pub mod file_use_cases;
pub mod share_use_cases;

#[cfg(test)]
pub(crate) mod fakes;
