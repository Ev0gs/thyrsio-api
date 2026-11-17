package com.pierr.thyrsio_api.company;

import com.pierr.thyrsio_api.company.dto.CompanySettingsRequest;
import com.pierr.thyrsio_api.company.dto.CompanySettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CompanySettingsService {

    private final CompanySettingsRepository repository;

    public CompanySettingsService(CompanySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompanySettingsResponse get() {
        return CompanySettingsResponse.from(settings());
    }

    @Transactional
    public CompanySettingsResponse update(CompanySettingsRequest request) {
        CompanySettings settings = settings();
        settings.update(clean(request.legalName()),
                clean(request.exciseNumber()) != null ? clean(request.exciseNumber()).toUpperCase(Locale.ROOT).replace(" ", "") : null,
                clean(request.siret()) != null ? clean(request.siret()).replace(" ", "") : null,
                clean(request.vatNumber()) != null ? clean(request.vatNumber()).toUpperCase(Locale.ROOT).replace(" ", "") : null,
                clean(request.addressLine1()), clean(request.addressLine2()), clean(request.postalCode()),
                clean(request.city()),
                request.countryCode() != null ? request.countryCode().toUpperCase(Locale.ROOT) : "FR",
                clean(request.phone()), clean(request.email()), request.registerStartDate());
        return CompanySettingsResponse.from(settings);
    }

    private CompanySettings settings() {
        return repository.findById((short) 1).orElseThrow();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
