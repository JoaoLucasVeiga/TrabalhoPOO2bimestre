package br.unicesumar.chat.client.controller;

import java.util.List;

/**
 * O que a janela principal sabe fazer. O {@link ChatController} só chama estes métodos
 * (a thread que os chama pode ser a de leitura da rede); quem implementa é responsável
 * por levar a atualização para a thread da interface. Não há Swing nesta interface.
 */
public interface ChatView {

    /** Define quem é o usuário desta janela (nome com as maiúsculas do cadastro). */
    void definirUsuario(String nome);

    /** Mostra o estado da conexão. */
    void mostrarConexao(EstadoConexao estado);

    /** Acrescenta uma linha ao fim da conversa. */
    void adicionar(MensagemTela mensagem);

    /** Troca todo o conteúdo da conversa (histórico ao entrar numa sala); lista vazia limpa a tela. */
    void substituirHistorico(List<MensagemTela> mensagens, String sala);

    /** Insere, no início da conversa, mensagens mais antigas que as exibidas. */
    void inserirAnteriores(List<MensagemTela> anteriores);

    /** Atualiza a lista de usuários online na sala. */
    void atualizarUsuarios(String sala, List<String> nomes);

    /** Atualiza a lista de salas e destaca a atual. */
    void atualizarSalas(List<String> salas, String atual);

    /** Mostra o resultado de uma busca (lista vazia significa nada encontrado). */
    void mostrarResultadoBusca(List<MensagemTela> resultados);

    /** Mostra um erro dentro do diálogo de busca. */
    void mostrarErroDeBusca(String texto);

    /** Mostra um erro ao usuário (o que houve e o que fazer). */
    void mostrarErro(String texto);

    /** Mostra um aviso sem gravidade. */
    void mostrarAviso(String texto);

    /** A sessão terminou sem o usuário pedir (servidor caiu ou encerrou). */
    void sessaoEncerrada(String motivo);
}
