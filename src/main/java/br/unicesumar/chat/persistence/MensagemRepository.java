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
     * Salva várias mensagens de uma vez, na ordem recebida. Implementações com banco devem gravar tudo
     * numa transação só (ou tudo, ou nada); a padrão salva uma a uma.
     *
     * @return as mesmas mensagens, já com id, na mesma ordem
     * @throws NullPointerException se a lista ou alguma mensagem for nula (nada é gravado)
     * @throws RepositorioException se a gravação falhar
     */
    default List<Mensagem> salvarTodas(List<Mensagem> mensagens) {
        java.util.Objects.requireNonNull(mensagens, "mensagens");
        mensagens.forEach(m -> java.util.Objects.requireNonNull(m, "mensagem"));
        List<Mensagem> salvas = new java.util.ArrayList<>(mensagens.size());
        for (Mensagem m : mensagens) {
            salvas.add(salvar(m));
        }
        return salvas;
    }

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

    /**
     * Apaga as mensagens públicas de uma sala. Mensagens privadas não são apagadas.
     *
     * @return quantas mensagens foram apagadas
     * @throws IllegalArgumentException se a sala for nula ou em branco
     * @throws RepositorioException se a operação falhar
     * @throws UnsupportedOperationException se a implementação não suporta apagar
     */
    default int apagarSala(String sala) {
        throw new UnsupportedOperationException("Esta implementação não suporta apagar o histórico.");
    }

    /**
     * Busca as últimas mensagens privadas trocadas entre dois usuários, nos dois sentidos
     * (nomes sem diferenciar maiúsculas). Mensagens públicas e conversas com terceiros nunca vêm.
     *
     * @param quantidade máximo de mensagens; zero devolve lista vazia
     * @return da mais antiga para a mais recente
     * @throws IllegalArgumentException se algum nome for nulo ou em branco, ou a quantidade for negativa
     * @throws UnsupportedOperationException se a implementação não suporta conversas privadas
     */
    default List<Mensagem> buscarConversa(String a, String b, int quantidade) {
        throw new UnsupportedOperationException("Esta implementação não suporta conversas privadas.");
    }

    /**
     * Lista com quem o usuário já trocou mensagens privadas, da conversa mais recente para a mais antiga.
     * Cada interlocutor aparece uma vez, com o nome como foi gravado.
     *
     * @throws IllegalArgumentException se o nome for nulo ou em branco
     * @throws UnsupportedOperationException se a implementação não suporta conversas privadas
     */
    default List<String> listarConversas(String usuario) {
        throw new UnsupportedOperationException("Esta implementação não suporta conversas privadas.");
    }
}
