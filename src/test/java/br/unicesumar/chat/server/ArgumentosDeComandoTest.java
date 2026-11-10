package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Funções de apoio para ler os argumentos digitados depois do nome do comando. */
class ArgumentosDeComandoTest {

    @Test
    void indiceDoEspacoAcharAPrimeiraQuebraOuMenosUm() {
        assertEquals(3, ArgumentosDeComando.indiceDoEspaco("ana oi"));
        assertEquals(3, ArgumentosDeComando.indiceDoEspaco("ana\toi"));
        assertEquals(-1, ArgumentosDeComando.indiceDoEspaco("ana"));
        assertEquals(-1, ArgumentosDeComando.indiceDoEspaco(""));
    }

    @Test
    void umNomeAceitaUmaPalavraEExplicaOUsoNosOutrosCasos() {
        assertEquals("ana", ArgumentosDeComando.umNome("ana", "/ban usuario"));
        IllegalArgumentException vazio = assertThrows(IllegalArgumentException.class,
                () -> ArgumentosDeComando.umNome("", "/ban usuario"));
        assertEquals("Uso: /ban usuario", vazio.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ArgumentosDeComando.umNome("ana bia", "/ban usuario"));
    }

    @Test
    void nomeDeEOnomeDoUsuarioIdentificadoOuNulo() {
        ClientRegistry registry = new ClientRegistry();
        ClienteFalso ana = ClienteFalso.identificado(registry, "ana");
        assertEquals("ana", ArgumentosDeComando.nomeDe(ana.handler));
        assertNull(ArgumentosDeComando.nomeDe(ClienteFalso.novo().handler));
    }

    @Test
    void nomeObrigatorioRecusaQuemAindaNaoEntrou() {
        ClienteFalso anonimo = ClienteFalso.novo();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ArgumentosDeComando.nomeObrigatorio(anonimo.handler));
        assertTrue(e.getMessage().contains("Entre no chat"));
    }

    @Test
    void quantidadeAceitaDeUmAoMaximoERecusaOResto() {
        assertEquals(10, ArgumentosDeComando.quantidade("10", 100, () -> new IllegalArgumentException("uso")));
        assertEquals(100, ArgumentosDeComando.quantidade("100", 100, () -> new IllegalArgumentException("uso")));
        for (String ruim : new String[] {"0", "101", "abc", "-1", "", "12345", "1.5"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ArgumentosDeComando.quantidade(ruim, 100, () -> new IllegalArgumentException("uso")), ruim);
            assertEquals("uso", e.getMessage());
        }
    }
}
