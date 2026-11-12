package com.pierr.thyrsio_api.cuverie;

import com.pierr.thyrsio_api.common.Codes;
import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.cuverie.dto.CreateTankRequest;
import com.pierr.thyrsio_api.cuverie.dto.TankResponse;
import com.pierr.thyrsio_api.cuverie.dto.UpdateTankRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** La fiche des cuves. Le contenu, lui, ne bouge que par CuverieService. */
@Service
public class TankService {

    private final TankRepository tankRepository;

    public TankService(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    /** Non paginé : une cuverie compte quelques dizaines de cuves, l'écran les affiche toutes. */
    @Transactional(readOnly = true)
    public List<TankResponse> list(boolean includeArchived) {
        List<Tank> tanks = includeArchived
                ? tankRepository.findAllByOrderByCodeAsc()
                : tankRepository.findByActiveTrueOrderByCodeAsc();
        return tanks.stream().map(TankResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TankResponse get(UUID id) {
        return TankResponse.from(tankRepository.findWithLotById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuve", id)));
    }

    @Transactional
    public TankResponse create(CreateTankRequest request) {
        String code = Codes.normalize(request.code());
        if (tankRepository.existsByCode(code)) {
            throw new DuplicateCodeException("Cuve", code);
        }
        Tank tank = new Tank(code, blankToNull(request.label()), request.capacityHl());
        return TankResponse.from(tankRepository.save(tank));
    }

    @Transactional
    public TankResponse update(UUID id, UpdateTankRequest request) {
        // Verrouillée : une contenance ne doit pas baisser pendant qu'on remplit la cuve
        Tank tank = lockTank(id);
        tank.setLabel(blankToNull(request.label()));
        tank.changeCapacity(request.capacityHl());
        return TankResponse.from(tank);
    }

    @Transactional
    public TankResponse changeStatus(UUID id, TankStatus status) {
        Tank tank = lockTank(id);
        tank.changeStatus(status);
        return TankResponse.from(tank);
    }

    @Transactional
    public void archive(UUID id) {
        lockTank(id).archive();
    }

    @Transactional
    public void reactivate(UUID id) {
        lockTank(id).reactivate();
    }

    private Tank lockTank(UUID id) {
        return tankRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuve", id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
