package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtocoloTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    private static Evento idaEVolta(Evento e) {
        return Protocolo.decodificar(Protocolo.codificar(e));
    }

    // ---------- formato ----------

    @Test
    void codificaSistemaComTipoEDelimitador() {
        assertEquals("SISTEMA|ana entrou", Protocolo.codificar(new Sistema("ana entrou")));
    }

    @Test
    void codificaMsgComOsQuatroCampos() {
        assertEquals("MSG|geral|ana|2026-10-01T09:05:30|oi",
                Protocolo.codificar(new Msg("geral", "ana", HORA, "oi")));
    }

    @Test
    void codificaListasSemCamposExtrasQuandoVazias() {
        assertEquals("SALAS", Protocolo.codificar(new Salas(List.of())));
        assertEquals("USUARIOS|geral", Protocolo.codificar(new Usuarios("geral", List.of())));
        assertEquals("HISTORICO", Protocolo.codificar(new Historico(List.of())));
    }

    @Test
    void linhaCodificadaNuncaTemQuebraDeLinhaCrua() {
        String linha = Protocolo.codificar(new Msg("geral", "ana", HORA, "a\nb\r\nc"));
        assertFalse(linha.contains("\n"));
        assertFalse(linha.contains("\r"));
    }

    @Test
    void delimitadorNoTextoVirgaEscape() {
        assertEquals("SISTEMA|a\\pb", Protocolo.codificar(new Sistema("a|b")));
    }

    // ---------- ida e volta ----------

    @Test
    void idaEVoltaMsg() {
        Evento e = new Msg("geral", "ana", HORA, "olá, mundo");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaPrivada() {
        Evento e = new Privada("ana", "bia", HORA, "psiu");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaSistemaOkEErro() {
        assertEquals(new Sistema("aviso"), idaEVolta(new Sistema("aviso")));
        assertEquals(new Ok("feito"), idaEVolta(new Ok("feito")));
        assertEquals(new Erro("falhou; tente de novo"), idaEVolta(new Erro("falhou; tente de novo")));
    }

    @Test
    void idaEVoltaUsuariosESalas() {
        assertEquals(new Usuarios("java", List.of("ana", "bia")),
                idaEVolta(new Usuarios("java", List.of("ana", "bia"))));
        assertEquals(new Salas(List.of("geral", "java")), idaEVolta(new Salas(List.of("geral", "java"))));
    }

    @Test
    void idaEVoltaListasVazias() {
        assertEquals(new Salas(List.of()), idaEVolta(new Salas(List.of())));
        assertEquals(new Usuarios("geral", List.of()), idaEVolta(new Usuarios("geral", List.of())));
        assertEquals(new Historico(List.of()), idaEVolta(new Historico(List.of())));
    }

    @Test
    void idaEVoltaHistoricoComVariasMensagens() {
        Historico h = new Historico(List.of(
                new Msg("geral", "ana", HORA, "um"),
                new Msg("geral", "bia", HORA.plusMinutes(1), "dois | com delimitador"),
                new Msg("geral", "ana", HORA.plusMinutes(2), "três\ncom quebra")));
        assertEquals(h, idaEVolta(h));
    }

    @Test
    void idaEVoltaTextoComDelimitador() {
        Evento e = new Msg("geral", "ana", HORA, "a|b||c|");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaTextoComBarraInvertida() {
        Evento e = new Msg("geral", "ana", HORA, "C:\\pasta\\novo\\\\fim\\");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaTextoComAcentosEEmoji() {
        Evento e = new Msg("geral", "ana", HORA, "ação, coração, ñ, 日本語 😀");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaTextoComQuebrasDeLinhaELinhasVazias() {
        Evento e = new Msg("geral", "ana", HORA, "l1\n\nl3\r\nl4\r");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaTextoVazio() {
        assertEquals(new Sistema(""), idaEVolta(new Sistema("")));
    }

    @Test
    void idaEVoltaTextoQueParecaSequenciaDeEscape() {
        Evento e = new Sistema("\\n \\p \\\\ literal");
        assertEquals(e, idaEVolta(e));
    }

    @Test
    void idaEVoltaTextoComTipoDeEventoDentro() {
        Evento e = new Sistema("MSG|geral|ana|x|y");
        assertEquals(e, idaEVolta(e));
    }

    // ---------- entrada do cliente ----------

    @Test
    void entradaDoClienteIdaEVoltaComTudoQueEspecial() {
        String texto = "a|b\nc\\d\r\nação";
        assertEquals(texto, Protocolo.decodificarEntrada(Protocolo.codificarEntrada(texto)));
    }

    @Test
    void entradaCodificadaNaoTemQuebraDeLinhaCrua() {
        assertFalse(Protocolo.codificarEntrada("a\nb").contains("\n"));
    }

    @Test
    void comandoSimplesNaoMudaNaCodificacao() {
        assertEquals("/msg ana oi", Protocolo.codificarEntrada("/msg ana oi"));
    }

    @Test
    void entradaComEscapeInvalidoLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificarEntrada("a\\xb"));
    }

    @Test
    void entradaNulaLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificarEntrada(null));
    }

    // ---------- erros de decodificação ----------

    @Test
    void linhaNulaOuVaziaLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar(null));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar(""));
    }

    @Test
    void tipoDesconhecidoLancaExcecao() {
        ProtocoloException e = assertThrows(ProtocoloException.class,
                () -> Protocolo.decodificar("XYZ|a"));
        assertTrue(e.getMessage().contains("XYZ"));
    }

    @Test
    void tipoEmMinusculasEDesconhecido() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("sistema|a"));
    }

    @Test
    void camposAMenosLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("MSG|geral|ana"));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("SISTEMA"));
    }

    @Test
    void camposAMaisLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("SISTEMA|a|b"));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("OK|a|b"));
    }

    @Test
    void horarioInvalidoLancaExcecao() {
        assertThrows(ProtocoloException.class,
                () -> Protocolo.decodificar("MSG|geral|ana|ontem|oi"));
    }

    @Test
    void historicoComCamposNaoMultiplosDeQuatroLancaExcecao() {
        assertThrows(ProtocoloException.class,
                () -> Protocolo.decodificar("HISTORICO|geral|ana|2026-10-01T09:05:30"));
    }

    @Test
    void escapeInvalidoLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("SISTEMA|a\\xb"));
    }

    @Test
    void barraInvertidaSolteiraNoFimLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("SISTEMA|abc\\"));
    }

    @Test
    void linhaComQuebraDeLinhaCruaLancaExcecao() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("SISTEMA|a\nb"));
    }

    @Test
    void mensagemDeErroDoProtocoloDizOQueFazer() {
        ProtocoloException e = assertThrows(ProtocoloException.class,
                () -> Protocolo.decodificar("XYZ|a"));
        assertFalse(e.getMessage().isBlank());
    }

    @Test
    void codificarEventoNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> Protocolo.codificar(null));
    }
}
