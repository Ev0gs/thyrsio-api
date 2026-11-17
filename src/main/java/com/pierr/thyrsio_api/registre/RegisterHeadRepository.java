package com.pierr.thyrsio_api.registre;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RegisterHeadRepository extends JpaRepository<RegisterHead, Short> {

    /** SELECT ... FOR UPDATE : deux écritures simultanées passent l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from RegisterHead h where h.id = 1")
    RegisterHead lockHead();
}
