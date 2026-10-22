package br.unicesumar.chat.protocol;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos de aviso que o servidor envia e os clientes precisam reconhecer. Ficam num
 * só lugar, com quem constrói o texto ao lado de quem o lê, para um não mudar sem o
 * outro. Nomes de usuário e de sala não têm espaços, então os padrões são seguros.
 */
public final class Mensagens {

    /** Aviso quando uma busca não acha nada. */
    public static final String SEM_RESULTADO_DE_BUSCA = "Nenhuma mensagem encontrada.";
    /** Aviso quando {@code /history} não tem mensagens a mostrar. */
    public static final String SEM_HISTORICO = "Não há mensagens anteriores nesta sala.";

    private static final Pattern ENTRADA = Pattern.compile("Você entrou como (\\S+) na sala (\\S+)\\.");
    private static final Pattern MUDANCA_DE_SALA = Pattern.compile("Você entrou na sala (\\S+)\\.");
    private static final Pattern PRESENCA = Pattern.compile("\\S+ (?:entrou na|saiu da) sala \\S+\\.");

    /**
     * Quem entrou e em qual sala.
     *
     * @param nome nome do usuário, com as maiúsculas do cadastro
     * @param sala sala em que ele está
     */
    public record Entrada(String nome, String sala) {
    }

    private Mensagens() {
    }

    /** Confirmação enviada ao próprio usuário ao entrar no chat. */
    public static String entrouComo(String nome, String sala) {
        return "Você entrou como " + nome + " na sala " + sala + ".";
    }

    /** Lê a confirmação de {@link #entrouComo}; vazio para qualquer outro texto. */
    public static Optional<Entrada> lerEntrada(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher m = ENTRADA.matcher(texto);
        return m.matches() ? Optional.of(new Entrada(m.group(1), m.group(2))) : Optional.empty();
    }

    /** Confirmação enviada ao próprio usuário ao trocar de sala. */
    public static String entrouNaSala(String sala) {
        return "Você entrou na sala " + sala + ".";
    }

    /** Lê a confirmação de {@link #entrouNaSala}; vazio para qualquer outro texto. */
    public static Optional<String> lerMudancaDeSala(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        Matcher m = MUDANCA_DE_SALA.matcher(texto);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Aviso aos demais da sala de que alguém entrou. */
    public static String avisoDeEntrada(String nome, String sala) {
        return nome + " entrou na sala " + sala + ".";
    }

    /** Aviso aos demais da sala de que alguém saiu. */
    public static String avisoDeSaida(String nome, String sala) {
        return nome + " saiu da sala " + sala + ".";
    }

    /** Indica se o texto é um aviso de entrada ou saída (a lista de online mudou). */
    public static boolean ehAvisoDePresenca(String texto) {
        return texto != null && PRESENCA.matcher(texto).matches();
    }
}
