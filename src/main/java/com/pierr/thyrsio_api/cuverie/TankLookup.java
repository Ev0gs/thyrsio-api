package com.pierr.thyrsio_api.cuverie;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La porte d'entrée des autres modules vers les cuves (lecture seule). */
@Service
public class TankLookup {

    private final TankRepository tankRepository;

    public TankLookup(TankRepository tankRepository) {
        this.tankRepository = tankRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TankInfo> findById(UUID id) {
        return tankRepository.findWithLotById(id).map(TankInfo::from);
    }

    /** Plusieurs cuves en une requête, indexées par id. */
    @Transactional(readOnly = true)
    public Map<UUID, TankInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tankRepository.findWithLotByIdIn(ids).stream()
                .map(TankInfo::from)
                .collect(Collectors.toMap(TankInfo::id, Function.identity()));
    }
}
