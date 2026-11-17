package com.pierr.thyrsio_api.registre;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Une écriture du registre. Jamais modifiée : la base le refuse (trigger). */
@Entity
@Immutable
@Table(name = "register_entries")
public class RegisterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegisterBook book;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private Instant operationAt;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private UUID recordedBy;

    private String recordedByName;
    private UUID wineLotId;
    private String wineLotCode;
    private String denomination;
    private String tanks;
    private BigDecimal volumeHl;
    private String reference;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String data;

    private UUID correctsEntryId;

    @Column(nullable = false)
    private boolean late;

    @Column(nullable = false)
    private String previousHash;

    @Column(nullable = false)
    private String hash;

    protected RegisterEntry() {
    }

    RegisterEntry(long sequenceNo, RegisterEntryDraft draft, Instant operationAt, Instant recordedAt,
                  String recordedByName, String data, boolean late, String previousHash, String hash) {
        this.sequenceNo = sequenceNo;
        this.book = draft.book();
        this.kind = draft.kind();
        this.operationAt = operationAt;
        this.recordedAt = recordedAt;
        this.recordedBy = draft.recordedBy();
        this.recordedByName = recordedByName;
        this.wineLotId = draft.wineLotId();
        this.wineLotCode = draft.wineLotCode();
        this.denomination = draft.denomination();
        this.tanks = draft.tanks();
        this.volumeHl = draft.volumeHl();
        this.reference = draft.reference();
        this.summary = draft.summary();
        this.data = data;
        this.correctsEntryId = draft.correctsEntryId();
        this.late = late;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    public UUID getId() { return id; }
    public long getSequenceNo() { return sequenceNo; }
    public RegisterBook getBook() { return book; }
    public String getKind() { return kind; }
    public Instant getOperationAt() { return operationAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public String getRecordedByName() { return recordedByName; }
    public UUID getWineLotId() { return wineLotId; }
    public String getWineLotCode() { return wineLotCode; }
    public String getDenomination() { return denomination; }
    public String getTanks() { return tanks; }
    public BigDecimal getVolumeHl() { return volumeHl; }
    public String getReference() { return reference; }
    public String getSummary() { return summary; }
    public String getData() { return data; }
    public UUID getCorrectsEntryId() { return correctsEntryId; }
    public boolean isLate() { return late; }
    public String getPreviousHash() { return previousHash; }
    public String getHash() { return hash; }
}
