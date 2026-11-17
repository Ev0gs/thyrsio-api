package com.pierr.thyrsio_api.registre.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.pierr.thyrsio_api.registre.RegisterBook;
import com.pierr.thyrsio_api.registre.RegisterEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Une écriture du registre. data : les colonnes propres au registre, telles qu'elles ont été scellées. */
public record RegisterEntryResponse(
        UUID id,
        long sequenceNo,
        RegisterBook book,
        String bookLabel,
        String kind,
        Instant operationAt,
        Instant recordedAt,
        String recordedByName,
        UUID wineLotId,
        String wineLotCode,
        String denomination,
        String tanks,
        BigDecimal volumeHl,
        String reference,
        String summary,
        @JsonRawValue String data,
        UUID correctsEntryId,
        boolean late,
        String hash
) {
    public static RegisterEntryResponse from(RegisterEntry entry) {
        return new RegisterEntryResponse(entry.getId(), entry.getSequenceNo(), entry.getBook(),
                entry.getBook().label(), entry.getKind(), entry.getOperationAt(), entry.getRecordedAt(),
                entry.getRecordedByName(), entry.getWineLotId(), entry.getWineLotCode(), entry.getDenomination(),
                entry.getTanks(), entry.getVolumeHl(), entry.getReference(), entry.getSummary(), entry.getData(),
                entry.getCorrectsEntryId(), entry.isLate(), entry.getHash());
    }
}
