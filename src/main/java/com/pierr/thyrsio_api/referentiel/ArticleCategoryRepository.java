package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, UUID> {
    Optional<ArticleCategory> findByCode(String code);

    boolean existsByCode(String code);

    // @EntityGraph : charge l'unité de chaque catégorie DANS la même requête (voir l'explication)
    @EntityGraph(attributePaths = "defaultUnit")
    List<ArticleCategory> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "defaultUnit")
    List<ArticleCategory> findByActiveTrueOrderByCodeAsc();
}
