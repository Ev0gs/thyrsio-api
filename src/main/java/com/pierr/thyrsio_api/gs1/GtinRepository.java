package com.pierr.thyrsio_api.gs1;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GtinRepository extends JpaRepository<Gtin, UUID>, JpaSpecificationExecutor<Gtin> {

    boolean existsByGtin(String gtin);

    /** Les gencodes qui commencent par ce préfixe, retirés compris : aucun ne doit resservir. */
    @Query("select g.gtin from Gtin g where g.gtin like :prefix")
    List<String> findGtinsLike(@Param("prefix") String prefixPattern);

    @Query("select count(g) from Gtin g where g.gtin like :prefix")
    long countLike(@Param("prefix") String prefixPattern);

    boolean existsByFinishedArticleIdAndPackagingAndUnitsPerPackAndStatus(
            UUID finishedArticleId, GtinPackaging packaging, int unitsPerPack, GtinStatus status);
}
