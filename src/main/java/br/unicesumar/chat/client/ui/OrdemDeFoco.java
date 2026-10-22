package br.unicesumar.chat.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Window;
import java.util.List;
import java.util.Objects;

/** Ordem de Tab explícita: a sequência lógica da tela, pulando o que está desligado ou escondido. */
public class OrdemDeFoco extends FocusTraversalPolicy {

    private final List<Component> ordem;
    private final Component inicial;

    /**
     * Cria a ordem.
     *
     * @param ordem   componentes na sequência do Tab (pelo menos um)
     * @param inicial quem recebe o foco ao abrir; se for nulo ou estiver indisponível, o primeiro disponível
     */
    public OrdemDeFoco(List<? extends Component> ordem, Component inicial) {
        Objects.requireNonNull(ordem, "ordem");
        if (ordem.isEmpty()) {
            throw new IllegalArgumentException("A ordem de foco precisa de pelo menos um componente.");
        }
        this.ordem = List.copyOf(ordem);
        this.inicial = inicial;
    }

    private static boolean disponivel(Component c) {
        return c.isEnabled() && c.isVisible() && c.isFocusable();
    }

    private Component procurar(int partida, int passo) {
        int n = ordem.size();
        for (int i = 0; i < n; i++) {
            int indice = Math.floorMod(partida + passo * i, n);
            if (disponivel(ordem.get(indice))) {
                return ordem.get(indice);
            }
        }
        return null;
    }

    @Override
    public Component getComponentAfter(Container raiz, Component atual) {
        int indice = ordem.indexOf(atual);
        return indice < 0 ? getFirstComponent(raiz) : procurar(indice + 1, 1);
    }

    @Override
    public Component getComponentBefore(Container raiz, Component atual) {
        int indice = ordem.indexOf(atual);
        return indice < 0 ? getLastComponent(raiz) : procurar(indice - 1, -1);
    }

    @Override
    public Component getFirstComponent(Container raiz) {
        return procurar(0, 1);
    }

    @Override
    public Component getLastComponent(Container raiz) {
        return procurar(ordem.size() - 1, -1);
    }

    @Override
    public Component getDefaultComponent(Container raiz) {
        return inicial != null && disponivel(inicial) ? inicial : getFirstComponent(raiz);
    }

    @Override
    public Component getInitialComponent(Window janela) {
        return getDefaultComponent(null);
    }
}
