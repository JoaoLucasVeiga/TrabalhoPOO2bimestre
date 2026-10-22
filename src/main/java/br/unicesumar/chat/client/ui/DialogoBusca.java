package br.unicesumar.chat.client.ui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

/** Diálogo de busca (não modal): abas Usuário, Texto e Período; Esc fecha. O conteúdo é o {@link PainelBusca}. */
public class DialogoBusca extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String FECHAR = "fechar";

    private final PainelBusca painel;

    /** Cria o diálogo sobre a janela dona. */
    public DialogoBusca(Window dono, Tema tema, PainelBusca.Acoes acoes) {
        super(dono, "Buscar", ModalityType.MODELESS);
        this.painel = new PainelBusca(tema, acoes);
        setContentPane(painel);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), FECHAR);
        getRootPane().getActionMap().put(FECHAR, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(dono);
    }

    /** O conteúdo do diálogo. */
    public PainelBusca painel() {
        return painel;
    }

    /** Abre o diálogo para buscar na sala indicada, com os resultados limpos. */
    public void abrir(String sala) {
        painel.definirSala(sala);
        painel.limparResultados();
        setVisible(true);
        painel.campoUsuario().requestFocusInWindow();
    }
}
