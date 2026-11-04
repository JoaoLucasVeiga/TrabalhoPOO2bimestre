package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** /conversas e /conversa: cada usuário só enxerga as próprias conversas privadas. */
class CommandServiceConversaTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final Set<String> contas = Set.of("ana", "bia", "caio");
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone(),
            nome -> contas.contains(nome.toLowerCase()));
    private final CommandService comandos = new CommandService(chat, registry, new SalaService(chat, registry),
            null);
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
    private final ClienteFalso bia = ClienteFalso.identificado(registry, "bia");

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    @Test
    void conversasListaOsInterlocutoresDeQuemPediu() {
        chat.enviarPrivada(ana.handler, "bia", "oi");
        chat.enviarPrivada(ana.handler, "caio", "oi");
        comandos.executar(ana.handler, "/conversas");
        assertEquals(new Evento.Conversas(List.of("caio", "bia")), ultimo(ana));
    }

    @Test
    void conversaDevolveOHistoricoComOInterlocutor() {
        chat.enviarPrivada(ana.handler, "bia", "um");
        chat.enviarPrivada(bia.handler, "ana", "dois");
        comandos.executar(ana.handler, "/conversa bia");
        Evento.Conversa c = assertInstanceOf(Evento.Conversa.class, ultimo(ana));
        assertEquals("bia", c.com());
        assertEquals(List.of("um", "dois"), c.mensagens().stream().map(Evento.Privada::texto).toList());
    }

    @Test
    void conversaNuncaMostraConversaDeOutrosUsuarios() {
        chat.enviarPrivada(bia.handler, "caio", "segredo entre bia e caio");
        comandos.executar(ana.handler, "/conversa caio");
        Evento.Conversa c = assertInstanceOf(Evento.Conversa.class, ultimo(ana));
        assertEquals(List.of(), c.mensagens());
    }

    @Test
    void conversaAceitaQuantidadeEDevolveAsUltimas() {
        for (int i = 1; i <= 5; i++) {
            chat.enviarPrivada(ana.handler, "bia", "m" + i);
        }
        comandos.executar(ana.handler, "/conversa bia 2");
        Evento.Conversa c = assertInstanceOf(Evento.Conversa.class, ultimo(ana));
        assertEquals(List.of("m4", "m5"), c.mensagens().stream().map(Evento.Privada::texto).toList());
    }

    @Test
    void usoInvalidoDizOQueFazer() {
        comandos.executar(ana.handler, "/conversa");
        assertTrue(assertInstanceOf(Evento.Erro.class, ultimo(ana)).texto().contains("Uso: /conversa usuario"));
        comandos.executar(ana.handler, "/conversa bia abc");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        comandos.executar(ana.handler, "/conversa bia 0");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        comandos.executar(ana.handler, "/conversas bia");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
    }

    @Test
    void ajudaListaOsComandos() {
        comandos.executar(ana.handler, "/help");
        String aviso = ((Evento.Sistema) ultimo(ana)).aviso();
        assertTrue(aviso.contains("/conversas"));
        assertTrue(aviso.contains("/conversa usuario"));
    }
}
