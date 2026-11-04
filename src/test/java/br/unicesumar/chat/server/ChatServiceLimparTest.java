package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Limpar o histórico de uma sala (clientes falsos, sem rede). */
class ChatServiceLimparTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final ClienteFalso chefe = ClienteFalso.identificado(registry, "chefe");
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    @Test
    void apagaOHistoricoDaSalaEDevolveQuantasForam() {
        chat.enviarMensagem(new Usuario("ana"), "geral", "um");
        chat.enviarMensagem(new Usuario("ana"), "geral", "dois");
        assertEquals(2, chat.limparSala("geral", "chefe"));
        assertEquals(List.of(), chat.buscarHistorico("geral", 10));
    }

    @Test
    void avisaTodosNaSalaQuemLimpou() {
        chat.enviarMensagem(new Usuario("ana"), "geral", "um");
        ana.eventos();
        chat.limparSala("geral", "chefe");
        assertTrue(ana.eventos().stream().anyMatch(e -> e instanceof Evento.Sistema s
                && s.aviso().contains("chefe") && s.aviso().contains("geral")));
    }

    @Test
    void naoAvisaQuemEstaEmOutraSala() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        bia.handler.definirSala("java");
        bia.eventos();
        chat.limparSala("geral", "chefe");
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void falhaDoRepositorioSobeSemAvisarNinguem() {
        ChatService quebrado = new ChatService(new RepositorioQueFalhaAoApagar(), registry,
                Clock.systemDefaultZone());
        ana.eventos();
        assertThrows(RepositorioException.class, () -> quebrado.limparSala("geral", "chefe"));
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void nomeDeQuemLimpouEhObrigatorio() {
        assertThrows(NullPointerException.class, () -> chat.limparSala("geral", null));
    }

    /** Grava e lê normalmente, mas falha ao apagar. */
    private static final class RepositorioQueFalhaAoApagar extends MensagemRepositoryEmMemoria {
        @Override
        public int apagarSala(String sala) {
            throw new RepositorioException("Não foi possível apagar o histórico.", null);
        }
    }
}
