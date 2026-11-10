package com.pierr.thyrsio_api.stock;

import java.time.Instant;
import java.util.UUID;

/** Résultat d'un GROUP BY : la date du dernier mouvement d'un article. */
public record ArticleLastMovement(UUID articleId, Instant occurredAt) {
}