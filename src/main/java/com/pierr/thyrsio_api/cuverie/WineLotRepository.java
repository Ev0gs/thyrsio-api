package com.pierr.thyrsio_api.cuverie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WineLotRepository extends JpaRepository<WineLot, UUID>, JpaSpecificationExecutor<WineLot> {

    /** Les codes d'une année ("V26-%") : le suivant se calcule à partir du plus grand numéro. */
    @Query("select l.code from WineLot l where l.code like :pattern")
    List<String> findCodesLike(@Param("pattern") String pattern);
}
