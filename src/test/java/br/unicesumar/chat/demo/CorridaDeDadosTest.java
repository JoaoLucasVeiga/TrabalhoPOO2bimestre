package br.unicesumar.chat.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Versões CORRIGIDAS do contador da demonstração: o total tem de ser sempre exato. */
class CorridaDeDadosTest {

    @Test
    void contadorComSynchronizedNuncaPerdeIncrementos() throws InterruptedException {
        assertEquals(2_000_000L, CorridaDeDados.somarSincronizado(2, 1_000_000));
    }

    @Test
    void contadorAtomicoNuncaPerdeIncrementos() throws InterruptedException {
        assertEquals(2_000_000L, CorridaDeDados.somarAtomico(2, 1_000_000));
    }

    @Test
    void contadorSemSincronizacaoNuncaPassaDoTotalEsperado() throws InterruptedException {
        long total = CorridaDeDados.somarSemSincronizar(2, 1_000_000);
        assertTrue(total <= 2_000_000L, "total impossível: " + total);
        assertTrue(total > 0);
    }

    @Test
    void contadoresFuncionamComUmaSoThread() throws InterruptedException {
        assertEquals(1000L, CorridaDeDados.somarSemSincronizar(1, 1000));
        assertEquals(1000L, CorridaDeDados.somarSincronizado(1, 1000));
        assertEquals(1000L, CorridaDeDados.somarAtomico(1, 1000));
    }

    @Test
    void argumentosInvalidosLancamExcecao() {
        assertThrows(IllegalArgumentException.class, () -> CorridaDeDados.somarSincronizado(0, 10));
        assertThrows(IllegalArgumentException.class, () -> CorridaDeDados.somarAtomico(2, -1));
        AtomicLong n = new AtomicLong();
        assertEquals(0, n.get());
    }
}
