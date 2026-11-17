package com.pierr.thyrsio_api.registre;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * La porte d'entrée du registre pour les autres modules : chaque opération y inscrit son écriture,
 * dans la transaction de l'opération elle-même (si l'opération échoue, l'écriture disparaît avec elle).
 *
 * Les écritures sont numérotées sans trou et chaînées : chacune porte l'empreinte SHA-256 de la précédente
 * et la sienne, calculée sur tout son contenu. Modifier une écriture en base, même hors de l'application,
 * casse la chaîne et se voit à la vérification.
 */
@Service
public class RegisterJournal {

    private final RegisterEntryRepository entryRepository;
    private final RegisterHeadRepository headRepository;
    private final JdbcTemplate jdbc;

    public RegisterJournal(RegisterEntryRepository entryRepository, RegisterHeadRepository headRepository,
                           JdbcTemplate jdbc) {
        this.entryRepository = entryRepository;
        this.headRepository = headRepository;
        this.jdbc = jdbc;
    }

    /** Inscrit l'écriture et la scelle. Exige la transaction de l'opération. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegisterEntry record(RegisterEntryDraft draft) {
        Objects.requireNonNull(draft.book(), "book");
        Objects.requireNonNull(draft.operationAt(), "operationAt");
        RegisterHead head = headRepository.lockHead();

        long sequence = head.getLastSequence() + 1;
        // La base garde la microseconde : on s'y aligne avant de sceller
        Instant operationAt = draft.operationAt().truncatedTo(ChronoUnit.MICROS);
        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String data = CanonicalJson.write(draft.data() != null ? draft.data() : Map.of());
        boolean late = draft.deadlineDays() > 0
                && RegisterDeadline.isLate(operationAt, recordedAt, draft.deadlineDays());
        RegisterEntryDraft normalized = new RegisterEntryDraft(draft.book(), draft.kind(), operationAt,
                draft.recordedBy(), draft.wineLotId(), draft.wineLotCode(), draft.denomination(), draft.tanks(),
                scale(draft.volumeHl()), draft.reference(), draft.summary(), draft.data(), draft.deadlineDays(),
                draft.correctsEntryId());
        String recordedByName = userName(draft.recordedBy());

        String hash = hash(head.getLastHash(), canonicalLine(sequence, normalized, recordedAt, recordedByName,
                data, late));
        RegisterEntry entry = entryRepository.save(new RegisterEntry(sequence, normalized, operationAt, recordedAt,
                recordedByName, data, late, head.getLastHash(), hash));
        head.advance(sequence, hash);
        return entry;
    }

    /** La dernière écriture d'un type pour un lot : ce qu'une contre-écriture vient corriger. */
    @Transactional(readOnly = true)
    public Optional<UUID> lastEntryId(UUID wineLotId, String kind) {
        return entryRepository.findFirstByWineLotIdAndKindOrderBySequenceNoDesc(wineLotId, kind)
                .map(RegisterEntry::getId);
    }

    /** Relit tout le registre et recalcule chaque empreinte. */
    @Transactional(readOnly = true)
    public RegisterVerification verify() {
        long expected = 1;
        String previousHash = "0".repeat(64);
        try (Stream<RegisterEntry> entries = entryRepository.streamAllByOrderBySequenceNoAsc()) {
            for (RegisterEntry entry : (Iterable<RegisterEntry>) entries::iterator) {
                if (entry.getSequenceNo() != expected) {
                    return RegisterVerification.broken(expected - 1, entry.getSequenceNo(),
                            "Numérotation interrompue : n° " + expected + " attendu, n° " + entry.getSequenceNo() + " trouvé");
                }
                if (!entry.getPreviousHash().equals(previousHash)) {
                    return RegisterVerification.broken(expected - 1, entry.getSequenceNo(),
                            "L'écriture n° " + entry.getSequenceNo() + " ne suit plus la précédente");
                }
                RegisterEntryDraft stored = new RegisterEntryDraft(entry.getBook(), entry.getKind(),
                        entry.getOperationAt(), entry.getRecordedBy(), entry.getWineLotId(), entry.getWineLotCode(),
                        entry.getDenomination(), entry.getTanks(), scale(entry.getVolumeHl()), entry.getReference(),
                        entry.getSummary(), null, 0, entry.getCorrectsEntryId());
                String recomputed = hash(previousHash, canonicalLine(entry.getSequenceNo(), stored,
                        entry.getRecordedAt(), entry.getRecordedByName(), entry.getData(), entry.isLate()));
                if (!recomputed.equals(entry.getHash())) {
                    return RegisterVerification.broken(expected - 1, entry.getSequenceNo(),
                            "L'écriture n° " + entry.getSequenceNo() + " a été modifiée après son inscription");
                }
                previousHash = entry.getHash();
                expected++;
            }
        }
        RegisterHead head = headRepository.findById((short) 1).orElseThrow();
        if (head.getLastSequence() != expected - 1) {
            return RegisterVerification.broken(expected - 1, expected,
                    "Il manque des écritures : la dernière inscrite porte le n° " + head.getLastSequence());
        }
        return RegisterVerification.valid(expected - 1, previousHash);
    }

    // ========== Le sceau ==========

    /** Tout le contenu de l'écriture, sur une ligne, dans un ordre fixe. */
    private static String canonicalLine(long sequence, RegisterEntryDraft draft, Instant recordedAt,
                                        String recordedByName, String data, boolean late) {
        return Stream.of(sequence, draft.book(), draft.kind(), draft.operationAt(), recordedAt, draft.recordedBy(),
                        recordedByName, draft.wineLotId(), draft.wineLotCode(), draft.denomination(), draft.tanks(),
                        draft.volumeHl() != null ? draft.volumeHl().toPlainString() : null, draft.reference(),
                        draft.summary(), data, draft.correctsEntryId(), late)
                .map(value -> value == null ? "" : value.toString().replace("|", "\\|"))
                .collect(Collectors.joining("|"));
    }

    static String hash(String previousHash, String line) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousHash.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(line.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static BigDecimal scale(BigDecimal volume) {
        return volume == null ? null : volume.setScale(4, RoundingMode.HALF_UP);
    }

    /** Le nom de l'auteur, figé dans l'écriture : il reste lisible même si le compte disparaît. */
    private String userName(UUID keycloakId) {
        if (keycloakId == null) {
            return null;
        }
        List<String> names = jdbc.queryForList("select name from users where keycloak_id = ?", String.class, keycloakId);
        return names.isEmpty() ? null : names.getFirst();
    }
}
