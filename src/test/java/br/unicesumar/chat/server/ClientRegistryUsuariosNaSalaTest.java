package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClientRegistryUsuariosNaSalaTest {

    private final ClientRegistry registry = new ClientRegistry();

    @Test
    void registroVazioNaoTemUsuariosNaSala() {
        assertTrue(registry.usuariosNaSala("geral").isEmpty());
    }

    @Test
    void devolveSoOsIdentificadosDaSalaEmOrdemAlfabeticaSemDiferenciarMaiusculas() {
        ClienteFalso.identificado(registry, "Zeca");
        ClienteFalso.identificado(registry, "ana");
        ClienteFalso.identificado(registry, "Bia");
        registry.adicionar(ClienteFalso.novo().handler);
        ClienteFalso java = ClienteFalso.identificado(registry, "caio");
        java.handler.definirSala("java");
        assertEquals(List.of("ana", "Bia", "Zeca"), registry.usuariosNaSala("geral"));
        assertEquals(List.of("caio"), registry.usuariosNaSala("java"));
    }

    @Test
    void listaDevolvidaEImutavelEUmaCopia() {
        ClienteFalso.identificado(registry, "ana");
        List<String> copia = registry.usuariosNaSala("geral");
        ClienteFalso.identificado(registry, "bia");
        assertEquals(List.of("ana"), copia);
        assertThrows(UnsupportedOperationException.class, () -> copia.add("x"));
    }
}
