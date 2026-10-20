package br.unicesumar.chat.protocol;

import br.unicesumar.chat.protocol.Evento.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Protocolo de texto do chat: uma linha por evento, campos separados por
 * {@code |}. Dentro dos campos, {@code \} vira {@code \\}, {@code |} vira
 * {@code \p}, e as quebras de linha viram {@code \n} e {@code \r}. Assim uma
 * linha codificada nunca contém delimitador nem quebra de linha crus.
 * Não conhece rede: só converte entre {@link Evento} e texto.
 */
public final class Protocolo {

    private static final char DELIMITADOR = '|';
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int CAMPOS_POR_MSG = 4;

    private Protocolo() {
    }

    /** Converte um evento do servidor em uma linha (sem a quebra de linha final). */
    public static String codificar(Evento evento) {
        Objects.requireNonNull(evento, "evento");
        return switch (evento) {
            case Msg m -> linha("MSG", m.sala(), m.remetente(), m.horario().format(ISO), m.texto());
            case Privada p -> linha("PRIVADA", p.de(), p.para(), p.horario().format(ISO), p.texto());
            case Sistema s -> linha("SISTEMA", s.aviso());
            case Usuarios u -> {
                List<String> campos = new ArrayList<>();
                campos.add(u.sala());
                campos.addAll(u.nomes());
                yield linha("USUARIOS", campos.toArray(String[]::new));
            }
            case Salas s -> linha("SALAS", s.nomes().toArray(String[]::new));
            case Historico h -> {
                List<String> campos = new ArrayList<>();
                for (Msg m : h.mensagens()) {
                    campos.add(m.sala());
                    campos.add(m.remetente());
                    campos.add(m.horario().format(ISO));
                    campos.add(m.texto());
                }
                yield linha("HISTORICO", campos.toArray(String[]::new));
            }
            case Ok o -> linha("OK", o.texto());
            case Erro e -> linha("ERRO", e.texto());
        };
    }

    /**
     * Converte uma linha recebida do servidor em evento.
     *
     * @throws ProtocoloException se a linha estiver fora do formato
     */
    public static Evento decodificar(String linha) {
        if (linha == null || linha.isEmpty()) {
            throw new ProtocoloException("Linha vazia recebida do servidor.");
        }
        if (linha.indexOf('\n') >= 0 || linha.indexOf('\r') >= 0) {
            throw new ProtocoloException("A linha contém quebra de linha. Envie um evento por linha.");
        }
        String[] partes = linha.split("\\|", -1);
        String tipo = partes[0];
        List<String> campos = new ArrayList<>();
        for (int i = 1; i < partes.length; i++) {
            campos.add(desescapar(partes[i]));
        }
        return switch (tipo) {
            case "MSG" -> {
                exigir(tipo, campos, CAMPOS_POR_MSG);
                yield msg(campos, 0);
            }
            case "PRIVADA" -> {
                exigir(tipo, campos, CAMPOS_POR_MSG);
                yield new Privada(campos.get(0), campos.get(1), horario(campos.get(2)), campos.get(3));
            }
            case "SISTEMA" -> {
                exigir(tipo, campos, 1);
                yield new Sistema(campos.get(0));
            }
            case "USUARIOS" -> {
                if (campos.isEmpty()) {
                    throw new ProtocoloException("Evento USUARIOS sem o nome da sala.");
                }
                yield new Usuarios(campos.get(0), campos.subList(1, campos.size()));
            }
            case "SALAS" -> new Salas(campos);
            case "HISTORICO" -> {
                if (campos.size() % CAMPOS_POR_MSG != 0) {
                    throw new ProtocoloException("Evento HISTORICO com " + campos.size()
                            + " campos; o esperado é múltiplo de " + CAMPOS_POR_MSG + ".");
                }
                List<Msg> mensagens = new ArrayList<>();
                for (int i = 0; i < campos.size(); i += CAMPOS_POR_MSG) {
                    mensagens.add(msg(campos, i));
                }
                yield new Historico(mensagens);
            }
            case "OK" -> {
                exigir(tipo, campos, 1);
                yield new Ok(campos.get(0));
            }
            case "ERRO" -> {
                exigir(tipo, campos, 1);
                yield new Erro(campos.get(0));
            }
            default -> throw new ProtocoloException("Tipo de evento desconhecido: '" + tipo
                    + "'. Verifique se o cliente e o servidor estão na mesma versão.");
        };
    }

    /** Codifica o que o usuário digitou (mensagem ou comando) em uma linha segura de enviar. */
    public static String codificarEntrada(String texto) {
        return escapar(Objects.requireNonNull(texto, "texto"));
    }

    /**
     * Desfaz {@link #codificarEntrada(String)} no servidor.
     *
     * @throws ProtocoloException se a linha for nula ou tiver escape inválido
     */
    public static String decodificarEntrada(String linha) {
        if (linha == null) {
            throw new ProtocoloException("Linha nula recebida do cliente.");
        }
        return desescapar(linha);
    }

    private static Msg msg(List<String> campos, int inicio) {
        return new Msg(campos.get(inicio), campos.get(inicio + 1),
                horario(campos.get(inicio + 2)), campos.get(inicio + 3));
    }

    private static void exigir(String tipo, List<String> campos, int esperado) {
        if (campos.size() != esperado) {
            throw new ProtocoloException("Evento " + tipo + " com " + campos.size()
                    + " campos; o esperado é " + esperado + ".");
        }
    }

    private static LocalDateTime horario(String texto) {
        try {
            return LocalDateTime.parse(texto, ISO);
        } catch (DateTimeParseException e) {
            throw new ProtocoloException("Horário inválido: '" + texto
                    + "'. Use o formato ISO-8601, ex.: 2026-10-01T09:05:30.");
        }
    }

    private static String linha(String tipo, String... campos) {
        StringBuilder sb = new StringBuilder(tipo);
        for (String campo : campos) {
            sb.append(DELIMITADOR).append(escapar(campo));
        }
        return sb.toString();
    }

    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 8);
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case DELIMITADOR -> sb.append("\\p");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String desescapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i + 1 >= texto.length()) {
                throw new ProtocoloException("Escape incompleto no fim da linha.");
            }
            char proximo = texto.charAt(++i);
            switch (proximo) {
                case '\\' -> sb.append('\\');
                case 'p' -> sb.append(DELIMITADOR);
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                default -> throw new ProtocoloException("Escape inválido: '\\" + proximo + "'.");
            }
        }
        return sb.toString();
    }
}
