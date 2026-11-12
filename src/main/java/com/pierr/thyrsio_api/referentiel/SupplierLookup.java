package com.pierr.thyrsio_api.referentiel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** La porte d'entrée des autres modules vers les fournisseurs, comme ArticleLookup pour les articles. */
@Service
public class SupplierLookup {

    private final SupplierRepository supplierRepository;

    public SupplierLookup(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SupplierInfo> findById(UUID id) {
        return supplierRepository.findById(id).map(SupplierLookup::toInfo);
    }

    /** Plusieurs fournisseurs en une seule requête, indexés par id : pour enrichir une liste sans N+1. */
    @Transactional(readOnly = true)
    public Map<UUID, SupplierInfo> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierRepository.findAllById(ids).stream()
                .map(SupplierLookup::toInfo)
                .collect(Collectors.toMap(SupplierInfo::id, Function.identity()));
    }

    private static SupplierInfo toInfo(Supplier supplier) {
        return new SupplierInfo(supplier.getId(), supplier.getName(), supplier.getKind(), supplier.isActive());
    }
}
