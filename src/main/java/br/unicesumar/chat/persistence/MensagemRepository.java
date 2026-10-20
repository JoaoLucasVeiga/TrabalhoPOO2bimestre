package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import java.util.List;

/** Contrato de persistência das mensagens. Não conhece JDBC nem rede. */
public interface MensagemRepository {

    /**
     * Salva a mensagem.
     *
     * @return a mesma mensagem, já com o id gerado
     * @throws RepositorioException se a gravação falhar
     */
    Mensagem salvar(Mensagem mensagem);

    /**
     * Busca as últimas mensagens públicas de uma sala. Mensagens privadas nunca
     * são devolvidas.
     *
     * @param sala       nome da sala
     * @param quantidade máximo de mensagens; zero devolve lista vazia
     * @return da mais antiga para a mais recente
     * @throws IllegalArgumentException se a quantidade for negativa
     */
    List<Mensagem> buscarUltimas(String sala, int quantidade);
}
