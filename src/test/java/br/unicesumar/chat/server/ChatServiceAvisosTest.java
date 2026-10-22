package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Avisos de entrada e saída (clientes falsos, sem rede). */
class ChatServiceAvisosTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
            Clock.systemDefaultZone());

    private ClienteFalso novo() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        return c;
    }

    @Test
    void quemJaEstaNaSalaRecebeOAvisoDeEntrada() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso bia = novo();
        chat.entrar(bia.handler, new Usuario("bia"));
        assertEquals(List.of(new Evento.Sistema("bia entrou na sala geral.")), ana.eventos());
    }

    @Test
    void quemEntraNaoRecebeOProprioAviso() {
        ClienteFalso bia = novo();
        chat.entrar(bia.handler, new Usuario("bia"));
        assertTrue(bia.eventos().stream().noneMatch(e -> e instanceof Evento.Sistema));
    }

    @Test
    void clientesDeOutraSalaENaoIdentificadosNaoRecebemOAviso() {
        ClienteFalso outraSala = ClienteFalso.identificado(registry, "caio");
        outraSala.handler.definirSala("java");
        ClienteFalso anonimo = novo();
        ClienteFalso bia = novo();
        chat.entrar(bia.handler, new Usuario("bia"));
        assertTrue(outraSala.eventos().isEmpty());
        assertTrue(anonimo.eventos().isEmpty());
    }

    @Test
    void nomeEmUsoNaoGeraAvisoParaNinguem() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso intruso = novo();
        assertThrows(IllegalArgumentException.class,
                () -> chat.entrar(intruso.handler, new Usuario("ana")));
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void sairAvisaQuemFicouNaSala() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        registry.remover(bia.handler);
        chat.sair(bia.handler);
        assertEquals(List.of(new Evento.Sistema("bia saiu da sala geral.")), ana.eventos());
        assertTrue(bia.eventos().isEmpty());
    }

    @Test
    void sairDeClienteQueNuncaSeIdentificouNaoAvisaNinguem() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso anonimo = novo();
        registry.remover(anonimo.handler);
        chat.sair(anonimo.handler);
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void sairNaoAvisaOutrasSalas() {
        ClienteFalso caio = ClienteFalso.identificado(registry, "caio");
        caio.handler.definirSala("java");
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        registry.remover(bia.handler);
        chat.sair(bia.handler);
        assertTrue(caio.eventos().isEmpty());
    }

    @Test
    void processadorAvisaASaidaAoDesconectar() {
        ProcessadorChat processador = new ProcessadorChat(chat, registry);
        ClienteFalso ana = novo();
        ClienteFalso bia = novo();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(bia.handler, "bia");
        registry.remover(bia.handler);
        processador.aoDesconectar(bia.handler);
        assertTrue(ana.eventos().contains(new Evento.Sistema("bia saiu da sala geral.")));
    }

    @Test
    void aposQuitOsOutrosVeemOAvisoDeSaida() {
        ProcessadorChat processador = new ProcessadorChat(chat, registry);
        ClienteFalso ana = novo();
        ClienteFalso bia = novo();
        processador.aoReceber(ana.handler, "ana");
        processador.aoReceber(bia.handler, "bia");
        processador.aoReceber(bia.handler, "/quit");
        registry.remover(bia.handler); // o Servidor faz isso antes de avisar a desconexão
        processador.aoDesconectar(bia.handler);
        assertEquals(new Evento.Sistema("bia saiu da sala geral."),
                ana.eventos().get(ana.eventos().size() - 1));
    }

    @Test
    void desconectarClienteNaoIdentificadoNaoLancaNemAvisa() {
        ProcessadorChat processador = new ProcessadorChat(chat, registry);
        ClienteFalso ana = novo();
        processador.aoReceber(ana.handler, "ana");
        ClienteFalso anonimo = novo();
        registry.remover(anonimo.handler);
        assertDoesNotThrow(() -> processador.aoDesconectar(anonimo.handler));
        assertEquals(1, ana.eventos().size()); // só o próprio OK
    }
}
