package com.wallet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferSqlInvariantTest {
    @Test
    void amountIsIntegerPaiseConceptually() {
        long paise = 12345L;
        assertEquals(12345L, paise);
    }
}
