package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Garante que o que o servidor envia é exatamente o que o cliente sabe ler (sem texto copiado). */
class ChatServiceMensagensCompartilhadasTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final MensagemRepositoryEmMemoria repo = new MensagemRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final CommandService comandos = new CommandService(chat, registry);

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    @Test
    void okDeEntradaEIgualAoTextoCompartilhadoELegivelPeloCliente() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        chat.entrar(c.handler, new Usuario("Ana"));
        Evento.Ok ok = (Evento.Ok) ultimo(c);
        assertEquals(Mensagens.entrouComo("Ana", "geral"), ok.texto());
        assertEquals("Ana", Mensagens.lerEntrada(ok.texto()).orElseThrow().nome());
    }

    @Test
    void okDeMudancaDeSalaEIgualAoTextoCompartilhado() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        comandos.executar(ana.handler, "/join java");
        Evento.Ok ok = (Evento.Ok) ultimo(ana);
        assertEquals(Mensagens.entrouNaSala("java"), ok.texto());
        assertEquals("java", Mensagens.lerMudancaDeSala(ok.texto()).orElseThrow());
    }

    @Test
    void avisosDePresencaSaoReconhecidosPeloCliente() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        ClienteFalso bia = ClienteFalso.novo();
        registry.adicionar(bia.handler);
        chat.entrar(bia.handler, new Usuario("bia"));
        Evento.Sistema entrada = (Evento.Sistema) ultimo(ana);
        assertEquals(Mensagens.avisoDeEntrada("bia", "geral"), entrada.aviso());
        assertTrue(Mensagens.ehAvisoDePresenca(entrada.aviso()));

        registry.remover(bia.handler);
        chat.sair(bia.handler);
        Evento.Sistema saida = (Evento.Sistema) ultimo(ana);
        assertEquals(Mensagens.avisoDeSaida("bia", "geral"), saida.aviso());
        assertTrue(Mensagens.ehAvisoDePresenca(saida.aviso()));
    }

    @Test
    void avisosDeBuscaESemHistoricoSaoOsTextosCompartilhados() {
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        comandos.executar(ana.handler, "/buscar texto nada");
        assertEquals(new Evento.Sistema(Mensagens.SEM_RESULTADO_DE_BUSCA), ultimo(ana));
        comandos.executar(ana.handler, "/history");
        assertEquals(new Evento.Sistema(Mensagens.SEM_HISTORICO), ultimo(ana));
    }
}
