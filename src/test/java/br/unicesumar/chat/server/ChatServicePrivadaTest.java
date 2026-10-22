package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mensagens privadas: clientes falsos e repositório espião, sem rede nem banco. */
class ChatServicePrivadaTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-01T09:05:30Z"), ZoneOffset.UTC);

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final ChatService chat = new ChatService(repo, registry, RELOGIO);
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
    private final ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
    private final ClienteFalso caio = ClienteFalso.identificado(registry, "caio");

    private static Evento.Privada privada(String de, String para, String texto) {
        return new Evento.Privada(de, para, LocalDateTime.of(2026, 10, 1, 9, 5, 30), texto);
    }

    @Test
    void entregaAoDestinatarioEConfirmaAoRemetenteComOMesmoEvento() {
        chat.enviarPrivada(ana.handler, "bia", "psiu");
        assertEquals(List.of(privada("ana", "bia", "psiu")), bia.eventos());
        assertEquals(List.of(privada("ana", "bia", "psiu")), ana.eventos());
    }

    @Test
    void terceirosNaoRecebemNada() {
        chat.enviarPrivada(ana.handler, "bia", "segredo");
        assertTrue(caio.eventos().isEmpty());
    }

    @Test
    void persisteComRemetenteDestinatarioEConteudo() {
        Mensagem m = chat.enviarPrivada(ana.handler, "bia", "guardada");
        assertNotNull(m.id());
        assertEquals(1, repo.salvas().size());
        Mensagem salva = repo.salvas().get(0);
        assertEquals(new Usuario("ana"), salva.remetente());
        assertEquals(new Usuario("bia"), salva.destinatario().orElseThrow());
        assertEquals("guardada", salva.conteudo());
        assertTrue(salva.ehPrivada());
    }

    @Test
    void usaOHorarioDoRelogioEASalaAtualDoRemetente() {
        ana.handler.definirSala("java");
        chat.enviarPrivada(ana.handler, "bia", "oi");
        Mensagem salva = repo.salvas().get(0);
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 5, 30), salva.horario());
        assertEquals("java", salva.sala());
    }

    @Test
    void destinatarioNaoPrecisaEstarNaMesmaSala() {
        bia.handler.definirSala("java");
        chat.enviarPrivada(ana.handler, "bia", "chego onde você estiver");
        assertEquals(1, bia.eventos().size());
    }

    @Test
    void nomeDoDestinatarioIgnoraMaiusculas() {
        chat.enviarPrivada(ana.handler, "BIA", "oi");
        assertEquals(1, bia.eventos().size());
    }

    @Test
    void destinatarioOfflineLancaErroQueDizOQueFazerENaoPersisteNemEntrega() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.enviarPrivada(ana.handler, "zeca", "oi"));
        assertTrue(e.getMessage().contains("zeca"));
        assertTrue(e.getMessage().contains("não está online"));
        assertTrue(e.getMessage().contains("/users"));
        assertTrue(repo.salvas().isEmpty());
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void conexaoAindaNaoIdentificadaNaoContaComoDestinatario() {
        registry.adicionar(ClienteFalso.novo().handler);
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana.handler, "", "oi"));
        assertTrue(repo.salvas().isEmpty());
    }

    @Test
    void mensagemParaSiMesmoERejeitadaENaoPersiste() {
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana.handler, "ana", "oi"));
        assertTrue(repo.salvas().isEmpty());
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void textoVazioNuloOuGrandeDemaisERejeitadoENaoPersiste() {
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana.handler, "bia", "  "));
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana.handler, "bia", null));
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviarPrivada(ana.handler, "bia", "x".repeat(501)));
        assertTrue(repo.salvas().isEmpty());
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void aceitaTextoComExatamente500Caracteres() {
        chat.enviarPrivada(ana.handler, "bia", "x".repeat(500));
        assertEquals(1, repo.salvas().size());
    }

    @Test
    void remetenteSemIdentificacaoNaoPodeEnviar() {
        ClienteFalso anonimo = ClienteFalso.novo();
        registry.adicionar(anonimo.handler);
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(anonimo.handler, "bia", "oi"));
        assertTrue(repo.salvas().isEmpty());
    }

    @Test
    void falhaNoRepositorioPropagaENaoEntregaNada() {
        MensagemRepository quebrado = new RepositorioQuebrado();
        ChatService servico = new ChatService(quebrado, registry, RELOGIO);
        assertThrows(RepositorioException.class, () -> servico.enviarPrivada(ana.handler, "bia", "oi"));
        assertTrue(bia.eventos().isEmpty());
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void destinatarioQueCaiuNoMeioDoEnvioGeraErroSemConfirmarAoRemetente() {
        ClienteFalso zeca = ClienteFalso.quebrado();
        registry.adicionar(zeca.handler);
        registry.identificar(zeca.handler, new Usuario("zeca"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.enviarPrivada(ana.handler, "zeca", "oi"));
        assertTrue(e.getMessage().contains("zeca"));
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void privadaNuncaApareceNoHistoricoDeQuemEntraNemNasBuscas() {
        chat.enviarMensagem(new Usuario("ana"), "geral", "publica java");
        chat.enviarPrivada(ana.handler, "bia", "segredo java");

        List<String> hist = chat.buscarHistorico("geral", 50).stream().map(Mensagem::conteudo).toList();
        assertEquals(List.of("publica java"), hist);
        assertEquals(List.of("publica java"),
                chat.buscarPorTexto("geral", "java").stream().map(Mensagem::conteudo).toList());
        assertEquals(List.of("publica java"),
                chat.buscarPorUsuario("geral", "ana").stream().map(Mensagem::conteudo).toList());

        ClienteFalso novo = ClienteFalso.novo();
        registry.adicionar(novo.handler);
        chat.entrar(novo.handler, new Usuario("duda"));
        Evento.Historico h = (Evento.Historico) novo.eventos().get(0);
        assertEquals(List.of("publica java"), h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void privadaNaoViraBroadcastPublico() {
        chat.enviarPrivada(ana.handler, "bia", "so nos dois");
        assertTrue(caio.textos().isEmpty());
        assertTrue(bia.textos().isEmpty()); // textos() = só MSG públicas
    }

    /** Repositório cuja gravação sempre falha. */
    static class RepositorioQuebrado implements MensagemRepository {
        @Override
        public Mensagem salvar(Mensagem m) {
            throw new RepositorioException("Não foi possível salvar a mensagem.", null);
        }

        @Override
        public List<Mensagem> buscarUltimas(String sala, int quantidade) {
            return List.of();
        }
    }
}
