package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.SupplierRequest;
import com.pierr.thyrsio_api.referentiel.dto.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(boolean includeArchived, Pageable pageable) {
        Page<Supplier> page = includeArchived
                ? supplierRepository.findAll(pageable)
                : supplierRepository.findByActiveTrue(pageable);
        return page.map(SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.from(findSupplier(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = new Supplier(request.name().trim());
        applyOptionalFields(supplier, request);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    /** Modification complète (PUT) : un champ facultatif vide efface la valeur existante. */
    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = findSupplier(id);
        supplier.setName(request.name().trim());
        applyOptionalFields(supplier, request);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void archive(UUID id) {
        findSupplier(id).archive();
    }

    @Transactional
    public void reactivate(UUID id) {
        findSupplier(id).reactivate();
    }

    // --- Méthodes utilitaires ---

    private Supplier findSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
    }

    private static void applyOptionalFields(Supplier supplier, SupplierRequest request) {
        supplier.setEmail(blankToNull(request.email()));
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setAverageLeadTimeDays(request.averageLeadTimeDays());
    }

    /** "" ou "   " deviennent null : on ne stocke pas de chaînes vides en base. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
