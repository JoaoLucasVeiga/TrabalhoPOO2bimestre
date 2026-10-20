package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Entrada de um cliente: identificação + histórico (repositório em memória, sem rede). */
class ChatServiceEntrarTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepository repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());

    private ClienteFalso novoCliente() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        return c;
    }

    private void semear(String sala, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            repo.salvar(Mensagem.nova(ANA, sala, "m" + i, HORA));
        }
    }

    @Test
    void entrarIdentificaOClienteEConfirmaComOk() {
        ClienteFalso c = novoCliente();
        chat.entrar(c.handler, new Usuario("bia"));
        assertEquals(new Usuario("bia"), c.handler.usuario().orElseThrow());
        assertInstanceOf(Evento.Ok.class, c.eventos().get(c.eventos().size() - 1));
    }

    @Test
    void semHistoricoNaoEnviaEventoDeHistorico() {
        ClienteFalso c = novoCliente();
        chat.entrar(c.handler, new Usuario("bia"));
        assertEquals(1, c.eventos().size());
        assertInstanceOf(Evento.Ok.class, c.eventos().get(0));
    }

    @Test
    void enviaOHistoricoDaSalaEmOrdemAntesDoOk() {
        semear("geral", 3);
        ClienteFalso c = novoCliente();
        chat.entrar(c.handler, new Usuario("bia"));
        assertEquals(2, c.eventos().size());
        Evento.Historico h = assertInstanceOf(Evento.Historico.class, c.eventos().get(0));
        assertEquals(List.of("m1", "m2", "m3"), h.mensagens().stream().map(Evento.Msg::texto).toList());
        assertInstanceOf(Evento.Ok.class, c.eventos().get(1));
    }

    @Test
    void historicoTrazSoAsUltimasMensagensNoLimiteDoServico() {
        semear("geral", ChatService.HISTORICO_INICIAL + 5);
        ClienteFalso c = novoCliente();
        chat.entrar(c.handler, new Usuario("bia"));
        Evento.Historico h = (Evento.Historico) c.eventos().get(0);
        assertEquals(ChatService.HISTORICO_INICIAL, h.mensagens().size());
        assertEquals("m6", h.mensagens().get(0).texto());
        assertEquals("m" + (ChatService.HISTORICO_INICIAL + 5),
                h.mensagens().get(h.mensagens().size() - 1).texto());
    }

    @Test
    void historicoEDaSalaAtualDoCliente() {
        semear("geral", 2);
        repo.salvar(Mensagem.nova(ANA, "java", "so no java", HORA));
        ClienteFalso c = novoCliente();
        c.handler.definirSala("java");
        chat.entrar(c.handler, new Usuario("bia"));
        Evento.Historico h = (Evento.Historico) c.eventos().get(0);
        assertEquals(List.of("so no java"), h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void historicoNuncaTrazMensagemPrivada() {
        semear("geral", 1);
        repo.salvar(Mensagem.privada(ANA, new Usuario("caio"), "geral", "segredo", HORA));
        ClienteFalso c = novoCliente();
        chat.entrar(c.handler, new Usuario("bia"));
        Evento.Historico h = (Evento.Historico) c.eventos().get(0);
        assertEquals(List.of("m1"), h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void nomeEmUsoLancaExcecaoENaoEnviaNada() {
        semear("geral", 2);
        ClienteFalso.identificado(registry, "ana");
        ClienteFalso c = novoCliente();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.entrar(c.handler, new Usuario("ANA")));
        assertTrue(e.getMessage().contains("em uso"));
        assertTrue(c.eventos().isEmpty());
        assertTrue(c.handler.usuario().isEmpty());
    }

    @Test
    void clienteNaoRegistradoLancaExcecao() {
        ClienteFalso solto = ClienteFalso.novo();
        assertThrows(IllegalArgumentException.class,
                () -> chat.entrar(solto.handler, new Usuario("bia")));
    }

    @Test
    void falhaAoBuscarOHistoricoNaoIdentificaOCliente() {
        MensagemRepository quebrado = new MensagemRepository() {
            @Override
            public Mensagem salvar(Mensagem m) {
                return m.comId(1);
            }

            @Override
            public List<Mensagem> buscarUltimas(String sala, int quantidade) {
                throw new RepositorioException("Não foi possível carregar o histórico.", null);
            }
        };
        ChatService servico = new ChatService(quebrado, registry, Clock.systemDefaultZone());
        ClienteFalso c = novoCliente();
        assertThrows(RepositorioException.class,
                () -> servico.entrar(c.handler, new Usuario("bia")));
        assertTrue(c.handler.usuario().isEmpty());
        assertTrue(c.eventos().isEmpty());
    }

    @Test
    void outrosClientesNaoRecebemONovoHistorico() {
        semear("geral", 2);
        ClienteFalso antigo = ClienteFalso.identificado(registry, "ana");
        ClienteFalso novo = novoCliente();
        chat.entrar(novo.handler, new Usuario("bia"));
        assertTrue(antigo.eventos().isEmpty());
    }
}
