package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes unitários de cada comando (clientes falsos e repositório em memória, sem rede). */
class CommandServiceTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final CommandService comandos = new CommandService(chat, registry);
    private final ClienteFalso eu = ClienteFalso.identificado(registry, "ana");

    private Evento unicaResposta() {
        List<Evento> eventos = eu.eventos();
        assertEquals(1, eventos.size(), "esperava exatamente uma resposta: " + eventos);
        return eventos.get(0);
    }

    private void semear(String sala, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            repo.salvar(Mensagem.nova(ANA, sala, "m" + i, HORA));
        }
    }

    // ---------- identificação do comando ----------

    @Test
    void ehComandoQuandoALinhaComecaComBarra() {
        assertTrue(CommandService.ehComando("/help"));
        assertTrue(CommandService.ehComando("   /help"));
        assertFalse(CommandService.ehComando("oi /help"));
        assertFalse(CommandService.ehComando(""));
    }

    @Test
    void comandoDesconhecidoDevolveErroQueSugereHelp() {
        comandos.executar(eu.handler, "/voar");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, unicaResposta());
        assertTrue(e.texto().contains("/voar"));
        assertTrue(e.texto().contains("/help"));
    }

    @Test
    void barraSozinhaDevolveErro() {
        comandos.executar(eu.handler, "/");
        assertInstanceOf(Evento.Erro.class, unicaResposta());
    }

    @Test
    void nomeDoComandoIgnoraMaiusculasEEspacosExtras() {
        comandos.executar(eu.handler, "  /HeLp   ");
        assertInstanceOf(Evento.Sistema.class, unicaResposta());
    }

    @Test
    void executarLinhaSemBarraLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> comandos.executar(eu.handler, "oi"));
    }

    // ---------- /help ----------

    @Test
    void helpListaTodosOsComandosDisponiveis() {
        comandos.executar(eu.handler, "/help");
        Evento.Sistema s = assertInstanceOf(Evento.Sistema.class, unicaResposta());
        for (String c : List.of("/help", "/users", "/history", "/quit")) {
            assertTrue(s.aviso().contains(c), "falta " + c);
        }
    }

    // ---------- /users ----------

    @Test
    void usersListaOsUsuariosDaSalaEmOrdemAlfabeticaIncluindoVoce() {
        ClienteFalso.identificado(registry, "Caio");
        ClienteFalso.identificado(registry, "bia");
        comandos.executar(eu.handler, "/users");
        Evento.Usuarios u = assertInstanceOf(Evento.Usuarios.class, unicaResposta());
        assertEquals("geral", u.sala());
        assertEquals(List.of("ana", "bia", "Caio"), u.nomes());
    }

    @Test
    void usersNaoListaOutraSalaNemClienteNaoIdentificado() {
        ClienteFalso outraSala = ClienteFalso.identificado(registry, "bia");
        outraSala.handler.definirSala("java");
        registry.adicionar(ClienteFalso.novo().handler);
        comandos.executar(eu.handler, "/users");
        Evento.Usuarios u = (Evento.Usuarios) unicaResposta();
        assertEquals(List.of("ana"), u.nomes());
    }

    @Test
    void usersNaoEnviaNadaAosOutrosClientes() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        comandos.executar(eu.handler, "/users");
        assertTrue(bia.eventos().isEmpty());
    }

    // ---------- /history ----------

    @Test
    void historySemArgumentoDevolveAsUltimasDoLimiteInicial() {
        semear("geral", ChatService.HISTORICO_INICIAL + 3);
        comandos.executar(eu.handler, "/history");
        Evento.Historico h = assertInstanceOf(Evento.Historico.class, unicaResposta());
        assertEquals(ChatService.HISTORICO_INICIAL, h.mensagens().size());
        assertEquals("m4", h.mensagens().get(0).texto());
    }

    @Test
    void historyComQuantidadeDevolveEssaQuantidadeEmOrdem() {
        semear("geral", 10);
        comandos.executar(eu.handler, "/history 3");
        Evento.Historico h = (Evento.Historico) unicaResposta();
        assertEquals(List.of("m8", "m9", "m10"),
                h.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void historySemMensagensAvisaQueNaoHaNada() {
        comandos.executar(eu.handler, "/history");
        Evento.Sistema s = assertInstanceOf(Evento.Sistema.class, unicaResposta());
        assertTrue(s.aviso().toLowerCase().contains("não há mensagens"));
    }

    @Test
    void historyUsaASalaDoClienteENuncaTrazPrivadas() {
        semear("geral", 2);
        repo.salvar(Mensagem.nova(ANA, "java", "do java", HORA));
        repo.salvar(Mensagem.privada(ANA, new Usuario("bia"), "geral", "segredo", HORA));
        eu.handler.definirSala("java");
        comandos.executar(eu.handler, "/history");
        Evento.Historico h = (Evento.Historico) unicaResposta();
        assertEquals(List.of("do java"), h.mensagens().stream().map(Evento.Msg::texto).toList());

        ClienteFalso outra = ClienteFalso.identificado(registry, "caio");
        comandos.executar(outra.handler, "/history 50");
        Evento.Historico geral = (Evento.Historico) outra.eventos().get(0);
        assertEquals(List.of("m1", "m2"), geral.mensagens().stream().map(Evento.Msg::texto).toList());
    }

    @Test
    void historyAceitaOLimiteMaximoExato() {
        semear("geral", 5);
        comandos.executar(eu.handler, "/history " + CommandService.HISTORICO_MAXIMO);
        assertInstanceOf(Evento.Historico.class, unicaResposta());
    }

    @Test
    void historyRejeitaQuantidadeInvalidaComOUso() {
        for (String ruim : List.of("abc", "0", "-1", "1 2", "1.5",
                String.valueOf(CommandService.HISTORICO_MAXIMO + 1))) {
            ClienteFalso c = ClienteFalso.identificado(registry, "x" + Math.abs(ruim.hashCode() % 1000));
            comandos.executar(c.handler, "/history " + ruim);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, c.eventos().get(0), ruim);
            assertTrue(e.texto().contains("/history"), ruim);
        }
    }

    // ---------- /quit ----------

    @Test
    void quitEnviaDespedidaEEncerraAConexao() {
        comandos.executar(eu.handler, "/quit");
        Evento.Ok ok = assertInstanceOf(Evento.Ok.class, unicaResposta());
        assertTrue(ok.texto().toLowerCase().contains("até logo"));
        assertFalse(eu.handler.ativo());
        assertEquals(1, eu.fechamentos.get());
    }

    @Test
    void quitNaoAfetaOsOutrosClientes() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        comandos.executar(eu.handler, "/quit");
        assertTrue(bia.handler.ativo());
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void construtorRejeitaDependenciasNulas() {
        assertThrows(NullPointerException.class, () -> new CommandService(null, registry));
        assertThrows(NullPointerException.class, () -> new CommandService(chat, null));
    }
}
