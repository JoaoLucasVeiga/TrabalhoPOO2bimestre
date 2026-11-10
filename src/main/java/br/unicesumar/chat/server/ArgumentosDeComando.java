package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Funções de apoio para ler o que vem depois do nome do comando. Não guarda estado. */
final class ArgumentosDeComando {

    private static final Pattern QUANTIDADE = Pattern.compile("\\d{1,4}");

    private ArgumentosDeComando() {
    }

    /** Posição do primeiro espaço (ou outra quebra) no texto, ou -1 se não houver. */
    static int indiceDoEspaco(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (Character.isWhitespace(texto.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Exige exatamente uma palavra (por exemplo, um nome de usuário).
     *
     * @throws IllegalArgumentException com "Uso: ..." se estiver vazio ou tiver mais de uma palavra
     */
    static String umNome(String argumentos, String uso) {
        if (argumentos.isEmpty() || indiceDoEspaco(argumentos) >= 0) {
            throw new IllegalArgumentException("Uso: " + uso);
        }
        return argumentos;
    }

    /** Nome de quem enviou o comando, ou {@code null} se ainda não entrou. */
    static String nomeDe(ClienteHandler origem) {
        return origem.usuario().map(Usuario::nome).orElse(null);
    }

    /**
     * Nome de quem enviou o comando.
     *
     * @throws IllegalArgumentException se ainda não entrou no chat
     */
    static String nomeObrigatorio(ClienteHandler origem) {
        String nome = nomeDe(origem);
        if (nome == null) {
            throw new IllegalArgumentException("Entre no chat antes de ver as suas conversas.");
        }
        return nome;
    }

    /**
     * Lê uma quantidade de 1 até o máximo.
     *
     * @param uso devolve o erro a lançar quando o texto não é uma quantidade válida
     */
    static int quantidade(String texto, int maximo, Supplier<IllegalArgumentException> uso) {
        if (!QUANTIDADE.matcher(texto).matches()) {
            throw uso.get();
        }
        int valor = Integer.parseInt(texto);
        if (valor < 1 || valor > maximo) {
            throw uso.get();
        }
        return valor;
    }
}
