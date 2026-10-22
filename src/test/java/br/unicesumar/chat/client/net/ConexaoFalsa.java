package br.unicesumar.chat.client.net;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.ProtocoloException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Adaptador falso de {@link ConexaoChat}: o "servidor" é um roteiro de eventos
 * montado pelo teste, e tudo que o cliente envia fica registrado. Sem rede.
 */
public class ConexaoFalsa implements ConexaoChat {

    private static final Object FIM = new Object();

    private final BlockingQueue<Object> recebidos = new LinkedBlockingQueue<>();
    private final List<String> enviadas = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean aberta;
    private IOException falhaAoAbrir;
    private IOException falhaAoEnviar;

    /** Faz {@link #abrir()} falhar com a exceção informada. */
    public ConexaoFalsa falharAoAbrirCom(IOException falha) {
        this.falhaAoAbrir = falha;
        return this;
    }

    /** Faz {@link #enviar(String)} falhar com a exceção informada. */
    public ConexaoFalsa falharAoEnviarCom(IOException falha) {
        this.falhaAoEnviar = falha;
        return this;
    }

    /** Roteiro: o servidor "envia" este evento. */
    public ConexaoFalsa receberEvento(Evento evento) {
        recebidos.add(evento);
        return this;
    }

    /** Roteiro: o servidor "envia" uma linha fora do protocolo. */
    public ConexaoFalsa receberLinhaInvalida(String problema) {
        recebidos.add(new ProtocoloException(problema));
        return this;
    }

    /** Roteiro: o servidor encerra a conexão. */
    public ConexaoFalsa encerrarPeloServidor() {
        recebidos.add(FIM);
        return this;
    }

    /** Linhas que o cliente enviou, na ordem. */
    public List<String> enviadas() {
        synchronized (enviadas) {
            return new ArrayList<>(enviadas);
        }
    }

    @Override
    public void abrir() throws IOException {
        if (falhaAoAbrir != null) {
            throw falhaAoAbrir;
        }
        aberta = true;
    }

    @Override
    public void enviar(String texto) throws IOException {
        if (!aberta) {
            throw new IOException("A conexão está fechada. Conecte-se novamente.");
        }
        if (falhaAoEnviar != null) {
            throw falhaAoEnviar;
        }
        enviadas.add(texto);
    }

    @Override
    public Evento receber() throws IOException {
        try {
            Object proximo = recebidos.take();
            if (proximo == FIM) {
                recebidos.add(FIM); // quem perguntar de novo também recebe o fim
                return null;
            }
            if (proximo instanceof ProtocoloException e) {
                throw e;
            }
            return (Evento) proximo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Leitura interrompida.", e);
        }
    }

    @Override
    public void fechar() {
        aberta = false;
        recebidos.add(FIM);
    }

    @Override
    public boolean aberta() {
        return aberta;
    }
}
