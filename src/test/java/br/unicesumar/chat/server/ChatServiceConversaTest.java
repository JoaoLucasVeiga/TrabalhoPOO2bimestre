package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Conversa privada de verdade: histórico por par e envio para quem está offline (se tem conta). */
class ChatServiceConversaTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final Set<String> contas = Set.of("ana", "bia", "caio");
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone(),
            nome -> contas.contains(nome.toLowerCase()));
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    @Test
    void paraQuemEstaOfflineMasTemContaGravaEConfirmaAoRemetente() {
        Mensagem salva = chat.enviarPrivada(ana.handler, "bia", "ola, bia");
        assertEquals("ola, bia", salva.conteudo());
        assertEquals(1, repo.buscarConversa("ana", "bia", 10).size());
        assertTrue(ana.eventos().stream().anyMatch(e -> e instanceof Evento.Privada p
                && p.de().equals("ana") && p.para().equals("bia")));
    }

    @Test
    void oDestinatarioOfflineVeAMensagemQuandoPedirAConversa() {
        chat.enviarPrivada(ana.handler, "bia", "te espero");
        assertEquals(List.of("te espero"), chat.conversa("bia", "ana", 10).stream().map(Mensagem::conteudo).toList());
        assertEquals(List.of("ana"), chat.conversasDe("bia"));
    }

    @Test
    void paraQuemNaoTemContaNemEstaOnlineRecusaENaoGrava() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> chat.enviarPrivada(ana.handler, "fulano", "oi"));
        assertTrue(e.getMessage().contains("fulano"));
        assertEquals(List.of(), repo.listarConversas("ana"));
    }

    @Test
    void paraQuemEstaOnlineAindaEntregaNaHora() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarPrivada(ana.handler, "bia", "oi online");
        assertTrue(bia.eventos().stream().anyMatch(e -> e instanceof Evento.Privada p && p.texto().equals("oi online")));
    }

    @Test
    void semAContaExisteTodoMundoOfflineContinuaRecusado() {
        ChatService antigo = new ChatService(repo, registry, Clock.systemDefaultZone());
        assertThrows(IllegalArgumentException.class, () -> antigo.enviarPrivada(ana.handler, "bia", "oi"));
        assertEquals(List.of(), repo.listarConversas("ana"));
    }

    @Test
    void naoMandaPrivadaParaSiMesmoNemOfflineNemOnline() {
        assertThrows(IllegalArgumentException.class, () -> chat.enviarPrivada(ana.handler, "ana", "oi"));
    }

    @Test
    void conversasDeListaQuemJaConversou() {
        chat.enviarPrivada(ana.handler, "bia", "1");
        chat.enviarPrivada(ana.handler, "caio", "2");
        assertEquals(List.of("caio", "bia"), chat.conversasDe("ana"));
    }

    @Test
    void conversaDevolveOsDoisSentidos() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        chat.enviarPrivada(ana.handler, "bia", "um");
        chat.enviarPrivada(bia.handler, "ana", "dois");
        assertEquals(List.of("um", "dois"),
                chat.conversa("ana", "bia", 10).stream().map(Mensagem::conteudo).toList());
    }

    @Test
    void usuarioNuloNaoEhAceitoNoEnvio() {
        assertThrows(NullPointerException.class, () -> new ChatService(repo, registry, Clock.systemDefaultZone(), null));
    }
}
