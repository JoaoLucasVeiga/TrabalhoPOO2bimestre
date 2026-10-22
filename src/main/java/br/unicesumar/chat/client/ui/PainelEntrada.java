package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.model.Mensagem;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Rodapé da janela: campo de texto, contador {@code x/500} e botão Enviar. Enviar fica
 * desabilitado com o campo vazio; o contador vira alerta perto do limite; o campo não passa
 * de 500 caracteres.
 */
public class PainelEntrada extends JPanel {

    private static final long serialVersionUID = 1L;

    /** A partir de quantos caracteres o contador avisa que o limite está perto. */
    public static final int LIMITE_DE_ALERTA = 450;

    private static final int LIMITE = Mensagem.LIMITE_CONTEUDO;

    private final transient Tema tema;
    private final transient Consumer<String> aoEnviar;
    private final JTextField campo = new JTextField();
    private final JLabel contador = new JLabel("0/" + LIMITE, SwingConstants.RIGHT);
    private final JButton botaoEnviar = new JButton("Enviar");
    private boolean habilitado = true;

    /**
     * Cria o rodapé.
     *
     * @param aoEnviar chamado com o texto (sem espaços nas pontas) ao enviar
     */
    public PainelEntrada(Tema tema, Consumer<String> aoEnviar) {
        super(new BorderLayout(Tema.espaco(1), 0));
        this.tema = Objects.requireNonNull(tema, "tema");
        this.aoEnviar = Objects.requireNonNull(aoEnviar, "aoEnviar");
        montar();
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
        atualizar();
    }

    private void montar() {
        setBorder(BorderFactory.createEmptyBorder(Tema.espaco(1), Tema.espaco(1), Tema.espaco(1), Tema.espaco(1)));
        ((AbstractDocument) campo.getDocument()).setDocumentFilter(new LimiteDeTamanho());
        campo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                atualizar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                atualizar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                atualizar();
            }
        });
        campo.addActionListener(e -> acionarEnvio());
        botaoEnviar.addActionListener(e -> acionarEnvio());
        campo.setToolTipText("Digite sua mensagem e pressione Enter para enviar (até " + LIMITE + " caracteres)");
        campo.getAccessibleContext().setAccessibleName("Mensagem");
        botaoEnviar.setToolTipText("Enviar a mensagem (Enter)");
        botaoEnviar.getAccessibleContext().setAccessibleName("Enviar mensagem");
        contador.setToolTipText("Caracteres usados e limite de " + LIMITE);
        contador.getAccessibleContext().setAccessibleName("Contador de caracteres");

        JPanel direita = new JPanel(new GridBagLayout());
        direita.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, Tema.espaco(1), 0, Tema.espaco(1));
        c.gridx = 0;
        direita.add(contador, c);
        c.gridx = 1;
        direita.add(botaoEnviar, c);
        add(campo, BorderLayout.CENTER);
        add(direita, BorderLayout.EAST);
    }

    private void aplicarTema() {
        Tema.Paleta p = tema.paleta();
        setBackground(p.fundo());
        campo.setBackground(p.superficie());
        campo.setForeground(p.texto());
        campo.setCaretColor(p.texto());
        campo.setFont(tema.fonteCorpo());
        contador.setFont(tema.fontePequena());
        botaoEnviar.setFont(tema.fonteCorpo());
        atualizar();
    }

    private void atualizar() {
        int tamanho = campo.getText().length();
        contador.setText(tamanho + "/" + LIMITE);
        Tema.Paleta p = tema.paleta();
        contador.setForeground(tamanho >= LIMITE_DE_ALERTA ? p.erro() : p.textoSecundario());
        boolean podeEnviar = habilitado && !campo.getText().isBlank();
        botaoEnviar.setEnabled(podeEnviar);
        botaoEnviar.setBackground(podeEnviar ? p.destaque() : p.superficie());
        botaoEnviar.setForeground(podeEnviar ? p.textoSobreDestaque() : p.textoSecundario());
    }

    /** Envia o texto do campo (se não estiver vazio) e limpa o campo. */
    public void acionarEnvio() {
        String texto = campo.getText().strip();
        if (texto.isEmpty()) {
            return;
        }
        aoEnviar.accept(texto);
        campo.setText("");
    }

    /** Liga ou desliga o campo e o botão (por exemplo, quando a conexão cai). */
    public void definirHabilitado(boolean ligado) {
        this.habilitado = ligado;
        campo.setEnabled(ligado);
        atualizar();
    }

    /** O campo de texto. */
    public JTextField campo() {
        return campo;
    }

    /** O botão Enviar. */
    public JButton botaoEnviar() {
        return botaoEnviar;
    }

    /** O contador x/500. */
    public JLabel contador() {
        return contador;
    }

    /** Impede que o campo passe do limite: o que sobra é cortado. */
    private static final class LimiteDeTamanho extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String texto, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, texto, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int tamanho, String texto, AttributeSet attr)
                throws BadLocationException {
            String novo = texto == null ? "" : texto;
            int cabe = LIMITE - (fb.getDocument().getLength() - tamanho);
            if (cabe < novo.length()) {
                novo = novo.substring(0, Math.max(0, cabe));
            }
            super.replace(fb, offset, tamanho, novo, attr);
        }
    }
}
