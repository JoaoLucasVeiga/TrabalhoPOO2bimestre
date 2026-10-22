package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import java.time.LocalDateTime;
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

    /**
     * Busca mensagens públicas de um remetente na sala (nome exato, sem diferenciar
     * maiúsculas). Privadas nunca são devolvidas.
     *
     * @param limite máximo de resultados (pelo menos 1); vêm as mais recentes
     * @return da mais antiga para a mais recente
     * @throws IllegalArgumentException se algum argumento for inválido
     * @throws UnsupportedOperationException se a implementação não suporta busca
     */
    default List<Mensagem> buscarPorUsuario(String sala, String remetente, int limite) {
        throw new UnsupportedOperationException("Esta implementação não suporta busca por usuário.");
    }

    /**
     * Busca mensagens públicas da sala cujo conteúdo contém o trecho. O trecho é
     * literal ({@code %} e {@code _} não são curingas) e a comparação ignora
     * maiúsculas pelo menos para letras ASCII. Privadas nunca são devolvidas.
     *
     * @param limite máximo de resultados (pelo menos 1); vêm as mais recentes
     * @return da mais antiga para a mais recente
     * @throws IllegalArgumentException se algum argumento for inválido
     * @throws UnsupportedOperationException se a implementação não suporta busca
     */
    default List<Mensagem> buscarPorTexto(String sala, String trecho, int limite) {
        throw new UnsupportedOperationException("Esta implementação não suporta busca por texto.");
    }

    /**
     * Busca mensagens públicas da sala enviadas entre dois instantes, ambos incluídos.
     * Privadas nunca são devolvidas.
     *
     * @param limite máximo de resultados (pelo menos 1); vêm as mais recentes
     * @return da mais antiga para a mais recente
     * @throws IllegalArgumentException se algum argumento for inválido ou o início vier depois do fim
     * @throws UnsupportedOperationException se a implementação não suporta busca
     */
    default List<Mensagem> buscarPorPeriodo(String sala, LocalDateTime inicio, LocalDateTime fim,
            int limite) {
        throw new UnsupportedOperationException("Esta implementação não suporta busca por período.");
    }
}
