package br.unicesumar.chat.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalaTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final Usuario BIA = new Usuario("bia");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 0);

    private static Mensagem msg(String sala, String texto) {
        return Mensagem.nova(ANA, sala, texto, HORA);
    }

    @Test
    void salaPadraoSeChamaGeral() {
        assertEquals("geral", Sala.PADRAO);
        assertEquals("geral", Sala.padrao().nome());
    }

    @Test
    void salaNovaNaoTemMensagens() {
        assertTrue(new Sala("java").mensagens().isEmpty());
    }

    @Test
    void normalizaONomeParaMinusculas() {
        assertEquals("java", new Sala("JaVa").nome());
    }

    @Test
    void rejeitaNomeNuloVazioOuComEspaco() {
        assertThrows(IllegalArgumentException.class, () -> new Sala(null));
        assertThrows(IllegalArgumentException.class, () -> new Sala(""));
        assertThrows(IllegalArgumentException.class, () -> new Sala("minha sala"));
    }

    @Test
    void aceitaNomeComExatamenteVinteCaracteresERejeita21() {
        assertEquals("a".repeat(20), new Sala("a".repeat(20)).nome());
        assertThrows(IllegalArgumentException.class, () -> new Sala("a".repeat(21)));
    }

    @Test
    void adicionarDevolveNovaSalaSemAlterarAOriginal() {
        Sala vazia = new Sala("java");
        Sala comUma = vazia.adicionar(msg("java", "oi"));
        assertTrue(vazia.mensagens().isEmpty());
        assertEquals(1, comUma.mensagens().size());
    }

    @Test
    void rejeitaMensagemDeOutraSala() {
        Sala java = new Sala("java");
        assertThrows(IllegalArgumentException.class, () -> java.adicionar(msg("geral", "oi")));
    }

    @Test
    void rejeitaMensagemPrivada() {
        Sala geral = Sala.padrao();
        Mensagem privada = Mensagem.privada(ANA, BIA, "geral", "segredo", HORA);
        assertThrows(IllegalArgumentException.class, () -> geral.adicionar(privada));
    }

    @Test
    void ultimasDevolveAsNMaisRecentesEmOrdemCronologica() {
        Sala sala = Sala.padrao().adicionar(msg("geral", "1"))
                .adicionar(msg("geral", "2")).adicionar(msg("geral", "3"));
        List<String> textos = sala.ultimas(2).stream().map(Mensagem::conteudo).toList();
        assertEquals(List.of("2", "3"), textos);
    }

    @Test
    void ultimasComNMaiorQueOTotalDevolveTudo() {
        Sala sala = Sala.padrao().adicionar(msg("geral", "1"));
        assertEquals(1, sala.ultimas(50).size());
    }

    @Test
    void ultimasComZeroDevolveListaVazia() {
        Sala sala = Sala.padrao().adicionar(msg("geral", "1"));
        assertTrue(sala.ultimas(0).isEmpty());
    }

    @Test
    void ultimasComNegativoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> Sala.padrao().ultimas(-1));
    }

    @Test
    void listaDeMensagensEImutavel() {
        Sala sala = Sala.padrao().adicionar(msg("geral", "1"));
        assertThrows(UnsupportedOperationException.class,
                () -> sala.mensagens().add(msg("geral", "2")));
    }

    @Test
    void salasComOMesmoNomeSaoIguais() {
        assertEquals(new Sala("Java"), new Sala("java"));
        assertEquals(new Sala("Java").hashCode(), new Sala("java").hashCode());
    }
}
