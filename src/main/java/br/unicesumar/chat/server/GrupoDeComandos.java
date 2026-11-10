package br.unicesumar.chat.server;

/** Conjunto de comandos de um mesmo assunto (conta, sala, conversa...). Cada grupo registra os seus. */
interface GrupoDeComandos {

    /** Coloca os comandos do grupo na tabela. */
    void registrarEm(TabelaDeComandos tabela);
}
