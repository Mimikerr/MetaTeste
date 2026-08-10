package com.example.metateste.host.session

import com.example.metateste.host.session.ConfirmationClassifier.Confirmation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationClassifierTest {

    @Test
    fun `classifies plain affirmative replies`() {
        assertEquals(Confirmation.AFFIRMATIVE, ConfirmationClassifier.classify("sim"))
        assertEquals(Confirmation.AFFIRMATIVE, ConfirmationClassifier.classify("Sim, pode rodar"))
        assertEquals(Confirmation.AFFIRMATIVE, ConfirmationClassifier.classify("claro, manda ver"))
    }

    @Test
    fun `classifies plain negative replies, with or without the accent`() {
        assertEquals(Confirmation.NEGATIVE, ConfirmationClassifier.classify("não"))
        assertEquals(Confirmation.NEGATIVE, ConfirmationClassifier.classify("nao, cancela isso"))
        assertEquals(Confirmation.NEGATIVE, ConfirmationClassifier.classify("pare, não quero"))
    }

    @Test
    fun `treats the preposition para as unrelated, not a stop command`() {
        assertEquals(Confirmation.AFFIRMATIVE, ConfirmationClassifier.classify("sim, pode rodar para mim"))
    }

    @Test
    fun `treats unrecognized replies as unclear`() {
        assertEquals(Confirmation.UNCLEAR, ConfirmationClassifier.classify("talvez mais tarde"))
        assertEquals(Confirmation.UNCLEAR, ConfirmationClassifier.classify(""))
    }

    @Test
    fun `never resolves a contradictory reply as affirmative`() {
        assertEquals(Confirmation.UNCLEAR, ConfirmationClassifier.classify("não sei, pode ser"))
    }
}
