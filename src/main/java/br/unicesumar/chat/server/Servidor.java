package br.unicesumar.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Escuta conexões TCP e entrega cada uma a um {@link ClienteHandler}, que roda
 * em sua própria thread. Não executa SQL nem regras do chat.
 */
public class Servidor {

    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());

    private final int porta;
    private final ProcessadorEntrada processador;
    private final AtomicInteger contador = new AtomicInteger();
    private final ExecutorService clientes = Executors.newCachedThreadPool(daemon("cliente-"));
    private volatile ServerSocket socketDeEscuta;
    private volatile boolean parado;
    private Thread threadDeAccept;

    /**
     * Cria o servidor (ainda sem escutar).
     *
     * @param porta       de 0 a 65535; 0 escolhe uma porta livre
     * @param processador quem interpreta as linhas dos clientes
     */
    public Servidor(int porta, ProcessadorEntrada processador) {
        if (porta < 0 || porta > 65535) {
            throw new IllegalArgumentException("Porta inválida: " + porta + ". Use de 0 a 65535.");
        }
        this.porta = porta;
        this.processador = Objects.requireNonNull(processador, "processador");
    }

    private static ThreadFactory daemon(String prefixo) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefixo + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Abre a porta e começa a aceitar conexões em uma thread própria. Retorna
     * assim que a porta está aberta.
     *
     * @throws IOException           se a porta estiver ocupada ou indisponível
     * @throws IllegalStateException se já foi iniciado
     */
    public synchronized void iniciar() throws IOException {
        if (socketDeEscuta != null) {
            throw new IllegalStateException("O servidor já foi iniciado.");
        }
        try {
            socketDeEscuta = new ServerSocket(porta);
        } catch (IOException e) {
            throw new IOException("Não foi possível abrir a porta " + porta
                    + ". Verifique se ela já está em uso e tente outra porta.", e);
        }
        threadDeAccept = new Thread(this::aceitarConexoes, "servidor-accept");
        threadDeAccept.setDaemon(true);
        threadDeAccept.start();
        LOG.log(Level.INFO, "Servidor escutando na porta {0}", porta());
    }

    private void aceitarConexoes() {
        ServerSocket escuta = socketDeEscuta;
        while (!parado) {
            try {
                Socket socket = escuta.accept();
                entregar(socket);
            } catch (SocketException e) {
                if (!parado) {
                    LOG.log(Level.SEVERE, "Socket de escuta falhou: " + e.getMessage(), e);
                }
                return;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Falha ao aceitar conexão: " + e.getMessage(), e);
            }
        }
    }

    private void entregar(Socket socket) {
        try {
            ClienteHandler handler = ClienteHandler.de(socket, processador);
            clientes.execute(handler);
            LOG.log(Level.INFO, "Conexão {0} aceita", contador.incrementAndGet());
        } catch (IOException | RejectedExecutionException e) {
            LOG.log(Level.WARNING, "Conexão descartada: " + e.getMessage(), e);
            try {
                socket.close();
            } catch (IOException ignorada) {
                LOG.log(Level.FINE, "Falha ao fechar socket descartado", ignorada);
            }
        }
    }

    /** Porta em que o servidor escuta (útil quando foi criado com porta 0). */
    public int porta() {
        ServerSocket escuta = socketDeEscuta;
        return escuta == null ? porta : escuta.getLocalPort();
    }

    /** Indica se o servidor está aceitando conexões. */
    public boolean ativo() {
        ServerSocket escuta = socketDeEscuta;
        return escuta != null && !parado && !escuta.isClosed();
    }

    /** Para de aceitar conexões. Pode ser chamado várias vezes. */
    public void parar() {
        parado = true;
        ServerSocket escuta = socketDeEscuta;
        if (escuta != null) {
            try {
                escuta.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Falha ao fechar o socket de escuta: " + e.getMessage(), e);
            }
        }
        clientes.shutdown();
    }
}
