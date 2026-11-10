package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.protocol.Evento;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Produtor-consumidor das mensagens públicas. As threads dos clientes (produtoras) só colocam a
 * mensagem na fila e esperam o resultado; uma única thread consumidora pega tudo o que está na fila,
 * grava num lote só (uma transação) e transmite na ordem de gravação. Como só uma thread grava e
 * transmite, todos os clientes veem a mesma ordem; e como as mensagens que chegam enquanto o banco
 * trabalha esperam juntas, sob carga o custo de gravar é dividido entre todas elas.
 *
 * <p>A espera é síncrona: {@link #publicar} só volta depois de a mensagem estar gravada e transmitida,
 * ou lança o erro do banco. A thread consumidora é criada sob demanda e some quando fica ociosa.
 */
final class SequenciadorDeMensagens {

    private static final Logger LOG = Logger.getLogger(SequenciadorDeMensagens.class.getName());
    private static final int TAMANHO_MAXIMO_DO_LOTE = 100;
    private static final long ESPERA_MAXIMA_EM_SEGUNDOS = 60;

    private record Pedido(Mensagem mensagem, CompletableFuture<Mensagem> resultado) {
    }

    private final MensagemRepository repositorio;
    private final ClientRegistry registry;
    /** Mesmo lock de {@code entrar}/{@code sair}/{@code mudarDeSala}: ninguém recebe mensagem "no meio". */
    private final Object ordem;
    private final ConcurrentLinkedQueue<Pedido> fila = new ConcurrentLinkedQueue<>();
    private final AtomicInteger naFila = new AtomicInteger();
    private final ThreadPoolExecutor consumidor;

    SequenciadorDeMensagens(MensagemRepository repositorio, ClientRegistry registry, Object ordem) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ordem = Objects.requireNonNull(ordem, "ordem");
        this.consumidor = new ThreadPoolExecutor(1, 1, 2, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, "sequenciador-de-mensagens");
            t.setDaemon(true);
            return t;
        });
        this.consumidor.allowCoreThreadTimeOut(true);
    }

    /**
     * Enfileira a mensagem e espera ela ser gravada e transmitida.
     *
     * @return a mensagem salva, com id
     * @throws br.unicesumar.chat.persistence.RepositorioException se o banco falhar para esta mensagem
     */
    Mensagem publicar(Mensagem mensagem) {
        Pedido pedido = new Pedido(Objects.requireNonNull(mensagem, "mensagem"), new CompletableFuture<>());
        naFila.incrementAndGet();
        fila.add(pedido);
        consumidor.execute(this::processarUmLote);
        try {
            return pedido.resultado().get(ESPERA_MAXIMA_EM_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Você foi interrompido enquanto a mensagem era gravada.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException erro) {
                throw erro;
            }
            throw new IllegalStateException("Falha inesperada ao gravar a mensagem.", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("O servidor demorou demais para gravar a mensagem. "
                    + "Tente enviar de novo em instantes.", e);
        }
    }

    /** Quantas mensagens estão esperando para entrar em um lote (as que já estão sendo gravadas não contam). */
    int naFila() {
        return naFila.get();
    }

    /** Pega o que está na fila (até o limite) e grava e transmite como um lote. Roda só na thread consumidora. */
    private void processarUmLote() {
        List<Pedido> lote = new ArrayList<>();
        Pedido pedido;
        while (lote.size() < TAMANHO_MAXIMO_DO_LOTE && (pedido = fila.poll()) != null) {
            lote.add(pedido);
            naFila.decrementAndGet();
        }
        if (lote.isEmpty()) {
            return; // um lote anterior já levou as mensagens deste pedido
        }
        try {
            synchronized (ordem) {
                gravarETransmitir(lote);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado no sequenciador: " + e.getMessage(), e);
        } finally {
            for (Pedido p : lote) {
                p.resultado().completeExceptionally(
                        new IllegalStateException("A mensagem não pôde ser processada. Tente de novo."));
            }
        }
    }

    private void gravarETransmitir(List<Pedido> lote) {
        List<Mensagem> salvas = null;
        try {
            salvas = repositorio.salvarTodas(lote.stream().map(Pedido::mensagem).toList());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao gravar o lote de {0} mensagens; tentando uma a uma: {1}",
                    new Object[] {lote.size(), e.getMessage()});
        }
        if (salvas != null) {
            for (int i = 0; i < lote.size(); i++) {
                transmitir(lote.get(i), salvas.get(i));
            }
            return;
        }
        for (Pedido pedido : lote) { // o lote voltou atrás inteiro: cada mensagem tenta sozinha
            try {
                transmitir(pedido, repositorio.salvar(pedido.mensagem()));
            } catch (RuntimeException e) {
                pedido.resultado().completeExceptionally(e);
            }
        }
    }

    private void transmitir(Pedido pedido, Mensagem salva) {
        registry.transmitir(Evento.Msg.de(salva), c -> c.usuario().isPresent() && c.sala().equals(salva.sala()));
        pedido.resultado().complete(salva);
    }
}
