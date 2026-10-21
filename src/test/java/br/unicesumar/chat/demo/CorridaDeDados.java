package br.unicesumar.chat.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstração (fora do núcleo do chat) de uma condição de corrida. Duas threads
 * somam 1.000.000 vezes cada em um contador compartilhado. Sem sincronização o
 * total quase nunca chega a 2.000.000; com {@code synchronized} ou {@code AtomicLong}
 * é sempre exato. Rode {@link #main} para tirar os prints antes e depois.
 */
public final class CorridaDeDados {

    private static long contadorSemTrava;
    private static long contadorComTrava;
    private static final Object TRAVA = new Object();
    private static final AtomicLong CONTADOR_ATOMICO = new AtomicLong();

    private CorridaDeDados() {
    }

    /** Versão com BUG: {@code contador++} não é atômico (lê, soma e grava em 3 passos). */
    public static long somarSemSincronizar(int threads, int vezes) throws InterruptedException {
        validar(threads, vezes);
        contadorSemTrava = 0;
        executar(threads, () -> {
            for (int i = 0; i < vezes; i++) {
                contadorSemTrava++;
            }
        });
        return contadorSemTrava;
    }

    /** Versão corrigida: só uma thread por vez entra no bloco {@code synchronized}. */
    public static long somarSincronizado(int threads, int vezes) throws InterruptedException {
        validar(threads, vezes);
        contadorComTrava = 0;
        executar(threads, () -> {
            for (int i = 0; i < vezes; i++) {
                synchronized (TRAVA) {
                    contadorComTrava++;
                }
            }
        });
        synchronized (TRAVA) {
            return contadorComTrava;
        }
    }

    /** Versão corrigida sem lock: {@link AtomicLong} soma de forma atômica. */
    public static long somarAtomico(int threads, int vezes) throws InterruptedException {
        validar(threads, vezes);
        CONTADOR_ATOMICO.set(0);
        executar(threads, () -> {
            for (int i = 0; i < vezes; i++) {
                CONTADOR_ATOMICO.incrementAndGet();
            }
        });
        return CONTADOR_ATOMICO.get();
    }

    private static void validar(int threads, int vezes) {
        if (threads < 1 || vezes < 0) {
            throw new IllegalArgumentException("Use pelo menos 1 thread e um número de vezes não negativo.");
        }
    }

    private static void executar(int quantidade, Runnable tarefa) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            Thread t = new Thread(tarefa, "soma-" + i);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    /** Imprime o resultado das três versões (2 threads x 1.000.000). */
    public static void main(String[] args) throws InterruptedException {
        int vezes = 1_000_000;
        System.out.println("Esperado: " + (2L * vezes));
        for (int rodada = 1; rodada <= 3; rodada++) {
            System.out.println("Rodada " + rodada + " - SEM sincronização: " + somarSemSincronizar(2, vezes));
        }
        System.out.println("COM synchronized:       " + somarSincronizado(2, vezes));
        System.out.println("COM AtomicLong:         " + somarAtomico(2, vezes));
    }
}
