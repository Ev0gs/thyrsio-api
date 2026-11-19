package com.pierr.thyrsio_api.gs1;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GtinsTest {

    @Test
    void computes_the_gs1_check_digit() {
        // L'exemple de GS1 : 400638133393 → 1
        assertThat(Gtins.checkDigit("400638133393")).isEqualTo(1);
        assertThat(Gtins.withCheckDigit("376012345678")).isEqualTo("3760123456784");
        assertThat(Gtins.checkDigit("000000000000")).isZero();
    }

    @Test
    void checks_a_gtin() {
        assertThat(Gtins.isValid("4006381333931")).isTrue();
        assertThat(Gtins.isValid("4006381333932")).isFalse();   // mauvaise clé
        assertThat(Gtins.isValid("400638133393")).isFalse();    // 12 chiffres
        assertThat(Gtins.isValid(Gtins.normalize("4 006381 333931"))).isTrue();
    }

    @Test
    void a_prefix_leaves_room_for_the_rest_of_twelve_digits() {
        assertThat(Gtins.capacity("376012345")).isEqualTo(1000);
        assertThat(Gtins.capacity("3760123456")).isEqualTo(100);
    }
}
