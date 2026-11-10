package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Evento;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heartbeat do servidor: de tempos em tempos manda um {@code PING} a todos os clientes. Quem está vivo
 * responde {@code /pong}, o que mantém a leitura do socket ativa; quem sumiu sem fechar a conexão
 * (cliente "zumbi") estoura o tempo-limite de leitura e é removido pelo {@link ClienteHandler}.
 */
final class MonitorDeConexoes {

    private static final Logger LOG = Logger.getLogger(MonitorDeConexoes.class.getName());

    private final ClientRegistry registry;
    private final Duration intervalo;
    private ScheduledExecutorService agendador;

    MonitorDeConexoes(ClientRegistry registry, Duration intervalo) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.intervalo = Objects.requireNonNull(intervalo, "intervalo");
        if (intervalo.isZero() || intervalo.isNegative()) {
            throw new IllegalArgumentException("O intervalo do PING deve ser positivo.");
        }
    }

    /** Começa a mandar PING em uma thread própria (daemon). Não faz nada se já começou. */
    synchronized void iniciar() {
        if (agendador != null) {
            return;
        }
        agendador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor-de-conexoes");
            t.setDaemon(true);
            return t;
        });
        agendador.scheduleAtFixedRate(this::pingar, intervalo.toMillis(), intervalo.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Para de mandar PING. Pode ser chamado várias vezes. */
    synchronized void parar() {
        if (agendador != null) {
            agendador.shutdownNow();
            agendador = null;
        }
    }

    private void pingar() {
        try {
            registry.transmitir(new Evento.Ping(), c -> true);
        } catch (RuntimeException e) { // uma falha não pode matar o agendador
            LOG.log(Level.WARNING, "Falha ao enviar PING: " + e.getMessage(), e);
        }
    }
}
