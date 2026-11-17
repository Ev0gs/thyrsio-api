package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.registre.RegisterBook;
import com.pierr.thyrsio_api.registre.RegisterEntryDraft;
import com.pierr.thyrsio_api.registre.RegisterJournal;
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
    private final RegisterJournal journal;

    public WineLotService(WineLotRepository wineLotRepository,
                          TankRepository tankRepository,
                          SupplierLookup supplierLookup,
                          RegisterJournal journal) {
        this.wineLotRepository = wineLotRepository;
        this.tankRepository = tankRepository;
        this.supplierLookup = supplierLookup;
        this.journal = journal;
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

    /**
     * Corriger la fiche d'un lot. Le registre garde la trace : une contre-écriture rattachée à la réception
     * indique chaque champ réglementaire modifié, avec sa valeur avant et après.
     */
    @Transactional
    public WineLotResponse update(UUID id, UpdateWineLotRequest request, UUID userId) {
        WineLot lot = findLot(id);
        Map<String, Object> before = registerFields(lot);
        String denominationBefore = CuverieService.denomination(lot);
        lot.setAppellation(request.appellation().trim());
        lot.setColor(request.color());
        lot.setVintage(request.vintage());
        lot.setAlcohol(request.alcohol());
        lot.setSupplierLotNumber(blankToNull(request.supplierLotNumber()));
        lot.setDaeNumber(blankToNull(request.daeNumber()));
        lot.setPricePerHl(request.pricePerHl());
        lot.setNotes(blankToNull(request.notes()));

        Map<String, Object> after = registerFields(lot);
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : before.keySet()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", before.get(field));
                change.put("after", after.get(field));
                changes.put(field, change);
            }
        }
        if (!changes.isEmpty()) {
            journal.record(new RegisterEntryDraft(RegisterBook.ENTREES_VRAC, "CORRECTION", Instant.now(), userId,
                    lot.getId(), lot.getCode(), CuverieService.denomination(lot), null, null, lot.getDaeNumber(),
                    "Correction de la fiche du lot " + lot.getCode() + " (" + String.join(", ", changes.keySet())
                            + ")" + (denominationBefore.equals(CuverieService.denomination(lot)) ? ""
                            : " : " + denominationBefore + " devient " + CuverieService.denomination(lot)),
                    Map.of("changes", changes), 0,
                    journal.lastEntryId(lot.getId(), "RECEPTION").orElse(null)));
        }
        return toResponse(lot);
    }

    /** Les champs de la fiche qui figurent au registre : leurs corrections y sont tracées. */
    private static Map<String, Object> registerFields(WineLot lot) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("appellation", lot.getAppellation());
        fields.put("color", lot.getColor().name());
        fields.put("vintage", lot.getVintage());
        fields.put("alcohol", lot.getAlcohol() != null ? lot.getAlcohol().stripTrailingZeros() : null);
        fields.put("supplierLotNumber", lot.getSupplierLotNumber());
        fields.put("dae", lot.getDaeNumber());
        return fields;
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
