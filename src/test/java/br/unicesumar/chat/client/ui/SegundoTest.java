package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** A tarefa roda fora da EDT; o resultado (ou a falha) volta SEMPRE na EDT. */
class SegundoTest {

    @Test
    void tarefaRodaForaDaEdtEOResultadoVoltaNaEdt() throws Exception {
        AtomicReference<Boolean> tarefaNaEdt = new AtomicReference<>();
        AtomicReference<Boolean> retornoNaEdt = new AtomicReference<>();
        AtomicReference<String> resultado = new AtomicReference<>();
        CountDownLatch pronto = new CountDownLatch(1);
        Segundo.plano(() -> {
            tarefaNaEdt.set(SwingUtilities.isEventDispatchThread());
            return "feito";
        }, valor -> {
            retornoNaEdt.set(SwingUtilities.isEventDispatchThread());
            resultado.set(valor);
            pronto.countDown();
        }, falha -> pronto.countDown());
        assertTrue(pronto.await(5, TimeUnit.SECONDS));
        assertEquals(false, tarefaNaEdt.get());
        assertEquals(true, retornoNaEdt.get());
        assertEquals("feito", resultado.get());
    }

    @Test
    void falhaDaTarefaChegaNaEdtComACausaOriginal() throws Exception {
        AtomicReference<Throwable> falha = new AtomicReference<>();
        AtomicReference<Boolean> naEdt = new AtomicReference<>();
        CountDownLatch pronto = new CountDownLatch(1);
        Segundo.plano(() -> {
            throw new IllegalStateException("deu ruim");
        }, valor -> pronto.countDown(), erro -> {
            falha.set(erro);
            naEdt.set(SwingUtilities.isEventDispatchThread());
            pronto.countDown();
        });
        assertTrue(pronto.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, falha.get());
        assertEquals("deu ruim", falha.get().getMessage());
        assertEquals(true, naEdt.get());
    }

    @Test
    void argumentosNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> Segundo.plano(null, v -> { }, e -> { }));
        assertThrows(NullPointerException.class, () -> Segundo.plano(() -> 1, null, e -> { }));
        assertThrows(NullPointerException.class, () -> Segundo.plano(() -> 1, v -> { }, null));
    }

    @Test
    void tarefaSemResultadoUsaOAtalhoDeRunnable() throws Exception {
        CountDownLatch pronto = new CountDownLatch(1);
        AtomicReference<Boolean> rodou = new AtomicReference<>(false);
        Segundo.plano(() -> rodou.set(true), pronto::countDown, falha -> pronto.countDown());
        assertTrue(pronto.await(5, TimeUnit.SECONDS));
        assertTrue(rodou.get());
    }
}
