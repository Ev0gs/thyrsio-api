package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.dto.*;
import com.pierr.thyrsio_api.referentiel.SupplierInfo;
import com.pierr.thyrsio_api.referentiel.SupplierKind;
import com.pierr.thyrsio_api.referentiel.SupplierLookup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Tout ce qui fait bouger du vin : réception, transfert, perte, jaugeage.
 * Même principe que StockService : chaque opération verrouille les cuves concernées,
 * met à jour leur contenu et écrit les mouvements, dans une seule transaction.
 */
@Service
public class CuverieService {

    private final TankRepository tankRepository;
    private final WineLotRepository wineLotRepository;
    private final TankMovementRepository movementRepository;
    private final WineLotService wineLotService;
    private final SupplierLookup supplierLookup;

    public CuverieService(TankRepository tankRepository,
                          WineLotRepository wineLotRepository,
                          TankMovementRepository movementRepository,
                          WineLotService wineLotService,
                          SupplierLookup supplierLookup) {
        this.tankRepository = tankRepository;
        this.wineLotRepository = wineLotRepository;
        this.movementRepository = movementRepository;
        this.wineLotService = wineLotService;
        this.supplierLookup = supplierLookup;
    }

    // ========== Réception ==========

    /** Crée le lot de vin et remplit les cuves indiquées. Tout ou rien : une cuve trop petite annule tout. */
    @Transactional
    public WineLotResponse receive(ReceptionRequest request, UUID userId) {
        SupplierInfo supplier = supplierLookup.findById(request.supplierId())
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur inconnu : " + request.supplierId()));
        if (supplier.kind() != SupplierKind.WINE) {
            throw new IllegalArgumentException("Le fournisseur " + supplier.name() + " n'est pas un fournisseur de vin");
        }
        if (!supplier.active()) {
            throw new BusinessRuleException("Le fournisseur " + supplier.name() + " est archivé");
        }
        Set<UUID> tankIds = new HashSet<>();
        for (TankFill fill : request.fills()) {
            if (!tankIds.add(fill.tankId())) {
                throw new IllegalArgumentException("Une même cuve figure deux fois dans la réception");
            }
        }

        Instant receivedAt = request.receivedAt() != null ? request.receivedAt() : Instant.now();
        WineLot lot = new WineLot(wineLotService.nextCode(receivedAt), supplier.id(),
                request.appellation().trim(), request.color(), receivedAt, userId);
        lot.setVintage(request.vintage());
        lot.setAlcohol(request.alcohol());
        lot.setSupplierLotNumber(blankToNull(request.supplierLotNumber()));
        lot.setDaeNumber(blankToNull(request.daeNumber()));
        lot.setPricePerHl(request.pricePerHl());
        lot.setNotes(blankToNull(request.notes()));
        wineLotRepository.save(lot);

        Map<UUID, Tank> tanks = lockTanks(tankIds);
        for (TankFill fill : request.fills()) {
            Tank tank = tanks.get(fill.tankId());
            tank.apply(lot, fill.volumeHl());
            movementRepository.save(new TankMovement(tank, lot, TankMovementType.RECEPTION, fill.volumeHl(), null,
                    receivedAt, lot.getDaeNumber(), null, userId));
        }
        return wineLotService.toResponse(lot);
    }

    // ========== Transfert ==========

