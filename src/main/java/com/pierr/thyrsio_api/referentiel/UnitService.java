package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.Codes;
import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.CreateUnitRequest;
import com.pierr.thyrsio_api.referentiel.dto.UnitResponse;
import com.pierr.thyrsio_api.referentiel.dto.UpdateUnitRequest;
import com.pierr.thyrsio_api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UnitService {

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository, UserRepository userRepository) {
        this.unitRepository = unitRepository;
    }

    /** Les unités sont peu nombreuses : une simple liste triée, sans pagination. */
    @Transactional(readOnly = true)
    public List<UnitResponse> list() {
        return unitRepository.findAllByOrderByCodeAsc().stream()
                .map(UnitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitResponse get(UUID id) {
        return UnitResponse.from(findUnit(id));
    }

    @Transactional
    public UnitResponse create(CreateUnitRequest request) {
        String code = Codes.normalize(request.code());
        if(unitRepository.existsByCode(code))
            throw new DuplicateCodeException("Unité", code);
        Unit unit = new Unit(code, request.label().trim(), request.decimals());
        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse update(UUID id, UpdateUnitRequest request) {
        Unit unit = findUnit(id);
        unit.setLabel(request.label().trim());
        return UnitResponse.from(unit);
    }

    // --- Méthodes utilitaires ---

    private Unit findUnit(UUID id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unité", id));
    }
}
