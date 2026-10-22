package br.unicesumar.chat.client.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Adaptador falso de {@link ChatView}: guarda tudo o que o controlador mandou mostrar. */
final class ChatViewFalsa implements ChatView {

    final List<String> usuarios = Collections.synchronizedList(new ArrayList<>());
    final List<EstadoConexao> estados = Collections.synchronizedList(new ArrayList<>());
    final List<MensagemTela> mensagens = Collections.synchronizedList(new ArrayList<>());
    final List<List<MensagemTela>> historicos = Collections.synchronizedList(new ArrayList<>());
    final List<String> salasDoHistorico = Collections.synchronizedList(new ArrayList<>());
    final List<List<MensagemTela>> anteriores = Collections.synchronizedList(new ArrayList<>());
    final List<String> usuariosDaSala = Collections.synchronizedList(new ArrayList<>());
    final List<List<String>> listasDeUsuarios = Collections.synchronizedList(new ArrayList<>());
    final List<List<String>> listasDeSalas = Collections.synchronizedList(new ArrayList<>());
    final List<String> salasAtuais = Collections.synchronizedList(new ArrayList<>());
    final List<List<MensagemTela>> resultadosDeBusca = Collections.synchronizedList(new ArrayList<>());
    final List<String> errosDeBusca = Collections.synchronizedList(new ArrayList<>());
    final List<String> erros = Collections.synchronizedList(new ArrayList<>());
    final List<String> avisos = Collections.synchronizedList(new ArrayList<>());
    final List<String> encerramentos = Collections.synchronizedList(new ArrayList<>());
    volatile RuntimeException falharAoAdicionar;

    private synchronized void mudou() {
        notifyAll();
    }

    /** Espera (sem sleep: monitor) até a condição valer, no máximo 5 segundos. */
    synchronized boolean aguardar(BooleanSupplier condicao) throws InterruptedException {
        long fim = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condicao.getAsBoolean()) {
            long restante = TimeUnit.NANOSECONDS.toMillis(fim - System.nanoTime());
            if (restante <= 0) {
                return false;
            }
            wait(restante);
        }
        return true;
    }

    @Override
    public void definirUsuario(String nome) {
        usuarios.add(nome);
        mudou();
    }

    @Override
    public void mostrarConexao(EstadoConexao estado) {
        estados.add(estado);
        mudou();
    }

    @Override
    public void adicionar(MensagemTela mensagem) {
        RuntimeException falha = falharAoAdicionar;
        if (falha != null) {
            falharAoAdicionar = null;
            throw falha;
        }
        mensagens.add(mensagem);
        mudou();
    }

    @Override
    public void substituirHistorico(List<MensagemTela> mensagens, String sala) {
        historicos.add(List.copyOf(mensagens));
        salasDoHistorico.add(sala);
        mudou();
    }

    @Override
    public void inserirAnteriores(List<MensagemTela> novas) {
        anteriores.add(List.copyOf(novas));
        mudou();
    }

    @Override
    public void atualizarUsuarios(String sala, List<String> nomes) {
        usuariosDaSala.add(sala);
        listasDeUsuarios.add(List.copyOf(nomes));
        mudou();
    }

    @Override
    public void atualizarSalas(List<String> salas, String atual) {
        listasDeSalas.add(List.copyOf(salas));
        salasAtuais.add(atual);
        mudou();
    }

    @Override
    public void mostrarResultadoBusca(List<MensagemTela> resultados) {
        resultadosDeBusca.add(List.copyOf(resultados));
        mudou();
    }

    @Override
    public void mostrarErroDeBusca(String texto) {
        errosDeBusca.add(texto);
        mudou();
    }

    @Override
    public void mostrarErro(String texto) {
        erros.add(texto);
        mudou();
    }

    @Override
    public void mostrarAviso(String texto) {
        avisos.add(texto);
        mudou();
    }

    @Override
    public void sessaoEncerrada(String motivo) {
        encerramentos.add(motivo);
        mudou();
    }
}
