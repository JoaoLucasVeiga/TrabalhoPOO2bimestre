package br.unicesumar.chat.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CredencialUsuarioTest {

    private static final Usuario ANA = new Usuario("ana");

    @Test
    void guardaUsuarioHashESalt() {
        CredencialUsuario c = new CredencialUsuario(ANA, "hash", "salt");
        assertEquals(ANA, c.usuario());
        assertEquals("hash", c.senhaHash());
        assertEquals("salt", c.salt());
    }

    @Test
    void rejeitaUsuarioNulo() {
        assertThrows(IllegalArgumentException.class, () -> new CredencialUsuario(null, "h", "s"));
    }

    @Test
    void rejeitaHashNuloOuVazio() {
        assertThrows(IllegalArgumentException.class, () -> new CredencialUsuario(ANA, null, "s"));
        assertThrows(IllegalArgumentException.class, () -> new CredencialUsuario(ANA, " ", "s"));
    }

    @Test
    void rejeitaSaltNuloOuVazio() {
        assertThrows(IllegalArgumentException.class, () -> new CredencialUsuario(ANA, "h", null));
        assertThrows(IllegalArgumentException.class, () -> new CredencialUsuario(ANA, "h", ""));
    }
}
