package br.unicesumar.chat.client.net;

import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;

/**
 * Porta do cliente para falar com o servidor (usada pelo terminal e pela
 * interface gráfica). Os adaptadores são {@link ConexaoSocket} (real) e
 * {@code ConexaoFalsa} (testes). Quem usa não conhece sockets nem o protocolo de texto.
 */
public interface ConexaoChat {

    /**
     * Abre a conexão.
     *
     * @throws IOException com mensagem que diz o que houve e o que fazer
     */
    void abrir() throws IOException;

    /**
     * Envia o que o usuário digitou (mensagem ou comando).
     *
     * @throws IOException se a conexão estiver fechada ou a escrita falhar
     */
    void enviar(String texto) throws IOException;

    /**
     * Espera o próximo evento do servidor (bloqueia).
     *
     * @return o evento, ou {@code null} quando a conexão terminou (pelo servidor ou por {@link #fechar()})
     * @throws IOException se a leitura falhar
     * @throws br.unicesumar.chat.protocol.ProtocoloException se a linha estiver fora do protocolo
     */
    Evento receber() throws IOException;

    /** Fecha a conexão e destrava quem está em {@link #receber()}. Pode ser chamado várias vezes. */
    void fechar();

    /** Indica se a conexão está aberta. */
    boolean aberta();
}
