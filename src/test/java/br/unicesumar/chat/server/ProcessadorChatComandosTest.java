package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Linhas que começam com / viram comandos; o resto continua sendo mensagem. */
class ProcessadorChatComandosTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final ProcessadorChat processador = new ProcessadorChat(chat, registry);

    private ClienteFalso entrar(String nome) {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        processador.aoReceber(c.handler, nome);
        return c;
    }

    @Test
    void linhaComBarraEExecutadaComoComandoENaoViraMensagem() {
        ClienteFalso ana = entrar("ana");
        processador.aoReceber(ana.handler, "/help");
        assertTrue(repo.buscarUltimas("geral", 10).isEmpty());
        assertTrue(ana.textos().isEmpty());
        assertInstanceOf(Evento.Sistema.class, ana.eventos().get(ana.eventos().size() - 1));
    }

    @Test
    void barraNoMeioDaLinhaContinuaSendoMensagem() {
        ClienteFalso ana = entrar("ana");
        processador.aoReceber(ana.handler, "veja o /help");
        assertEquals(List.of("veja o /help"), ana.textos());
    }

    @Test
    void comandoDesconhecidoViraErroENaoMensagem() {
        ClienteFalso ana = entrar("ana");
        processador.aoReceber(ana.handler, "/xyz");
        assertInstanceOf(Evento.Erro.class, ana.eventos().get(ana.eventos().size() - 1));
        assertTrue(repo.buscarUltimas("geral", 10).isEmpty());
    }

    @Test
    void antesDeInformarONomeABarraNaoEComando() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        processador.aoReceber(c.handler, "/help");
        assertInstanceOf(Evento.Erro.class, c.eventos().get(0));
        assertTrue(c.handler.usuario().isEmpty());
    }

    @Test
    void usersViaProcessadorMostraQuemEstaNaSala() {
        ClienteFalso ana = entrar("ana");
        entrar("bia");
        processador.aoReceber(ana.handler, "/users");
        Evento.Usuarios u = (Evento.Usuarios) ana.eventos().get(ana.eventos().size() - 1);
        assertEquals(List.of("ana", "bia"), u.nomes());
    }

    @Test
    void quitViaProcessadorEncerraSoOCliente() {
        ClienteFalso ana = entrar("ana");
        ClienteFalso bia = entrar("bia");
        processador.aoReceber(ana.handler, "/quit");
        assertFalse(ana.handler.ativo());
        assertTrue(bia.handler.ativo());
    }

    @Test
    void erroDeUmComandoNaoDerrubaOCliente() {
        ClienteFalso ana = entrar("ana");
        processador.aoReceber(ana.handler, "/history abc");
        processador.aoReceber(ana.handler, "ainda funciona");
        assertEquals(List.of("ainda funciona"), ana.textos());
    }

    @Test
    void construtorComCommandServiceExplicitoTambemFunciona() {
        ProcessadorChat p = new ProcessadorChat(chat, new CommandService(chat, registry));
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        p.aoReceber(c.handler, "caio");
        p.aoReceber(c.handler, "/help");
        assertInstanceOf(Evento.Sistema.class, c.eventos().get(c.eventos().size() - 1));
    }
}
