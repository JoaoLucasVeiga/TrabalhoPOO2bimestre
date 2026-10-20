package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Um cliente que para de ler não pode travar o servidor nem os outros (sem rede). */
class ClienteLentoTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
            Clock.systemDefaultZone());

    private ClienteHandler handlerTravado(DestinoTravavel destino, AtomicInteger fechamentos,
            int limite) {
        ClienteHandler h = ClienteHandler.assincrono(new ByteArrayInputStream(new byte[0]), destino,
                fechamentos::incrementAndGet, (origem, linha) -> { }, limite);
        registry.adicionar(h);
        registry.identificar(h, new Usuario("travado"));
        return h;
    }

    @Test
    void enviarParaClienteQueNaoLeNaoBloqueiaQuemEnvia() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        ClienteHandler lento = handlerTravado(destino, new AtomicInteger(), 10_000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> envio = pool.submit(() -> {
            for (int i = 0; i < 20; i++) {
                chat.enviarMensagem(new Usuario("ana"), "geral", "mensagem " + i);
            }
            return null;
        });
        envio.get(10, TimeUnit.SECONDS); // bloquearia para sempre sem a fila
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(lento.ativo());
        destino.liberar();
        lento.encerrar();
    }

    @Test
    void clienteQueEstouraOLimiteEDesconectadoEOsOutrosContinuamRecebendoTudo() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        AtomicInteger fechamentos = new AtomicInteger();
        ClienteHandler lento = handlerTravado(destino, fechamentos, 1000);
        ClienteFalso saudavel = ClienteFalso.identificado(registry, "saudavel");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        int total = 60;
        Future<?> envio = pool.submit(() -> {
            for (int i = 0; i < total; i++) {
                chat.enviarMensagem(new Usuario("ana"), "geral", "mensagem " + i);
            }
            return null;
        });
        envio.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertFalse(lento.ativo());
        assertEquals(1, fechamentos.get());
        assertEquals(java.util.List.of(saudavel.handler), registry.listar());
        assertEquals(total, saudavel.textos().size());
        destino.liberar();
    }

    @Test
    void clienteLentoNaoImpedeOutroDeEntrarNoChat() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        handlerTravado(destino, new AtomicInteger(), 1000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> entrada = pool.submit(() -> {
            ClienteFalso novo = ClienteFalso.novo();
            registry.adicionar(novo.handler);
            chat.enviarMensagem(new Usuario("ana"), "geral", "x".repeat(400));
            chat.enviarMensagem(new Usuario("ana"), "geral", "y".repeat(400));
            chat.enviarMensagem(new Usuario("ana"), "geral", "z".repeat(400));
            chat.entrar(novo.handler, new Usuario("novo"));
            return null;
        });
        entrada.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        destino.liberar();
    }
}