    /** Sortie de la cuve d'origine, perte éventuelle, entrée dans la destination : 2 ou 3 mouvements liés. */
    @Transactional
    public List<TankMovementResponse> transfer(TransferRequest request, UUID userId) {
        if (request.sourceTankId().equals(request.destinationTankId())) {
            throw new IllegalArgumentException("La cuve d'origine et la cuve de destination doivent être différentes");
        }
        Map<UUID, Tank> tanks = lockTanks(List.of(request.sourceTankId(), request.destinationTankId()));
        Tank source = tanks.get(request.sourceTankId());
        Tank destination = tanks.get(request.destinationTankId());
        if (source.isEmpty()) {
            throw new BusinessRuleException("La cuve " + source.getCode() + " est vide : rien à transférer");
        }

        BigDecimal loss = request.lossHl() != null ? request.lossHl() : BigDecimal.ZERO;
        BigDecimal volume;
        if (Boolean.TRUE.equals(request.emptySource())) {
            volume = source.getVolumeHl().subtract(loss);
            if (volume.signum() <= 0) {
                throw new IllegalArgumentException("La perte est supérieure ou égale au volume de la cuve "
                        + source.getCode() + " : rien n'arriverait dans la destination");
            }
        } else {
            if (request.volumeHl() == null) {
                throw new IllegalArgumentException("Le volume transféré est obligatoire (ou emptySource=true)");
            }
            volume = request.volumeHl();
        }

        // Le lot est lu AVANT de vider la cuve : une fois vide, elle n'en a plus
        WineLot lot = source.getWineLot();
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();
        String notes = blankToNull(request.notes());
        String reference = source.getCode() + " → " + destination.getCode();
        UUID transferId = UUID.randomUUID();

        // On vérifie d'abord tout ce qui sort, en une fois : un message clair si volume + perte dépasse
        BigDecimal leaving = volume.add(loss);
        if (leaving.compareTo(source.getVolumeHl()) > 0) {
            throw new BusinessRuleException("Volume insuffisant dans la cuve " + source.getCode() + " : "
                    + Tank.plain(source.getVolumeHl()) + " hl présents, " + Tank.plain(leaving)
                    + " hl demandés (transfert + perte)");
        }

        List<TankMovement> movements = new ArrayList<>();
        source.apply(lot, volume.negate());
        movements.add(new TankMovement(source, lot, TankMovementType.TRANSFER_OUT, volume.negate(), transferId,
                occurredAt, reference, notes, userId));
        if (loss.signum() > 0) {
            source.apply(lot, loss.negate());
            movements.add(new TankMovement(source, lot, TankMovementType.LOSS, loss.negate(), transferId,
                    occurredAt, reference, "Perte au transfert", userId));
        }
        destination.apply(lot, volume);
        movements.add(new TankMovement(destination, lot, TankMovementType.TRANSFER_IN, volume, transferId,
                occurredAt, reference, notes, userId));

        return movementRepository.saveAll(movements).stream().map(TankMovementResponse::from).toList();
    }

    // ========== Perte et jaugeage ==========

    @Transactional
    public TankMovementResponse recordLoss(UUID tankId, LossRequest request, UUID userId) {
        Tank tank = lockTank(tankId);
        if (tank.isEmpty()) {
            throw new BusinessRuleException("La cuve " + tank.getCode() + " est vide : aucune perte possible");
        }
        WineLot lot = tank.getWineLot();
        tank.apply(lot, request.volumeHl().negate());
        TankMovement movement = movementRepository.save(new TankMovement(
                tank, lot, TankMovementType.LOSS, request.volumeHl().negate(), null,
                request.occurredAt() != null ? request.occurredAt() : Instant.now(),
                blankToNull(request.reference()), blankToNull(request.notes()), userId));
        return TankMovementResponse.from(movement);
    }

