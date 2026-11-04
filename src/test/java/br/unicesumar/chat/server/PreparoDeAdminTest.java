package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.infra.ConfiguracaoServidor.CredencialAdmin;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Preparação do administrador na partida do servidor: cria a conta e monta a lista de admins. */
class PreparoDeAdminTest {

    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);
    private final List<String> avisos = new ArrayList<>();

    @Test
    void comAdminConfiguradoCriaAContaEFicaSoEleComoAdministrador() {
        Administradores admins = PreparoDeAdmin.preparar(
                Optional.of(new CredencialAdmin("chefe", "segredo123")), auth, avisos::add);
        assertTrue(admins.restrito());
        assertTrue(admins.ehAdmin("chefe"));
        assertFalse(admins.ehAdmin("ana"));
        assertEquals("chefe", auth.autenticar("chefe", "segredo123").nome());
        assertTrue(avisos.stream().anyMatch(a -> a.contains("chefe") && a.contains("criada")));
    }

    @Test
    void contaQueJaExisteNaoTemASenhaTrocadaEOAvisoDiz() {
        auth.registrar("chefe", "senhaantiga");
        PreparoDeAdmin.preparar(Optional.of(new CredencialAdmin("chefe", "outrasenha")), auth, avisos::add);
        assertEquals("chefe", auth.autenticar("chefe", "senhaantiga").nome());
        assertTrue(avisos.stream().anyMatch(a -> a.contains("já existe")));
    }

    @Test
    void semAdminConfiguradoNinguemEAdminEOAvisoExplicaComoConfigurar() {
        Administradores admins = PreparoDeAdmin.preparar(Optional.empty(), auth, avisos::add);
        assertTrue(admins.restrito());
        assertFalse(admins.ehAdmin("admin"));
        assertTrue(avisos.stream().anyMatch(a -> a.contains("servidor.properties")));
    }

    @Test
    void semAutenticacaoNaoHaRestricao() {
        Administradores admins = PreparoDeAdmin.preparar(
                Optional.of(new CredencialAdmin("chefe", "segredo123")), null, avisos::add);
        assertFalse(admins.restrito());
    }

    @Test
    void avisosNuncaMostramASenha() {
        PreparoDeAdmin.preparar(Optional.of(new CredencialAdmin("chefe", "segredo123")), auth, avisos::add);
        assertTrue(avisos.stream().noneMatch(a -> a.contains("segredo123")));
    }

    @Test
    void senhaInvalidaDaConfiguracaoSobeComMensagemQueDizOQueFazer() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PreparoDeAdmin.preparar(
                Optional.of(new CredencialAdmin("chefe", "123")), auth, avisos::add));
        assertTrue(e.getMessage().contains("senha"));
    }
}
