package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

/** Criação da conta do administrador na partida do servidor. */
class AuthServiceGarantirContaTest {

    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);

    @Test
    void criaAContaQuandoNaoExisteEPermiteEntrarComASenha() {
        assertTrue(auth.garantirConta("chefe", "segredo123"));
        assertEquals("chefe", auth.autenticar("chefe", "segredo123").nome());
    }

    @Test
    void contaQueJaExisteNaoTemASenhaAlterada() {
        auth.registrar("chefe", "senhaantiga");
        assertFalse(auth.garantirConta("chefe", "outrasenha"));
        assertEquals("chefe", auth.autenticar("chefe", "senhaantiga").nome());
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("chefe", "outrasenha"));
    }

    @Test
    void naoDiferenciaMaiusculasAoVerSeJaExiste() {
        auth.registrar("Chefe", "senhaantiga");
        assertFalse(auth.garantirConta("chefe", "outrasenha"));
    }

    @Test
    void senhaCurtaDemaisEhRecusadaComMensagemQueDizOQueFazer() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.garantirConta("chefe", "123"));
        assertTrue(e.getMessage().contains("senha"));
    }
}
