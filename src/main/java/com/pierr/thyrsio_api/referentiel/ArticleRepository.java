package com.pierr.thyrsio_api.referentiel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {

    boolean existsByCode(String code);

    // On redéclare findAll pour lui ajouter l'EntityGraph : pas de problème N+1 sur la list
    @Override
    @EntityGraph(attributePaths = {"category", "unit", "mainSupplier"})
    Page<Article> findAll(Specification<Article> spec, Pageable pageable);
}
