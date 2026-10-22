package br.unicesumar.chat.client.ui;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

/**
 * Detecta alteração de componente Swing fora da EDT: todo {@code repaint()} e {@code revalidate()}
 * passa por aqui, e quem os chama de outra thread fica registrado.
 */
final class VerificadorDeEdt extends RepaintManager {

    private final List<String> violacoes = Collections.synchronizedList(new ArrayList<>());
    private final RepaintManager anterior;

    private VerificadorDeEdt(RepaintManager anterior) {
        this.anterior = anterior;
    }

    /** Instala o verificador; chame {@link #remover()} no fim. */
    static VerificadorDeEdt instalar() {
        VerificadorDeEdt v = new VerificadorDeEdt(RepaintManager.currentManager(null));
        RepaintManager.setCurrentManager(v);
        return v;
    }

    void remover() {
        RepaintManager.setCurrentManager(anterior);
    }

    List<String> violacoes() {
        synchronized (violacoes) {
            return new ArrayList<>(violacoes);
        }
    }

    private void checar(String operacao, JComponent c) {
        if (!EventQueue.isDispatchThread()) {
            violacoes.add(operacao + " em " + c.getClass().getSimpleName() + " na thread "
                    + Thread.currentThread().getName());
        }
    }

    @Override
    public synchronized void addInvalidComponent(JComponent componente) {
        checar("revalidate", componente);
        super.addInvalidComponent(componente);
    }

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        checar("repaint", c);
        super.addDirtyRegion(c, x, y, w, h);
    }
}
