package br.unicesumar.chat.protocol;

/** Linha que não segue o protocolo de texto do chat. */
public class ProtocoloException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Cria a exceção com a descrição do problema. */
    public ProtocoloException(String mensagem) {
        super(mensagem);
    }
}
