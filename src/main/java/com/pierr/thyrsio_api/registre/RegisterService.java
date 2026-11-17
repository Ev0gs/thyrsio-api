package com.pierr.thyrsio_api.registre;

import com.pierr.thyrsio_api.registre.dto.RegisterEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** La lecture du registre : filtrée par registre, période, lot ou texte. */
@Service
public class RegisterService {

    private final RegisterEntryRepository entryRepository;

    public RegisterService(RegisterEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public Page<RegisterEntryResponse> list(RegisterBook book, Instant from, Instant to, UUID wineLotId,
                                            String search, boolean lateOnly, Pageable pageable) {
        List<Specification<RegisterEntry>> filters = new ArrayList<>();
        if (book != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("book"), book));
        }
        if (from != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("operationAt"), from));
        }
        if (to != null) {
            filters.add((root, query, cb) -> cb.lessThan(root.get("operationAt"), to));
        }
        if (wineLotId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("wineLotId"), wineLotId));
        }
        if (lateOnly) {
            filters.add((root, query, cb) -> cb.isTrue(root.get("late")));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("summary")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("wineLotCode"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("denomination"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("tanks"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("reference"), "")), pattern)));
        }
        return entryRepository.findAll(Specification.allOf(filters), pageable).map(RegisterEntryResponse::from);
    }
}
