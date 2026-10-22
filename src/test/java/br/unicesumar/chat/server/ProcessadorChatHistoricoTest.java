package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ao informar o nome, o cliente recebe o histórico salvo (sem rede, repositório em memória). */
class ProcessadorChatHistoricoTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final ProcessadorChat processador = new ProcessadorChat(chat, registry);

    @Test
    void novoClienteRecebeHistoricoDeQuemJaConversouEDepoisOOk() {
        ClienteFalso ana = ClienteFalso.novo();
        registry.adicionar(ana.handler);
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "primeira");
        processador.aoReceber(ana.handler, "segunda");

        ClienteFalso bia = ClienteFalso.novo();
        registry.adicionar(bia.handler);
        processador.aoReceber(bia.handler, "bia");

        List<Evento> eventos = bia.eventos();
        Evento.Historico h = assertInstanceOf(Evento.Historico.class, eventos.get(0));
        assertEquals(List.of("primeira", "segunda"),
                h.mensagens().stream().map(Evento.Msg::texto).toList());
        assertEquals("ana", h.mensagens().get(0).remetente());
        assertInstanceOf(Evento.Ok.class, eventos.get(1));
    }

    @Test
    void historicoSobreviveQuandoOUsuarioQueEscreveuJaSaiu() {
        ClienteFalso ana = ClienteFalso.novo();
        registry.adicionar(ana.handler);
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "deixei um recado");
        registry.remover(ana.handler);

        ClienteFalso bia = ClienteFalso.novo();
        registry.adicionar(bia.handler);
        processador.aoReceber(bia.handler, "bia");
        Evento.Historico h = (Evento.Historico) bia.eventos().get(0);
        assertEquals("deixei um recado", h.mensagens().get(0).texto());
    }

    @Test
    void nomeEmUsoNaoRecebeHistorico() {
        ClienteFalso ana = ClienteFalso.novo();
        registry.adicionar(ana.handler);
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(ana.handler, "oi");

        ClienteFalso intruso = ClienteFalso.novo();
        registry.adicionar(intruso.handler);
        processador.aoReceber(intruso.handler, "ana");
        assertEquals(1, intruso.eventos().size());
        assertInstanceOf(Evento.Erro.class, intruso.eventos().get(0));
        assertTrue(intruso.handler.usuario().isEmpty());
        assertEquals(new Usuario("ana"), ana.handler.usuario().orElseThrow());
    }
}
