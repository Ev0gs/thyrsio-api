package com.pierr.thyrsio_api.production;

import com.pierr.thyrsio_api.referentiel.AppellationInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les numéros de lot des bouteilles : L + sigle de l'appellation + millésime (2 chiffres) + numéro d'ordre.
 * Bordeaux 2024, premier lot : LBX2401 ; le suivant : LBX2402 ; après LBX2499 vient LBX24100.
 * Un vin sans millésime prend NM (non millésimé) : LBXNM01.
 * <p>
 * Le numéro d'ordre vient d'un compteur par préfixe (LBX24), incrémenté en base dans la transaction de
 * la production : deux mises planifiées en même temps ne peuvent pas recevoir le même numéro, et un
 * numéro abandonné (production retirée ou annulée) n'est jamais redonné.
 */
@Component
class LotNumberGenerator {

    static final String NON_VINTAGE = "NM";

    private final JdbcTemplate jdbc;
    private final BottlingProductionRepository productionRepository;

    LotNumberGenerator(JdbcTemplate jdbc, BottlingProductionRepository productionRepository) {
        this.jdbc = jdbc;
        this.productionRepository = productionRepository;
    }

    /** LBX24 : ce qui précède le numéro d'ordre. */
    static String prefix(AppellationInfo appellation, Integer vintage) {
        return "L" + appellation.code() + (vintage == null ? NON_VINTAGE : "%02d".formatted(vintage % 100));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    String next(AppellationInfo appellation, Integer vintage) {
        String prefix = prefix(appellation, vintage);
        while (true) {
            // INSERT ... ON CONFLICT DO UPDATE verrouille la ligne du compteur jusqu'à la fin de la transaction
            Integer value = jdbc.queryForObject("""
                    INSERT INTO lot_number_sequences (prefix, last_value) VALUES (?, 1)
                    ON CONFLICT (prefix) DO UPDATE SET last_value = lot_number_sequences.last_value + 1
                    RETURNING last_value
                    """, Integer.class, prefix);
            String candidate = prefix + "%02d".formatted(value);
            // Un lot saisi à la main sous ce numéro (stock des produits finis) : on passe au suivant
            boolean taken = productionRepository.existsByLotNumber(candidate)
                    || Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM lots WHERE lot_number = ?)", Boolean.class, candidate));
            if (!taken) {
                return candidate;
            }
        }
    }
}
