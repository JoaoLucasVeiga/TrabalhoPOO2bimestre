package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes unitários do processador que liga a rede ao ChatService (sem rede real). */
class ProcessadorChatTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepository repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final ProcessadorChat processador = new ProcessadorChat(chat, registry);

    private ClienteFalso conectar() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        processador.aoConectar(c.handler);
        return c;
    }

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> eventos = c.eventos();
        return eventos.get(eventos.size() - 1);
    }

    @Test
    void aoConectarPedeONomeDeUsuario() {
        ClienteFalso c = conectar();
        Evento.Sistema e = assertInstanceOf(Evento.Sistema.class, c.eventos().get(0));
        assertTrue(e.aviso().toLowerCase().contains("nome"));
    }

    @Test
    void primeiraLinhaIdentificaOUsuarioEConfirmaComOk() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "ana");
        assertEquals(new Usuario("ana"), c.handler.usuario().orElseThrow());
        assertInstanceOf(Evento.Ok.class, ultimo(c));
    }

    @Test
    void nomeInvalidoGeraErroENaoIdentifica() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "a");
        assertInstanceOf(Evento.Erro.class, ultimo(c));
        assertTrue(c.handler.usuario().isEmpty());
    }

    @Test
    void nomeJaEmUsoGeraErroQueDizOQueFazer() {
        ClienteFalso primeiro = conectar();
        processador.aoReceber(primeiro.handler, "ana");
        ClienteFalso segundo = conectar();
        processador.aoReceber(segundo.handler, "ANA");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(segundo));
        assertTrue(erro.texto().contains("em uso"));
        assertTrue(erro.texto().toLowerCase().contains("escolha outro"));
        assertTrue(segundo.handler.usuario().isEmpty());
    }

    @Test
    void depoisDeIdentificadoACadaLinhaViraMensagemParaTodosDaSala() {
        ClienteFalso ana = conectar();
        ClienteFalso bia = conectar();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(bia.handler, "bia");
        processador.aoReceber(ana.handler, "olá, bia");
        assertEquals(List.of("olá, bia"), ana.textos());
        assertEquals(List.of("olá, bia"), bia.textos());
    }

    @Test
    void mensagemEnviadaFicaPersistida() {
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "guardada");
        assertEquals(1, repo.buscarUltimas("geral", 10).size());
    }

    @Test
    void linhaVaziaGeraErroDizendoOQueFazerENaoPersiste() {
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "   ");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(erro.texto().contains("vazia"));
        assertTrue(repo.buscarUltimas("geral", 10).isEmpty());
    }

    @Test
    void mensagemComMaisDe500CaracteresGeraErroMencionandoOLimite() {
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "x".repeat(501));
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(erro.texto().contains("500"));
    }

    @Test
    void falhaNoRepositorioViraErroAmigavelParaOCliente() {
        MensagemRepository quebrado = new MensagemRepository() {
            @Override
            public br.unicesumar.chat.model.Mensagem salvar(br.unicesumar.chat.model.Mensagem m) {
                throw new br.unicesumar.chat.persistence.RepositorioException(
                        "Não foi possível salvar a mensagem. Tente novamente em instantes.", null);
            }

            @Override
            public List<br.unicesumar.chat.model.Mensagem> buscarUltimas(String sala, int n) {
                return List.of();
            }
        };
        ClientRegistry reg = new ClientRegistry();
        ProcessadorChat p = new ProcessadorChat(
                new ChatService(quebrado, reg, Clock.systemDefaultZone()), reg);
        ClienteFalso c = ClienteFalso.novo();
        reg.adicionar(c.handler);
        p.aoReceber(c.handler, "ana");
        p.aoReceber(c.handler, "oi");
        Evento.Erro erro = assertInstanceOf(Evento.Erro.class, c.eventos().get(c.eventos().size() - 1));
        assertTrue(erro.texto().contains("Tente novamente"));
    }
}
