package com.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargeAcademicTermTest {

    @Test
    void fromCodeMapsPersistedCodes() {
        assertAll(
            () -> assertEquals(ChargeAcademicTerm.FIRST_SEM,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_1ST_SEM)),
            () -> assertEquals(ChargeAcademicTerm.SECOND_SEM,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_2ND_SEM)),
            () -> assertEquals(ChargeAcademicTerm.SUMMER,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_SUMMER)),
            () -> assertEquals(ChargeAcademicTerm.CURRENT,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_CURRENT)),
            () -> assertEquals(ChargeAcademicTerm.PREVIOUS,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_PREVIOUS)),
            () -> assertEquals(ChargeAcademicTerm.UNASSIGNED,
                ChargeAcademicTerm.fromCode(ChargeAcademicTerm.DB_UNASSIGNED))
        );
    }

    @Test
    void fromCodeAcceptsCaseInsensitiveTrimmedAliases() {
        assertAll(
            () -> assertEquals(ChargeAcademicTerm.FIRST_SEM,
                ChargeAcademicTerm.fromCode("  first semester  ")),
            () -> assertEquals(ChargeAcademicTerm.SECOND_SEM,
                ChargeAcademicTerm.fromCode("second_sem")),
            () -> assertEquals(ChargeAcademicTerm.SUMMER,
                ChargeAcademicTerm.fromCode("Midyear")),
            () -> assertEquals(ChargeAcademicTerm.CURRENT,
                ChargeAcademicTerm.fromCode("current term")),
            () -> assertEquals(ChargeAcademicTerm.PREVIOUS,
                ChargeAcademicTerm.fromCode("prev term"))
        );
    }

    @Test
    void fromCodeFallsBackToUnassignedForMissingOrUnknownValues() {
        assertAll(
            () -> assertEquals(ChargeAcademicTerm.UNASSIGNED,
                ChargeAcademicTerm.fromCode(null)),
            () -> assertEquals(ChargeAcademicTerm.UNASSIGNED,
                ChargeAcademicTerm.fromCode("")),
            () -> assertEquals(ChargeAcademicTerm.UNASSIGNED,
                ChargeAcademicTerm.fromCode("   ")),
            () -> assertEquals(ChargeAcademicTerm.UNASSIGNED,
                ChargeAcademicTerm.fromCode("third semester"))
        );
    }
}
