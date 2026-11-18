package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.Codes;
import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.AppellationRequest;
import com.pierr.thyrsio_api.referentiel.dto.AppellationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Les appellations. Changer un sigle ne touche pas les numéros déjà attribués :
 * seuls les suivants l'utilisent. Une appellation archivée ne se choisit plus pour une nouvelle production.
 */
@Service
public class AppellationService {

    private final AppellationRepository repository;

    public AppellationService(AppellationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AppellationResponse> list(boolean includeArchived) {
        List<Appellation> appellations = includeArchived
                ? repository.findAllByOrderByNameAsc()
                : repository.findByActiveTrueOrderByNameAsc();
        return appellations.stream().map(AppellationResponse::from).toList();
    }

    @Transactional
    public AppellationResponse create(AppellationRequest request) {
        String code = Codes.normalize(request.code());
        String name = request.name().trim();
        if (repository.existsByCode(code)) {
            throw new DuplicateCodeException("Appellation", code);
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("L'appellation " + name + " existe déjà");
        }
        return AppellationResponse.from(repository.save(new Appellation(code, name)));
    }

    @Transactional
    public AppellationResponse update(UUID id, AppellationRequest request) {
        Appellation appellation = find(id);
        String code = Codes.normalize(request.code());
        String name = request.name().trim();
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new DuplicateCodeException("Appellation", code);
        }
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BusinessRuleException("L'appellation " + name + " existe déjà");
        }
        appellation.rename(code, name);
        return AppellationResponse.from(appellation);
    }

    @Transactional
    public void archive(UUID id) {
        find(id).archive();
    }

    @Transactional
    public void restore(UUID id) {
        find(id).restore();
    }

    private Appellation find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appellation", id));
    }
}
