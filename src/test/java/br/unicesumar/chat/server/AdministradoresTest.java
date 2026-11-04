package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Quem é administrador: lista por nome (sem diferenciar maiúsculas) ou sem restrição. */
class AdministradoresTest {

    @Test
    void nomeNaListaEAdministradorSemDiferenciarMaiusculas() {
        Administradores admins = Administradores.de(List.of("Chefe"));
        assertTrue(admins.ehAdmin("chefe"));
        assertTrue(admins.ehAdmin("CHEFE"));
        assertFalse(admins.ehAdmin("ana"));
    }

    @Test
    void listaVaziaNaoTemAdministradorEContinuaRestrita() {
        Administradores admins = Administradores.nenhum();
        assertTrue(admins.restrito());
        assertFalse(admins.ehAdmin("ana"));
        assertEquals(admins.restrito(), Administradores.de(List.of()).restrito());
        assertFalse(Administradores.de(List.of()).ehAdmin("ana"));
    }

    @Test
    void semRestricaoLiberaTodoMundoENaoEhRestrita() {
        Administradores admins = Administradores.semRestricao();
        assertFalse(admins.restrito());
        assertTrue(admins.ehAdmin("qualquer"));
    }

    @Test
    void nomeNuloOuEmBrancoNuncaEAdministradorQuandoRestrita() {
        Administradores admins = Administradores.de(List.of("chefe"));
        assertFalse(admins.ehAdmin(null));
        assertFalse(admins.ehAdmin("  "));
    }

    @Test
    void listaNulaEElementoNuloSaoRejeitados() {
        assertThrows(NullPointerException.class, () -> Administradores.de(null));
        assertThrows(NullPointerException.class, () -> Administradores.de(java.util.Arrays.asList("a", null)));
    }

    @Test
    void alterarAListaOriginalDepoisNaoMudaQuemEAdmin() {
        List<String> origem = new java.util.ArrayList<>(List.of("chefe"));
        Administradores admins = Administradores.de(origem);
        origem.add("intruso");
        assertFalse(admins.ehAdmin("intruso"));
    }
}
