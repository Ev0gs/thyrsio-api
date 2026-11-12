package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.dto.UpdateWineLotRequest;
import com.pierr.thyrsio_api.cuverie.dto.WineLotResponse;
import com.pierr.thyrsio_api.cuverie.dto.WineLotRow;
import com.pierr.thyrsio_api.referentiel.SupplierInfo;
import com.pierr.thyrsio_api.referentiel.SupplierLookup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WineLotService {

    /** L'année du code de lot est celle de la réception, à l'heure française. */
    private static final ZoneId CODE_ZONE = ZoneId.of("Europe/Paris");

    private final WineLotRepository wineLotRepository;
    private final TankRepository tankRepository;
    private final SupplierLookup supplierLookup;

    public WineLotService(WineLotRepository wineLotRepository,
                          TankRepository tankRepository,
                          SupplierLookup supplierLookup) {
        this.wineLotRepository = wineLotRepository;
        this.tankRepository = tankRepository;
        this.supplierLookup = supplierLookup;
    }

    // ========== Lecture ==========

    /** 3 requêtes pour une page : les lots, leurs volumes en cuve, leurs fournisseurs. */
    @Transactional(readOnly = true)
    public Page<WineLotRow> list(boolean inTankOnly, String search, UUID supplierId, Pageable pageable) {
        List<Specification<WineLot>> filters = new ArrayList<>();
        if (inTankOnly) filters.add(CuverieSpecifications.lotInTank());
        if (search != null && !search.isBlank()) filters.add(CuverieSpecifications.lotMatches(search));
        if (supplierId != null) filters.add(CuverieSpecifications.lotOfSupplier(supplierId));

        Page<WineLot> page = wineLotRepository.findAll(Specification.allOf(filters), pageable);
        Set<UUID> lotIds = page.stream().map(WineLot::getId).collect(Collectors.toSet());
        Map<UUID, WineLotVolume> volumes = lotIds.isEmpty() ? Map.of()
                : tankRepository.volumesByLot(lotIds).stream()
                .collect(Collectors.toMap(WineLotVolume::wineLotId, Function.identity()));
        Map<UUID, SupplierInfo> suppliers = supplierLookup.findAllById(
                page.stream().map(WineLot::getSupplierId).collect(Collectors.toSet()));

        return page.map(lot -> {
            WineLotVolume volume = volumes.get(lot.getId());
            return WineLotRow.from(lot, suppliers.get(lot.getSupplierId()),
                    volume != null ? volume.volumeHl() : BigDecimal.ZERO,
                    volume != null ? volume.tankCount() : 0);
        });
    }

    @Transactional(readOnly = true)
    public WineLotResponse get(UUID id) {
        return toResponse(findLot(id));
    }

    // ========== Écriture ==========

    @Transactional
    public WineLotResponse update(UUID id, UpdateWineLotRequest request) {
        WineLot lot = findLot(id);
        lot.setAppellation(request.appellation().trim());
        lot.setColor(request.color());
        lot.setVintage(request.vintage());
        lot.setAlcohol(request.alcohol());
        lot.setSupplierLotNumber(blankToNull(request.supplierLotNumber()));
        lot.setDaeNumber(blankToNull(request.daeNumber()));
        lot.setPricePerHl(request.pricePerHl());
        lot.setNotes(blankToNull(request.notes()));
        return toResponse(lot);
    }

    // ========== Pour le module (package-private) ==========

    /**
     * Le prochain code de l'année : V26-001, V26-002...
     * Deux réceptions exactement simultanées pourraient calculer le même code :
     * la contrainte UNIQUE de la base refuse alors la seconde (409), qu'il suffit de relancer.
     */
    String nextCode(Instant receivedAt) {
        int year = receivedAt.atZone(CODE_ZONE).getYear() % 100;
        String prefix = "V%02d-".formatted(year);
        int max = wineLotRepository.findCodesLike(prefix + "%").stream()
                .map(code -> code.substring(prefix.length()))
                .filter(number -> number.chars().allMatch(Character::isDigit) && !number.isEmpty())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return prefix + "%03d".formatted(max + 1);
    }

    WineLotResponse toResponse(WineLot lot) {
        SupplierInfo supplier = supplierLookup.findById(lot.getSupplierId()).orElse(null);
        return WineLotResponse.from(lot, supplier, tankRepository.findByWineLotIdOrderByCodeAsc(lot.getId()));
    }

    WineLot findLot(UUID id) {
        return wineLotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot de vin", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
