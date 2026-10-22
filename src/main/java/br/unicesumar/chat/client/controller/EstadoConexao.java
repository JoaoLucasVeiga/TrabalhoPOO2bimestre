package br.unicesumar.chat.client.controller;

/** Estado da ligação com o servidor, como a tela precisa mostrar. */
public enum EstadoConexao {
    /** Abrindo a conexão ou entrando. */
    CONECTANDO,
    /** Conectado e identificado. */
    CONECTADO,
    /** Sem conexão (saiu, caiu ou o servidor encerrou). */
    DESCONECTADO
}
