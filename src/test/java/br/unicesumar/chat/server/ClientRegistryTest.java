package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientRegistryTest {

    private final ClientRegistry registry = new ClientRegistry();

    @Test
    void registroNovoNaoTemClientes() {
        assertEquals(0, registry.quantidade());
        assertTrue(registry.listar().isEmpty());
    }

    @Test
    void adicionarAumentaAQuantidade() {
        registry.adicionar(ClienteFalso.novo().handler);
        registry.adicionar(ClienteFalso.novo().handler);
        assertEquals(2, registry.quantidade());
    }

    @Test
    void adicionarOMesmoHandlerDuasVezesContaUmaVez() {
        ClienteHandler h = ClienteFalso.novo().handler;
        registry.adicionar(h);
        registry.adicionar(h);
        assertEquals(1, registry.quantidade());
    }

    @Test
    void adicionarHandlerNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> registry.adicionar(null));
    }

    @Test
    void removerDiminuiADevolveTrue() {
        ClienteHandler h = ClienteFalso.novo().handler;
        registry.adicionar(h);
        assertTrue(registry.remover(h));
        assertEquals(0, registry.quantidade());
    }

    @Test
    void removerInexistenteDevolveFalse() {
        assertFalse(registry.remover(ClienteFalso.novo().handler));
    }

    @Test
    void listarDevolveCopiaQueNaoMudaQuandoORegistroMuda() {
        ClienteHandler a = ClienteFalso.novo().handler;
        registry.adicionar(a);
        List<ClienteHandler> copia = registry.listar();
        registry.adicionar(ClienteFalso.novo().handler);
        registry.remover(a);
        assertEquals(List.of(a), copia);
    }

    @Test
    void listaDevolvidaEImutavel() {
        registry.adicionar(ClienteFalso.novo().handler);
        assertThrows(UnsupportedOperationException.class,
                () -> registry.listar().add(ClienteFalso.novo().handler));
    }

    @Test
    void buscarPorUsuarioIgnoraMaiusculas() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "Ana");
        assertSame(ana.handler, registry.buscarPorUsuario("ANA").orElseThrow());
    }

    @Test
    void buscarPorUsuarioInexistenteOuNuloDevolveVazio() {
        ClienteFalso.identificado(registry, "ana");
        assertTrue(registry.buscarPorUsuario("bia").isEmpty());
        assertTrue(registry.buscarPorUsuario(null).isEmpty());
    }

    @Test
    void clienteNaoIdentificadoNaoApareceNaBuscaPorUsuario() {
        registry.adicionar(ClienteFalso.novo().handler);
        assertTrue(registry.buscarPorUsuario("ana").isEmpty());
    }

    @Test
    void identificarDefineOUsuarioDoHandler() {
        ClienteHandler h = ClienteFalso.novo().handler;
        registry.adicionar(h);
        assertTrue(h.usuario().isEmpty());
        assertTrue(registry.identificar(h, new Usuario("ana")));
        assertEquals(new Usuario("ana"), h.usuario().orElseThrow());
    }

    @Test
    void identificarComNomeJaUsadoPorOutroDevolveFalseENaoAltera() {
        ClienteFalso.identificado(registry, "ana");
        ClienteHandler outro = ClienteFalso.novo().handler;
        registry.adicionar(outro);
        assertFalse(registry.identificar(outro, new Usuario("ANA")));
        assertTrue(outro.usuario().isEmpty());
    }

    @Test
    void identificarDeNovoOMesmoHandlerComOMesmoNomeEPermitido() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        assertTrue(registry.identificar(ana.handler, new Usuario("ana")));
    }

    @Test
    void depoisDeRemoverONomeFicaLivre() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        registry.remover(ana.handler);
        ClienteHandler nova = ClienteFalso.novo().handler;
        registry.adicionar(nova);
        assertTrue(registry.identificar(nova, new Usuario("ana")));
    }

    @Test
    void identificarHandlerNaoRegistradoLancaExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.identificar(ClienteFalso.novo().handler, new Usuario("ana")));
    }

    @Test
    void transmitirEntregaATodosEDevolveQuantosReceberam() {
        ClienteFalso a = ClienteFalso.identificado(registry, "ana");
        ClienteFalso b = ClienteFalso.identificado(registry, "bia");
        int entregues = registry.transmitir(new Evento.Sistema("oi"));
        assertEquals(2, entregues);
        assertEquals(List.of(new Evento.Sistema("oi")), a.eventos());
        assertEquals(List.of(new Evento.Sistema("oi")), b.eventos());
    }

    @Test
    void transmitirComFiltroEntregaSoAosAprovados() {
        ClienteFalso a = ClienteFalso.identificado(registry, "ana");
        ClienteFalso b = ClienteFalso.identificado(registry, "bia");
        int entregues = registry.transmitir(new Evento.Sistema("so ana"),
                h -> h.usuario().orElseThrow().equals(new Usuario("ana")));
        assertEquals(1, entregues);
        assertEquals(1, a.eventos().size());
        assertTrue(b.eventos().isEmpty());
    }

    @Test
    void transmitirParaRegistroVazioNaoFalha() {
        assertEquals(0, registry.transmitir(new Evento.Sistema("ninguém")));
    }

    @Test
    void transmitirRemoveOClienteComFalhaEContinuaParaOsDemais() {
        ClienteFalso ruim = ClienteFalso.quebrado();
        registry.adicionar(ruim.handler);
        ClienteFalso bom = ClienteFalso.identificado(registry, "bia");
        int entregues = registry.transmitir(new Evento.Sistema("oi"));
        assertEquals(1, entregues);
        assertEquals(1, bom.eventos().size());
        assertEquals(List.of(bom.handler), registry.listar());
        assertEquals(1, ruim.fechamentos.get());
    }

    @Test
    void encerrarTodosFechaAsConexoesEEsvaziaORegistro() {
        ClienteFalso a = ClienteFalso.novo();
        ClienteFalso b = ClienteFalso.novo();
        registry.adicionar(a.handler);
        registry.adicionar(b.handler);
        registry.encerrarTodos();
        assertEquals(0, registry.quantidade());
        assertEquals(1, a.fechamentos.get());
        assertEquals(1, b.fechamentos.get());
    }

    // ---------- concorrência ----------

    @Test
    void adicionarERemoverEmVariasThreadsEnquantoOutraTransmiteNaoLancaExcecao() throws Exception {
        int escritores = 4;
        int rodadas = 200;
        ExecutorService pool = Executors.newFixedThreadPool(escritores + 1);
        CountDownLatch largada = new CountDownLatch(1);
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int t = 0; t < escritores; t++) {
            tarefas.add(() -> {
                largada.await();
                for (int i = 0; i < rodadas; i++) {
                    ClienteHandler h = ClienteFalso.novo().handler;
                    registry.adicionar(h);
                    registry.remover(h);
                }
                return null;
            });
        }
        tarefas.add(() -> {
            largada.await();
            for (int i = 0; i < rodadas; i++) {
                registry.transmitir(new Evento.Sistema("b" + i));
                registry.listar();
            }
            return null;
        });
        List<Future<Void>> futuros = new ArrayList<>();
        for (Callable<Void> t : tarefas) {
            futuros.add(pool.submit(t));
        }
        largada.countDown();
        for (Future<Void> f : futuros) {
            f.get(30, TimeUnit.SECONDS); // propaga qualquer ConcurrentModificationException
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, registry.quantidade());
    }

    @Test
    void identificarOMesmoNomeEmVariasThreadsTemUmUnicoVencedor() throws Exception {
        int n = 8;
        List<ClienteHandler> handlers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ClienteHandler h = ClienteFalso.novo().handler;
            registry.adicionar(h);
            handlers.add(h);
        }
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger vencedores = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (ClienteHandler h : handlers) {
            futuros.add(pool.submit(() -> {
                largada.await();
                if (registry.identificar(h, new Usuario("ana"))) {
                    vencedores.incrementAndGet();
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, vencedores.get());
    }
}
