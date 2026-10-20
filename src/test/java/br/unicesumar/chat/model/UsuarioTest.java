package br.unicesumar.chat.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void criaUsuarioComNomeValido() {
        assertEquals("ana_01", new Usuario("ana_01").nome());
    }

    @Test
    void preservaAsMaiusculasDoNomeInformado() {
        assertEquals("Ana", new Usuario("Ana").nome());
    }

    @Test
    void rejeitaNomeNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(null));
    }

    @Test
    void rejeitaNomeVazio() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(""));
    }

    @Test
    void rejeitaNomeSoComEspacos() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("   "));
    }

    @Test
    void rejeitaNomeComDoisCaracteres() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("ab"));
    }

    @Test
    void aceitaNomeComExatamenteTresCaracteres() {
        assertEquals("abc", new Usuario("abc").nome());
    }

    @Test
    void aceitaNomeComExatamenteVinteCaracteres() {
        String nome = "a".repeat(20);
        assertEquals(nome, new Usuario(nome).nome());
    }

    @Test
    void rejeitaNomeComVinteEUmCaracteres() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a".repeat(21)));
    }

    @Test
    void rejeitaNomeComEspacoNoMeio() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("ana maria"));
    }

    @Test
    void rejeitaNomeComEspacoNasPontas() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(" ana"));
    }

    @Test
    void rejeitaNomeComAcentoOuSimbolo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("joão"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("ana-maria"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("ana|bia"));
    }

    @Test
    void mensagemDeErroDizOQueAconteceuEOQueFazer() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new Usuario("a"));
        assertTrue(e.getMessage().contains("3"));
        assertTrue(e.getMessage().contains("20"));
    }

    @Test
    void igualdadeIgnoraMaiusculasEMinusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ANA"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("aNa").hashCode());
    }

    @Test
    void usuariosComNomesDiferentesSaoDiferentes() {
        assertNotEquals(new Usuario("ana"), new Usuario("bia"));
    }

    @Test
    void usuarioNaoEIgualANuloNemAOutroTipo() {
        Usuario ana = new Usuario("ana");
        assertNotEquals(null, ana);
        assertNotEquals("ana", ana);
    }

    @Test
    void toStringDevolveONome() {
        assertEquals("Ana", new Usuario("Ana").toString());
    }
}
