package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.dto.AnalysisRequest;
import com.pierr.thyrsio_api.cuverie.dto.AnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class WineAnalysisService {

    private final WineAnalysisRepository analysisRepository;
    private final WineLotService wineLotService;
    private final TankRepository tankRepository;

    public WineAnalysisService(WineAnalysisRepository analysisRepository,
                               WineLotService wineLotService,
                               TankRepository tankRepository) {
        this.analysisRepository = analysisRepository;
        this.wineLotService = wineLotService;
        this.tankRepository = tankRepository;
    }

    /** De la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public List<AnalysisResponse> list(UUID wineLotId) {
        wineLotService.findLot(wineLotId);  // 404 si le lot n'existe pas, plutôt qu'une liste vide
        return analysisRepository.findByWineLotIdOrderByAnalyzedOnDescCreatedAtDesc(wineLotId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    @Transactional
    public AnalysisResponse add(UUID wineLotId, AnalysisRequest request, UUID userId) {
        WineLot lot = wineLotService.findLot(wineLotId);
        boolean hasValue = Stream.of(request.alcohol(), request.freeSo2(), request.totalSo2(),
                request.totalAcidity(), request.volatileAcidity()).anyMatch(value -> value != null);
        if (!hasValue && blankToNull(request.notes()) == null) {
            throw new IllegalArgumentException("Saisissez au moins une valeur mesurée ou une note");
        }
        Tank tank = null;
        if (request.tankId() != null) {
            tank = tankRepository.findById(request.tankId())
                    .orElseThrow(() -> new IllegalArgumentException("Cuve inconnue : " + request.tankId()));
        }
        WineAnalysis analysis = new WineAnalysis(lot, tank, request.analyzedOn(), request.alcohol(),
                request.freeSo2(), request.totalSo2(), request.totalAcidity(), request.volatileAcidity(),
                blankToNull(request.laboratory()), blankToNull(request.notes()), userId);
        return AnalysisResponse.from(analysisRepository.save(analysis));
    }

    /** Une analyse saisie par erreur se supprime : ce n'est pas un mouvement, rien ne dépend d'elle. */
    @Transactional
    public void delete(UUID analysisId) {
        WineAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", analysisId));
        analysisRepository.delete(analysis);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
