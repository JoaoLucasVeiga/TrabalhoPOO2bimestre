package br.unicesumar.chat.client.controller;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tela falsa genérica: grava toda chamada ao {@link ChatView} (nome e argumentos). Serve para testar
 * o controlador sem escrever os 20 métodos da interface.
 */
final class ChatViewGravadora {

    /** Uma chamada gravada. */
    record Chamada(String metodo, List<Object> argumentos) { }

    private final List<Chamada> chamadas = Collections.synchronizedList(new ArrayList<>());
    private final ChatView view;

    ChatViewGravadora() {
        InvocationHandler gravador = (proxy, metodo, args) -> {
            if (metodo.getDeclaringClass() == Object.class) {
                return metodo.getName().equals("hashCode") ? System.identityHashCode(proxy)
                        : metodo.getName().equals("equals") ? proxy == args[0] : "ChatViewGravadora";
            }
            chamadas.add(new Chamada(metodo.getName(), args == null ? List.of() : Arrays.asList(args)));
            return null;
        };
        this.view = (ChatView) Proxy.newProxyInstance(ChatView.class.getClassLoader(),
                new Class<?>[] {ChatView.class}, gravador);
    }

    /** A tela a entregar ao controlador. */
    ChatView view() {
        return view;
    }

    /** Todas as chamadas ao método, em ordem. */
    List<Chamada> de(String metodo) {
        synchronized (chamadas) {
            return chamadas.stream().filter(c -> c.metodo().equals(metodo)).toList();
        }
    }

    /** A última chamada ao método, ou {@code null}. */
    Chamada ultima(String metodo) {
        List<Chamada> todas = de(metodo);
        return todas.isEmpty() ? null : todas.get(todas.size() - 1);
    }

    /** Esquece o que já foi gravado. */
    void limpar() {
        chamadas.clear();
    }
}
