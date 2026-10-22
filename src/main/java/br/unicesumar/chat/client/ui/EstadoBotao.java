package br.unicesumar.chat.client.ui;

/** Os três estados padronizados de um botão da interface. */
public enum EstadoBotao {
    /** Pronto para clicar. */
    ATIVO,
    /** Indisponível agora (texto original mantido). */
    DESABILITADO,
    /** Uma operação está em andamento: desligado e com o texto "Carregando...". */
    CARREGANDO
}
