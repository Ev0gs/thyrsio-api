package com.pierr.thyrsio_api.referentiel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La lecture des appellations par les autres modules (la production, pour ses numéros de lot). */
@Component
public class AppellationLookup {

    private final AppellationRepository repository;

    public AppellationLookup(AppellationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<AppellationInfo> findById(UUID id) {
        return repository.findById(id).map(AppellationLookup::toInfo);
    }

    @Transactional(readOnly = true)
    public Map<UUID, AppellationInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdIn(ids).stream()
                .map(AppellationLookup::toInfo)
                .collect(Collectors.toMap(AppellationInfo::id, Function.identity()));
    }

    private static AppellationInfo toInfo(Appellation appellation) {
        return new AppellationInfo(appellation.getId(), appellation.getCode(), appellation.getName(),
                appellation.isActive());
    }
}
