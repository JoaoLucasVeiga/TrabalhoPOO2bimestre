package br.unicesumar.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import javax.net.ServerSocketFactory;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
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
 * em sua própria thread. Registra cada cliente no {@link ClientRegistry}. Não executa SQL nem regras do chat.
 */
public class Servidor {

    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());

    /** Intervalo padrão entre os PING do servidor. */
    public static final Duration INTERVALO_DO_PING = Duration.ofSeconds(15);

    /** Tempo padrão sem receber nada de um cliente antes de desconectá-lo (três PING sem resposta). */
    public static final Duration TEMPO_LIMITE_SEM_RESPOSTA = Duration.ofSeconds(45);

    private final int porta;
    private final ClientRegistry registry;
    private final ProcessadorEntrada processador;
    private final AtomicInteger contador = new AtomicInteger();
    private final ExecutorService clientes = Executors.newCachedThreadPool(daemon("cliente-"));
    private volatile ServerSocket socketDeEscuta;
    private final Duration tempoLimiteSemLer;
    private final MonitorDeConexoes monitor;
    private volatile boolean parado;
    private volatile ServerSocketFactory fabricaDeSockets = ServerSocketFactory.getDefault();
    private volatile Thread threadDeAccept;

    /** Cria o servidor com um registro de clientes próprio. Veja o outro construtor. */
    public Servidor(int porta, ProcessadorEntrada processador) {
        this(porta, new ClientRegistry(), processador);
    }

    /**
     * Cria o servidor (ainda sem escutar).
     *
     * @param porta       de 0 a 65535; 0 escolhe uma porta livre
     * @param registry    onde ficam os clientes conectados
     * @param processador quem interpreta as linhas dos clientes
     */
    public Servidor(int porta, ClientRegistry registry, ProcessadorEntrada processador) {
        this(porta, registry, processador, INTERVALO_DO_PING, TEMPO_LIMITE_SEM_RESPOSTA);
    }

    /**
     * Cria o servidor com heartbeat configurável.
     *
     * @param intervaloDoPing    de quanto em quanto tempo o servidor pergunta se o cliente está vivo
     * @param tempoLimiteSemLer  quanto tempo sem receber nada (nem a resposta ao PING) até desconectar;
     *                           precisa ser maior que o intervalo do PING
     */
    public Servidor(int porta, ClientRegistry registry, ProcessadorEntrada processador,
            Duration intervaloDoPing, Duration tempoLimiteSemLer) {
        if (porta < 0 || porta > 65535) {
            throw new IllegalArgumentException("Porta inválida: " + porta + ". Use de 0 a 65535.");
        }
        Objects.requireNonNull(intervaloDoPing, "intervaloDoPing");
        Objects.requireNonNull(tempoLimiteSemLer, "tempoLimiteSemLer");
        if (tempoLimiteSemLer.compareTo(intervaloDoPing) <= 0) {
            throw new IllegalArgumentException("O tempo-limite sem resposta (" + tempoLimiteSemLer.toMillis()
                    + " ms) precisa ser maior que o intervalo do PING (" + intervaloDoPing.toMillis() + " ms).");
        }
        this.tempoLimiteSemLer = tempoLimiteSemLer;
        this.monitor = new MonitorDeConexoes(registry, intervaloDoPing);
        this.porta = porta;
        this.registry = Objects.requireNonNull(registry, "registry");
        this.processador = removendoDoRegistro(Objects.requireNonNull(processador, "processador"));
    }

    /** Decora o processador: o cliente sai do registro antes de o processador saber da saída. */
    private ProcessadorEntrada removendoDoRegistro(ProcessadorEntrada real) {
        return new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
                real.aoConectar(origem);
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
                real.aoReceber(origem, linha);
            }

            @Override
            public void aoDesconectar(ClienteHandler origem) {
                registry.remover(origem);
                real.aoDesconectar(origem);
            }
        };
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
     * Faz o servidor aceitar conexões com TLS (passe a fábrica de um {@code SSLContext}). Deve ser chamado
     * antes de {@link #iniciar()}; sem isso o servidor usa TCP puro.
     *
     * @return este servidor
     */
    public Servidor comTls(ServerSocketFactory fabrica) {
        this.fabricaDeSockets = java.util.Objects.requireNonNull(fabrica, "fabrica");
        return this;
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
            socketDeEscuta = fabricaDeSockets.createServerSocket(porta);
        } catch (IOException e) {
            throw new IOException("Não foi possível abrir a porta " + porta
                    + ". Verifique se ela já está em uso e tente outra porta.", e);
        }
        monitor.iniciar();
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
                if (parado) {
                    // chegou na janela entre o parar() e o fechamento real do socket de escuta
                    socket.close();
                    return;
                }
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
            socket.setKeepAlive(true);
            socket.setSoTimeout((int) tempoLimiteSemLer.toMillis()); // cliente "zumbi" estoura e é removido
            ClienteHandler handler = ClienteHandler.de(socket, processador);
            registry.adicionar(handler);
            try {
                clientes.execute(handler);
            } catch (RejectedExecutionException e) {
                registry.remover(handler);
                throw e;
            }
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

    /** Registro dos clientes conectados. */
    public ClientRegistry registry() {
        return registry;
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
        monitor.parar();
        ServerSocket escuta = socketDeEscuta;
        if (escuta != null) {
            try {
                escuta.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Falha ao fechar o socket de escuta: " + e.getMessage(), e);
            }
        }
        aguardarFimDoAccept();
        registry.encerrarTodos();
        clientes.shutdown();
    }

    /**
     * Espera a thread de accept terminar: só então o socket de escuta deixou de
     * existir no sistema operacional e novas conexões passam a ser recusadas.
     */
    private void aguardarFimDoAccept() {
        Thread accept = threadDeAccept;
        if (accept == null || accept == Thread.currentThread()) {
            return;
        }
        try {
            accept.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
