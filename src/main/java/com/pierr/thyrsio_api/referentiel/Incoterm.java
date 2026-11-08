package com.pierr.thyrsio_api.referentiel;

/**
 * Incoterms 2020 (Chambre de commerce internationale).
 * Un enum : la liste est fixée par une norme, et chaque incoterm déterminera plus tard
 * qui organise et paie le transport, et quels documents préparer.
 */
public enum Incoterm {
    EXW, FCA, CPT, CIP, DAP, DPU, DDP, // tous modes de transport
    FAS, FOB, CFR, CIF                 // maritime et fluvial
}