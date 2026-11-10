package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tabela de comandos: registro, busca sem diferenciar maiúsculas e ordem de registro. */
class TabelaDeComandosTest {

    private static final Comando NADA = (origem, argumentos) -> { };

    @Test
    void comecaVazia() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        assertTrue(tabela.todas().isEmpty());
        assertTrue(tabela.buscar("help").isEmpty());
    }

    @Test
    void registraEEncontraPeloNomeIgnorandoMaiusculas() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        tabela.registrar("help", "/help", "Mostra a ajuda.", NADA);
        var entrada = tabela.buscar("HeLp").orElseThrow();
        assertEquals("/help", entrada.uso());
        assertEquals("Mostra a ajuda.", entrada.descricao());
        assertSame(NADA, entrada.comando());
    }

    @Test
    void mantemAOrdemDeRegistro() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        tabela.registrar("b", "/b", "segundo", NADA);
        tabela.registrar("a", "/a", "primeiro", NADA);
        assertEquals(List.of("b", "a"), tabela.todas().stream().map(TabelaDeComandos.Entrada::nome).toList());
    }

    @Test
    void nomeRepetidoEhErroDeProgramacao() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        tabela.registrar("join", "/join sala", "entra", NADA);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> tabela.registrar("JOIN", "/join sala", "de novo", NADA));
        assertTrue(e.getMessage().contains("join"));
    }

    @Test
    void argumentosNulosSaoRecusados() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        assertThrows(NullPointerException.class, () -> tabela.registrar(null, "/x", "d", NADA));
        assertThrows(NullPointerException.class, () -> tabela.registrar("x", "/x", "d", null));
    }

    @Test
    void listaDeTodasNaoPodeSerAlteradaDeFora() {
        TabelaDeComandos tabela = new TabelaDeComandos();
        tabela.registrar("a", "/a", "d", NADA);
        assertThrows(UnsupportedOperationException.class, () -> tabela.todas().clear());
    }
}
