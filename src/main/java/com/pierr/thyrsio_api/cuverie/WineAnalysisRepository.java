package com.pierr.thyrsio_api.cuverie;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WineAnalysisRepository extends JpaRepository<WineAnalysis, UUID> {

    @EntityGraph(attributePaths = "tank")
    List<WineAnalysis> findByWineLotIdOrderByAnalyzedOnDescCreatedAtDesc(UUID wineLotId);
}
