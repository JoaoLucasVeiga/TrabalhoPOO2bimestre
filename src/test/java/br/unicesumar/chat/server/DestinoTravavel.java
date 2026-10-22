package br.unicesumar.chat.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Destino falso de escrita: fica preso até ser liberado, como um cliente que parou de ler. */
final class DestinoTravavel extends OutputStream {

    private final CountDownLatch liberacao = new CountDownLatch(1);
    private final ByteArrayOutputStream recebido = new ByteArrayOutputStream();
    final AtomicInteger fechamentos = new AtomicInteger();

    /** Libera o destino: as escritas presas continuam. */
    void liberar() {
        liberacao.countDown();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            liberacao.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", e);
        }
        synchronized (recebido) {
            recebido.write(b, off, len);
        }
    }

    @Override
    public void close() {
        fechamentos.incrementAndGet();
        liberacao.countDown(); // fechar uma conexão destrava quem estava escrevendo
    }

    String conteudo() {
        synchronized (recebido) {
            return recebido.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
