use thiserror::Error;

/// Pure business errors of the Domain layer.
#[derive(Debug, Error, Clone, PartialEq, Eq)]
pub enum DomainError {
    #[error("Identifiants administrateur invalides.")]
    InvalidCredentials,

    #[error("Authentification requise.")]
    Unauthorized,

    #[error("Jeton expiré.")]
    TokenExpired,

    #[error("Chemin interdit ou invalide.")]
    ForbiddenPath,

    #[error("Ressource introuvable.")]
    NotFound,

    #[error("Ce slug personnalisé est déjà utilisé.")]
    SlugAlreadyExists,

    #[error("Ce slug est réservé par le système.")]
    ReservedSlug,

    #[error("Slug invalide : doit contenir 3 à 64 caractères alphanumériques, '-' ou '_'.")]
    InvalidSlug,

    #[error("La taille du dossier dépasse la limite autorisée pour l'export ZIP.")]
    PayloadTooLarge,

    #[error("Ce lien de partage a expiré.")]
    ShareExpired,

    #[error("Ce lien de partage a été révoqué.")]
    ShareRevoked,

    #[error("Le quota maximal de téléchargements est atteint.")]
    QuotaExceeded,

    #[error("Mot de passe invalide.")]
    InvalidPassword,

    #[error("Paramètre invalide : {0}")]
    ValidationError(String),

    #[error("Trop de requêtes, veuillez réessayer plus tard.")]
    RateLimited,

    #[error("Erreur interne : {0}")]
    Internal(String),
}
