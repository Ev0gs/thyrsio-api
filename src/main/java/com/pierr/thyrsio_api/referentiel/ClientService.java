package com.pierr.thyrsio_api.referentiel;

import com.pierr.thyrsio_api.common.Codes;
import com.pierr.thyrsio_api.common.DuplicateCodeException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.referentiel.dto.ClientFields;
import com.pierr.thyrsio_api.referentiel.dto.ClientResponse;
import com.pierr.thyrsio_api.referentiel.dto.CreateClientRequest;
import com.pierr.thyrsio_api.referentiel.dto.UpdateClientRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ClientService {

    private static final String DEFAULT_LANGUAGE = "fr";

    // TVA intracommunautaire : préfixe pays (FR, DE, EL pour la Grèce...) puis 2 à 12 caractères
    private static final Pattern VAT_NUMBER = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,12}$");

    private final ClientRepository clientRepository;
    private final GeocodingTrigger geocoding;

    public ClientService(ClientRepository clientRepository, GeocodingTrigger geocoding) {
        this.clientRepository = clientRepository;
        this.geocoding = geocoding;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> list(String search, String countryCode, boolean includeArchived, Pageable pageable) {
        List<Specification<Client>> filters = new ArrayList<>();
        if (!includeArchived) {
            filters.add(ClientSpecifications.isActive());
        }
        if (countryCode != null && !countryCode.isBlank()) {
            filters.add(ClientSpecifications.inCountry(countryCode.trim().toUpperCase(Locale.ROOT)));
        }
        if (search != null && !search.isBlank()) {
            filters.add(ClientSpecifications.matches(search));
        }
        return clientRepository.findAll(Specification.allOf(filters), pageable)
                .map(ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID id) {
        return ClientResponse.from(findClient(id));
    }

    @Transactional
    public ClientResponse create(CreateClientRequest request) {
        String code = Codes.normalize(request.code());
        if (clientRepository.existsByCode(code)) {
            throw new DuplicateCodeException("Client", code);
        }
        Client client = new Client(code);
        applyFields(client, request);
        geocoding.addressChanged();
        return ClientResponse.from(clientRepository.save(client));
    }

    /** Remplacement complet (PUT) : un champ facultatif vide efface la valeur existante. */
    @Transactional
    public ClientResponse update(UUID id, UpdateClientRequest request) {
        Client client = findClient(id);
        applyFields(client, request);
        geocoding.addressChanged();
        return ClientResponse.from(client);
    }

    @Transactional
    public void archive(UUID id) {
        findClient(id).archive();
    }

    @Transactional
    public void reactivate(UUID id) {
        findClient(id).reactivate();
    }

    // --- Méthodes utilitaires ---

    private Client findClient(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
    }

    /** Une seule méthode pour la création et la modification, grâce à l'interface ClientFields. */
    private static void applyFields(Client client, ClientFields fields) {
        client.setName(fields.name().trim());
        client.setCountryCode(IsoCodes.country(fields.countryCode()));
        client.setLanguage(normalizeLanguage(fields.language()));
        client.setAddressLine1(blankToNull(fields.addressLine1()));
        client.setAddressLine2(blankToNull(fields.addressLine2()));
        client.setPostalCode(blankToNull(fields.postalCode()));
        client.setCity(blankToNull(fields.city()));
        client.setEmail(blankToNull(fields.email()));
        client.setPhone(blankToNull(fields.phone()));
        client.setVatNumber(normalizeVatNumber(fields.vatNumber()));
        client.setExciseNumber(compactUpper(fields.exciseNumber()));
        client.setDefaultIncoterm(fields.defaultIncoterm());
        client.setGs1LabelingRequired(Boolean.TRUE.equals(fields.gs1LabelingRequired()));
        client.setNotes(blankToNull(fields.notes()));
    }

    private static String normalizeLanguage(String value) {
        return (value == null || value.isBlank()) ? DEFAULT_LANGUAGE : IsoCodes.language(value);
    }

    private static String normalizeVatNumber(String value) {
        String vat = compactUpper(value);
        if (vat != null && !VAT_NUMBER.matcher(vat).matches()) {
            throw new IllegalArgumentException(
                    "N° de TVA intracommunautaire invalide : " + vat + " (attendu : préfixe pays puis 2 à 12 caractères)");
        }
        return vat;
    }

    /** "fr 12.345-678 901" devient "FR12345678901" : on retire espaces, points et tirets. */
    private static String compactUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}