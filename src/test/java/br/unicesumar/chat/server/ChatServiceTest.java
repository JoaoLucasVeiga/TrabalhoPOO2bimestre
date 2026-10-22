package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes unitários: repositório em memória e clientes falsos, sem rede nem banco. */
class ChatServiceTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-01T09:05:30Z"), ZoneOffset.UTC);

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepository repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, RELOGIO);

    @Test
    void enviarMensagemPersisteNoRepositorio() {
        chat.enviarMensagem(ANA, "geral", "olá");
        List<Mensagem> salvas = repo.buscarUltimas("geral", 10);
        assertEquals(1, salvas.size());
        assertEquals("olá", salvas.get(0).conteudo());
        assertEquals(ANA, salvas.get(0).remetente());
    }

    @Test
    void enviarMensagemDevolveAMensagemComIdEHorarioDoRelogio() {
        Mensagem m = chat.enviarMensagem(ANA, "geral", "olá");
        assertNotNull(m.id());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 5, 30), m.horario());
    }

    @Test
    void enviarMensagemTransmiteATodosDaSalaIncluindoORemetente() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarMensagem(ANA, "geral", "oi pessoal");
        assertEquals(List.of("oi pessoal"), ana.textos());
        assertEquals(List.of("oi pessoal"), bia.textos());
    }

    @Test
    void eventoTransmitidoTemSalaRemetenteHorarioETexto() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarMensagem(ANA, "geral", "oi");
        Evento.Msg e = (Evento.Msg) bia.eventos().get(0);
        assertEquals("geral", e.sala());
        assertEquals("ana", e.remetente());
        assertEquals("oi", e.texto());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 5, 30), e.horario());
    }

    @Test
    void naoTransmiteParaClientesDeOutraSala() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        bia.handler.definirSala("java");
        ClienteFalso caio = ClienteFalso.identificado(registry, "caio");
        chat.enviarMensagem(ANA, "geral", "só geral");
        assertTrue(bia.textos().isEmpty());
        assertEquals(List.of("só geral"), caio.textos());
    }

    @Test
    void naoTransmiteParaClienteAindaNaoIdentificado() {
        ClienteFalso anonimo = ClienteFalso.novo();
        registry.adicionar(anonimo.handler);
        chat.enviarMensagem(ANA, "geral", "oi");
        assertTrue(anonimo.eventos().isEmpty());
    }

    @Test
    void mensagemVaziaLancaExcecaoENaoPersisteNemTransmite() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        assertThrows(IllegalArgumentException.class, () -> chat.enviarMensagem(ANA, "geral", "   "));
        assertTrue(repo.buscarUltimas("geral", 10).isEmpty());
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void mensagemNulaLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> chat.enviarMensagem(ANA, "geral", null));
    }

    @Test
    void mensagemComExatamente500CaracteresEAceita() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarMensagem(ANA, "geral", "x".repeat(500));
        assertEquals(1, bia.textos().size());
    }

    @Test
    void mensagemComMaisDe500CaracteresEhRejeitadaENaoPersiste() {
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviarMensagem(ANA, "geral", "x".repeat(501)));
        assertTrue(repo.buscarUltimas("geral", 10).isEmpty());
    }

    @Test
    void remetenteNuloLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> chat.enviarMensagem(null, "geral", "oi"));
    }

    @Test
    void falhaNoRepositorioPropagaENaoTransmite() {
        MensagemRepository quebrado = new MensagemRepository() {
            @Override
            public Mensagem salvar(Mensagem mensagem) {
                throw new RepositorioException("banco fora do ar", null);
            }

            @Override
            public List<Mensagem> buscarUltimas(String sala, int quantidade) {
                return List.of();
            }
        };
        ChatService servico = new ChatService(quebrado, registry, RELOGIO);
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        assertThrows(RepositorioException.class, () -> servico.enviarMensagem(ANA, "geral", "oi"));
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void clienteComConexaoQuebradaNaoImpedeOsOutrosDeReceber() {
        ClienteFalso ruim = ClienteFalso.quebrado();
        registry.adicionar(ruim.handler);
        registry.identificar(ruim.handler, new Usuario("zeca"));
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarMensagem(ANA, "geral", "oi");
        assertEquals(List.of("oi"), bia.textos());
        assertEquals(1, registry.quantidade());
    }

    @Test
    void buscarHistoricoDevolveAsUltimasDaSalaEmOrdem() {
        chat.enviarMensagem(ANA, "geral", "1");
        chat.enviarMensagem(ANA, "geral", "2");
        chat.enviarMensagem(ANA, "java", "outra sala");
        chat.enviarMensagem(ANA, "geral", "3");
        List<String> textos = chat.buscarHistorico("geral", 2).stream().map(Mensagem::conteudo).toList();
        assertEquals(List.of("2", "3"), textos);
    }

    @Test
    void construtorRejeitaDependenciasNulas() {
        assertThrows(NullPointerException.class, () -> new ChatService(null, registry, RELOGIO));
        assertThrows(NullPointerException.class, () -> new ChatService(repo, null, RELOGIO));
        assertThrows(NullPointerException.class, () -> new ChatService(repo, registry, null));
    }
}
