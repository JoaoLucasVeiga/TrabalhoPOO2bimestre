package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exceção não engolida: hash guardado fora do formato precisa aparecer no log, sem segredos. */
class AuthServiceLogTest {

    private final List<LogRecord> registros = Collections.synchronizedList(new ArrayList<>());
    private final Logger logger = Logger.getLogger(AuthService.class.getName());
    private final Handler captura = new Handler() {
        @Override
        public void publish(LogRecord registro) {
            registros.add(registro);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void ligar() {
        logger.addHandler(captura);
    }

    @AfterEach
    void desligar() {
        logger.removeHandler(captura);
    }

    @Test
    void hashMalFormadoGeraAvisoNoLogComONomeDoUsuarioMasSemSenhaNemHash() {
        UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
        usuarios.cadastrar(new CredencialUsuario(new Usuario("ana"), "isso-nao-e-um-hash", "c2FsdA=="));
        AuthService auth = new AuthService(usuarios, 1000);

        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "senha-digitada-123"));

        List<LogRecord> avisos = registros.stream().filter(r -> r.getLevel() == Level.WARNING).toList();
        assertEquals(1, avisos.size());
        String texto = avisos.get(0).getMessage() + " " + (avisos.get(0).getParameters() == null ? ""
                : java.util.Arrays.toString(avisos.get(0).getParameters()));
        assertTrue(texto.contains("ana"), texto);
        assertFalse(texto.contains("senha-digitada-123"), texto);
        assertFalse(texto.contains("isso-nao-e-um-hash"), texto);
    }

    @Test
    void loginNormalNaoGeraAvisoNenhum() {
        UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
        AuthService auth = new AuthService(usuarios, 1000);
        auth.registrar("ana", "minhasenha");
        auth.autenticar("ana", "minhasenha");
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "errada123"));
        assertTrue(registros.stream().noneMatch(r -> r.getLevel() == Level.WARNING));
    }
}
