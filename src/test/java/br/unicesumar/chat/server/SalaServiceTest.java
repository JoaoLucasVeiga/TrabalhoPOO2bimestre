package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Salas: troca de sala, avisos, histórico e listagem (clientes falsos, sem rede). */
class SalaServiceTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final SalaService salas = new SalaService(chat, registry);
    private final ClienteFalso ana = ClienteFalso.identificado(registry, "ana");

    private static List<Evento> semAvisosDeSistema(ClienteFalso c) {
        return c.eventos().stream().filter(e -> !(e instanceof Evento.Sistema)).toList();
    }

    @Test
    void entrarSalaMudaASalaDoClienteEConfirmaComOk() {
        salas.entrarSala(ana.handler, "java");
        assertEquals("java", ana.handler.sala());
        assertEquals(new Evento.Ok("Você entrou na sala java."), ana.eventos().get(ana.eventos().size() - 1));
    }

    @Test
    void normalizaONomeDaSalaParaMinusculas() {
        salas.entrarSala(ana.handler, "JaVa");
        assertEquals("java", ana.handler.sala());
    }

    @Test
    void entrarSalaEnviaOHistoricoDaNovaSalaAntesDoOk() {
        repo.salvar(Mensagem.nova(new Usuario("bia"), "java", "bem-vindo ao java", HORA));
        repo.salvar(Mensagem.nova(new Usuario("bia"), "geral", "so na geral", HORA));
        salas.entrarSala(ana.handler, "java");
        List<Evento> eventos = ana.eventos();
        Evento.Historico h = assertInstanceOf(Evento.Historico.class, eventos.get(0));
        assertEquals(List.of("bem-vindo ao java"), h.mensagens().stream().map(Evento.Msg::texto).toList());
        assertInstanceOf(Evento.Ok.class, eventos.get(1));
    }

    @Test
    void salaSemHistoricoNaoEnviaEventoDeHistorico() {
        salas.entrarSala(ana.handler, "vazia");
        assertEquals(1, ana.eventos().size());
        assertInstanceOf(Evento.Ok.class, ana.eventos().get(0));
    }

    @Test
    void osDaSalaAntigaVeemOAvisoDeSaidaEOsDaNovaVeemOAvisoDeEntrada() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia"); // fica na geral
        ClienteFalso caio = ClienteFalso.identificado(registry, "caio");
        caio.handler.definirSala("java");
        salas.entrarSala(ana.handler, "java");
        assertEquals(List.of(new Evento.Sistema("ana saiu da sala geral.")), bia.eventos());
        assertEquals(List.of(new Evento.Sistema("ana entrou na sala java.")), caio.eventos());
    }

    @Test
    void quemTrocouDeSalaNaoRecebeOsPropriosAvisos() {
        salas.entrarSala(ana.handler, "java");
        assertTrue(ana.eventos().stream().noneMatch(e -> e instanceof Evento.Sistema));
    }

    @Test
    void depoisDeTrocarDeSalaAsMensagensSoChegamAosDaNovaSala() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        salas.entrarSala(ana.handler, "java");
        chat.enviarMensagem(new Usuario("bia"), "geral", "na geral");
        chat.enviarMensagem(new Usuario("bia"), "java", "no java");
        assertEquals(List.of("no java"), ana.textos());
        assertEquals(List.of("na geral"), bia.textos());
    }

    @Test
    void nomeInvalidoLancaErroENaoMudaDeSala() {
        for (String ruim : new String[] {"", "  ", "minha sala", "a".repeat(21), "sala!"}) {
            assertThrows(IllegalArgumentException.class, () -> salas.entrarSala(ana.handler, ruim), ruim);
        }
        assertThrows(IllegalArgumentException.class, () -> salas.entrarSala(ana.handler, null));
        assertEquals("geral", ana.handler.sala());
        assertTrue(ana.eventos().isEmpty());
    }

    @Test
    void entrarNaSalaOndeJaEstaLancaErro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> salas.entrarSala(ana.handler, "GERAL"));
        assertTrue(e.getMessage().contains("já está"));
    }

    @Test
    void clienteNaoIdentificadoNaoPodeTrocarDeSala() {
        ClienteFalso anonimo = ClienteFalso.novo();
        registry.adicionar(anonimo.handler);
        assertThrows(IllegalArgumentException.class, () -> salas.entrarSala(anonimo.handler, "java"));
        assertEquals("geral", anonimo.handler.sala());
    }

    @Test
    void falhaAoBuscarOHistoricoNaoMudaASalaNemAvisaNinguem() {
        MensagemRepository quebrado = new RepositorioQueFalhaAoLer();
        ChatService servico = new ChatService(quebrado, registry, Clock.systemDefaultZone());
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        SalaService comFalha = new SalaService(servico, registry);
        assertThrows(RepositorioException.class, () -> comFalha.entrarSala(ana.handler, "java"));
        assertEquals("geral", ana.handler.sala());
        assertTrue(bia.eventos().isEmpty());
    }

    // ---------- /leave ----------

    @Test
    void sairDaSalaVoltaParaAGeral() {
        salas.entrarSala(ana.handler, "java");
        salas.sairSala(ana.handler);
        assertEquals("geral", ana.handler.sala());
    }

    @Test
    void sairDaSalaGeralLancaErroQueDizOQueFazer() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> salas.sairSala(ana.handler));
        assertTrue(e.getMessage().contains("/join"));
    }

    // ---------- /rooms ----------

    @Test
    void listarSalasSempreIncluiAGeral() {
        assertEquals(List.of("geral"), new SalaService(chat, new ClientRegistry()).listarSalas());
    }

    @Test
    void listarSalasMostraAsSalasComGenteEmOrdemSemRepetir() {
        ClienteFalso bia = ClienteFalso.identificado(registry, "bia");
        ClienteFalso caio = ClienteFalso.identificado(registry, "caio");
        salas.entrarSala(bia.handler, "zeta");
        salas.entrarSala(caio.handler, "alfa");
        salas.entrarSala(ana.handler, "alfa");
        assertEquals(List.of("alfa", "geral", "zeta"), salas.listarSalas());
    }

    @Test
    void salaVaziaDeixaDeSerListada() {
        salas.entrarSala(ana.handler, "efemera");
        assertTrue(salas.listarSalas().contains("efemera"));
        salas.sairSala(ana.handler);
        assertFalse(salas.listarSalas().contains("efemera"));
    }

    @Test
    void conexaoNaoIdentificadaNaoCriaSalaNaListagem() {
        ClienteFalso anonimo = ClienteFalso.novo();
        registry.adicionar(anonimo.handler);
        anonimo.handler.definirSala("fantasma");
        assertEquals(List.of("geral"), salas.listarSalas());
    }

    @Test
    void construtorRejeitaDependenciasNulas() {
        assertThrows(NullPointerException.class, () -> new SalaService(null, registry));
        assertThrows(NullPointerException.class, () -> new SalaService(chat, null));
    }

    /** Grava normalmente, mas falha ao buscar o histórico (o cenário que o teste acima exige). */
    private static final class RepositorioQueFalhaAoLer implements MensagemRepository {
        @Override
        public Mensagem salvar(Mensagem m) {
            return m;
        }

        @Override
        public List<Mensagem> buscarUltimas(String sala, int quantidade) {
            throw new RepositorioException("Não foi possível ler o histórico.", null);
        }
    }
}
