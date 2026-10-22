package br.unicesumar.chat.client.controller;

/**
 * Resultado de uma tentativa de entrar no chat.
 *
 * @param sucesso se entrou
 * @param usuario nome com as maiúsculas do cadastro (só quando deu certo)
 * @param erro    o que aconteceu e o que fazer (só quando falhou)
 */
public record ResultadoConexao(boolean sucesso, String usuario, String erro) {

    /** Entrada bem-sucedida. */
    public static ResultadoConexao ok(String usuario) {
        return new ResultadoConexao(true, usuario, null);
    }

    /** Falha, com a mensagem para o usuário. */
    public static ResultadoConexao falha(String erro) {
        return new ResultadoConexao(false, null, erro);
    }
}