    /** On saisit ce qu'on a mesuré ; l'application en déduit l'écart et crée un ADJUSTMENT. */
    @Transactional
    public TankMovementResponse measure(UUID tankId, MeasureRequest request, UUID userId) {
        Tank tank = lockTank(tankId);
        BigDecimal delta = request.measuredHl().subtract(tank.getVolumeHl());
        if (delta.signum() == 0) {
            throw new BusinessRuleException("Le volume mesuré est égal au volume enregistré : aucun ajustement à faire");
        }

        WineLot lot;
        if (tank.isEmpty()) {
            if (request.wineLotId() == null) {
                throw new IllegalArgumentException("La cuve " + tank.getCode()
                        + " est vide dans l'application : précisez le lot de vin qu'elle contient");
            }
            lot = wineLotRepository.findById(request.wineLotId())
                    .orElseThrow(() -> new IllegalArgumentException("Lot de vin inconnu : " + request.wineLotId()));
        } else {
            lot = tank.getWineLot();
            if (request.wineLotId() != null && !request.wineLotId().equals(lot.getId())) {
                throw new IllegalArgumentException("La cuve " + tank.getCode() + " contient le lot " + lot.getCode()
                        + " : pour changer de vin, utilisez un transfert");
            }
        }

        tank.apply(lot, delta);
        TankMovement movement = movementRepository.save(new TankMovement(
                tank, lot, TankMovementType.ADJUSTMENT, delta, null,
                request.occurredAt() != null ? request.occurredAt() : Instant.now(),
                "Jaugeage : " + Tank.plain(request.measuredHl()) + " hl", blankToNull(request.notes()), userId));
        return TankMovementResponse.from(movement);
    }

    // ========== Mise en bouteille (appelé par le module Production) ==========

    /**
     * Le vin tiré d'une cuve pour une mise : un mouvement BOTTLING.
     * Public : c'est la porte d'entrée du module Production, qui l'appelle dans sa propre transaction.
     */
    @Transactional
    public void recordBottling(UUID tankId, BigDecimal volumeHl, Instant occurredAt, String reference, UUID userId) {
        if (volumeHl.signum() <= 0) {
            throw new IllegalArgumentException("Le volume mis en bouteille doit être positif");
        }
        Tank tank = tankRepository.findForUpdateById(tankId)
                .orElseThrow(() -> new IllegalArgumentException("Cuve inconnue : " + tankId));
        if (tank.isEmpty()) {
            throw new BusinessRuleException("La cuve " + tank.getCode() + " est vide : aucun vin à mettre en bouteille");
        }
        WineLot lot = tank.getWineLot();
        tank.apply(lot, volumeHl.negate());
        movementRepository.save(new TankMovement(tank, lot, TankMovementType.BOTTLING, volumeHl.negate(), null,
                occurredAt, reference, null, userId));
    }

    // ========== Lecture ==========

    @Transactional(readOnly = true)
    public Page<TankMovementResponse> listMovements(UUID tankId, UUID wineLotId, TankMovementType type,
                                                    UUID transferId, Instant from, Instant to, Pageable pageable) {
        List<Specification<TankMovement>> filters = new ArrayList<>();
        if (tankId != null) filters.add(CuverieSpecifications.movementOfTank(tankId));
        if (wineLotId != null) filters.add(CuverieSpecifications.movementOfLot(wineLotId));
        if (type != null) filters.add(CuverieSpecifications.movementOfType(type));
        if (transferId != null) filters.add(CuverieSpecifications.movementOfTransfer(transferId));
        if (from != null) filters.add(CuverieSpecifications.occurredFrom(from));
        if (to != null) filters.add(CuverieSpecifications.occurredBefore(to));
        return movementRepository.findAll(Specification.allOf(filters), pageable).map(TankMovementResponse::from);
    }

    // ========== Verrous ==========

    /**
     * Verrouille plusieurs cuves, toujours dans le même ordre (par id) : deux transferts croisés
     * (A → B et B → A en même temps) ne peuvent pas s'attendre l'un l'autre indéfiniment.
     */
    private Map<UUID, Tank> lockTanks(Collection<UUID> ids) {
        Map<UUID, Tank> tanks = new HashMap<>();
        for (UUID id : new TreeSet<>(ids)) {
            tanks.put(id, tankRepository.findForUpdateById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Cuve inconnue : " + id)));
        }
        return tanks;
    }

    /** Une cuve désignée dans l'URL : 404 si elle n'existe pas. */
    private Tank lockTank(UUID id) {
        return tankRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuve", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
