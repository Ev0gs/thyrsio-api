package com.pierr.thyrsio_api.registre;

import jakarta.persistence.*;

/** La dernière écriture : son numéro et son empreinte. Une seule ligne, verrouillée à chaque écriture. */
@Entity
@Table(name = "register_head")
class RegisterHead {

    @Id
    private short id;

    @Column(nullable = false)
    private long lastSequence;

    @Column(nullable = false)
    private String lastHash;

    protected RegisterHead() {
    }

    long getLastSequence() { return lastSequence; }
    String getLastHash() { return lastHash; }

    void advance(long sequence, String hash) {
        this.lastSequence = sequence;
        this.lastHash = hash;
    }
}
