package com.pierr.thyrsio_api.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BottlingRepository extends JpaRepository<Bottling, UUID>, JpaSpecificationExecutor<Bottling> {

    @Query("select b.code from Bottling b where b.code like :pattern")
    List<String> findCodesLike(@Param("pattern") String pattern);
}
