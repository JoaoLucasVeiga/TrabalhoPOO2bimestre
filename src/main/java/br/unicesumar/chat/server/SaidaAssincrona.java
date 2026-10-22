package br.unicesumar.chat.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fila de escrita de um cliente. Quem chama {@link #write} só enfileira os
 * bytes e retorna; uma thread própria grava no destino (o socket). Assim um
 * cliente que parou de ler, com o buffer de rede cheio, não bloqueia quem está
 * transmitindo, e portanto não trava o servidor nem os outros clientes. Se o
 * volume de bytes pendentes passa do limite, o cliente é considerado lento
 * demais: a escrita falha com {@link IOException} e o chamador o desconecta.
 */
public final class SaidaAssincrona extends OutputStream {

    /** Limite padrão de bytes pendentes por cliente (256 KB). */
    public static final int LIMITE_PADRAO = 256 * 1024;
    /** Tempo máximo, em ms, que {@link #close()} espera a fila esvaziar. */
    public static final long PRAZO_PADRAO_MS = 500;

    private static final Logger LOG = Logger.getLogger(SaidaAssincrona.class.getName());
    private static final AtomicInteger NUMERO = new AtomicInteger();
    private static final byte[] FIM = new byte[0];

    private final OutputStream destino;
    private final int limite;
    private final long prazoMs;
    private final BlockingQueue<byte[]> fila = new LinkedBlockingQueue<>();
    private final AtomicLong pendentes = new AtomicLong();
    private final AtomicBoolean fechada = new AtomicBoolean(false);
    private final Thread escritor;
    private volatile boolean falhou;

    /** Cria a fila com o prazo de fechamento padrão. */
    public SaidaAssincrona(OutputStream destino, int limiteBytes) {
        this(destino, limiteBytes, PRAZO_PADRAO_MS);
    }

    /**
     * Cria a fila e inicia a thread de escrita.
     *
     * @param destino     onde os bytes são gravados de fato
     * @param limiteBytes máximo de bytes pendentes antes de considerar o cliente lento
     * @param prazoMs     quanto {@link #close()} espera a fila esvaziar
     */
    public SaidaAssincrona(OutputStream destino, int limiteBytes, long prazoMs) {
        if (limiteBytes <= 0) {
            throw new IllegalArgumentException("O limite de bytes deve ser maior que zero.");
        }
        this.destino = destino;
        this.limite = limiteBytes;
        this.prazoMs = prazoMs;
        this.escritor = new Thread(this::gravar, "escritor-cliente-" + NUMERO.incrementAndGet());
        this.escritor.setDaemon(true);
        this.escritor.start();
    }

    private void gravar() {
        try {
            while (!falhou) {
                byte[] bloco = fila.take();
                if (bloco == FIM) {
                    return;
                }
                try {
                    destino.write(bloco);
                    destino.flush();
                } finally {
                    pendentes.addAndGet(-bloco.length);
                }
            }
        } catch (IOException e) {
            falhou = true;
            LOG.log(Level.INFO, "Escrita no cliente falhou: {0}", e.getMessage());
        } catch (InterruptedException e) {
            falhou = true;
            Thread.currentThread().interrupt();
        }
    }

    /** Copia os bytes (quem escreve reaproveita o buffer) e os enfileira, sem bloquear. */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (fechada.get() || falhou) {
            throw new IOException("A conexão com o cliente já foi encerrada.");
        }
        if (len == 0) {
            return;
        }
        if (pendentes.addAndGet(len) > limite) {
            pendentes.addAndGet(-len);
            falhou = true;
            LOG.log(Level.WARNING, "Cliente lento: mais de {0} bytes pendentes.", limite);
            throw new IOException("Cliente lento demais: a conexão será encerrada "
                    + "porque ele não está recebendo as mensagens.");
        }
        fila.add(Arrays.copyOfRange(b, off, off + len));
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    /** Não faz nada: a gravação real acontece na thread de escrita. */
    @Override
    public void flush() {
    }

    /** Indica se a saída falhou (destino com erro ou cliente lento demais). */
    public boolean falhou() {
        return falhou;
    }

    /**
     * Encerra a saída. Se ela não falhou, espera até o prazo para a fila esvaziar
     * (assim uma despedida já enfileirada chega ao cliente) e depois fecha o
     * destino, o que também destrava uma escrita presa. Pode ser chamado várias vezes.
     */
    @Override
    public void close() throws IOException {
        if (!fechada.compareAndSet(false, true)) {
            return;
        }
        fila.add(FIM);
        if (!falhou) {
            try {
                escritor.join(prazoMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        destino.close();
    }
}
