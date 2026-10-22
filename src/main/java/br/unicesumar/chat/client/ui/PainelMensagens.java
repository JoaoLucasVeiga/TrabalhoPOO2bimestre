package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.MensagemTela;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Lista rolável de mensagens. A rolagem automática só acontece se o usuário já estava no fim;
 * quem está lendo mensagens antigas não é arrastado para baixo. Ao inserir mensagens antigas
 * no início, a janela continua mostrando as mesmas mensagens.
 */
public class PainelMensagens extends JScrollPane {

    private static final long serialVersionUID = 1L;
    private static final int TOLERANCIA_DO_FIM = 4;
    private static final double FRACAO_DA_LARGURA = 0.7;

    private final transient Tema tema;
    private final transient List<LinhaMensagem> linhas = new ArrayList<>();
    private final Conteudo conteudo = new Conteudo();
    private int larguraMaximaDosBaloes = 480;
    private boolean rolarNoProximoLayout;
    private boolean ajustarPosicaoNoProximoLayout;
    private int posicaoAntesDaInsercao;
    private int alturaAntesDaInsercao;

    /** Cria a lista vazia. */
    public PainelMensagens(Tema tema) {
        this.tema = Objects.requireNonNull(tema, "tema");
        setViewportView(conteudo);
        setBorder(null);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        getVerticalScrollBar().setUnitIncrement(Tema.espaco(3));
        getAccessibleContext().setAccessibleName("Conversa");
        aplicarTema();
        tema.aoMudar(this::aplicarTema);
    }

    private void aplicarTema() {
        conteudo.setBackground(tema.paleta().fundo());
        getViewport().setBackground(tema.paleta().fundo());
        linhas.forEach(LinhaMensagem::aplicarTema);
        conteudo.repaint();
    }

    /** Quantidade de linhas na conversa. */
    public int quantidade() {
        return linhas.size();
    }

    /** A linha de posição {@code indice} (0 é a mais antiga). */
    public LinhaMensagem linha(int indice) {
        return linhas.get(indice);
    }

    /** Indica se a janela de rolagem está no fim da conversa (ou se tudo cabe nela). */
    public boolean estaNoFim() {
        Rectangle visivel = getViewport().getViewRect();
        return visivel.y + visivel.height >= conteudo.getHeight() - TOLERANCIA_DO_FIM;
    }

    /** Acrescenta ao fim; só rola para baixo se o usuário já estava no fim. */
    public void adicionar(MensagemTela mensagem) {
        Objects.requireNonNull(mensagem, "mensagem");
        boolean noFim = estaNoFim();
        LinhaMensagem linha = criarLinha(mensagem);
        linhas.add(linha);
        conteudo.add(linha);
        rolarNoProximoLayout = rolarNoProximoLayout || noFim;
        reorganizar();
    }

    /** Troca toda a conversa e vai para o fim. */
    public void substituir(List<MensagemTela> mensagens) {
        Objects.requireNonNull(mensagens, "mensagens");
        conteudo.removeAll();
        linhas.clear();
        for (MensagemTela m : mensagens) {
            LinhaMensagem linha = criarLinha(m);
            linhas.add(linha);
            conteudo.add(linha);
        }
        rolarNoProximoLayout = true;
        ajustarPosicaoNoProximoLayout = false;
        reorganizar();
    }

    /** Insere mensagens mais antigas no início sem mover o que o usuário está lendo. */
    public void inserirNoInicio(List<MensagemTela> anteriores) {
        Objects.requireNonNull(anteriores, "anteriores");
        if (anteriores.isEmpty()) {
            return;
        }
        posicaoAntesDaInsercao = getViewport().getViewPosition().y;
        alturaAntesDaInsercao = conteudo.getHeight();
        int indice = 0;
        for (MensagemTela m : anteriores) {
            LinhaMensagem linha = criarLinha(m);
            linhas.add(indice, linha);
            conteudo.add(linha, indice);
            indice++;
        }
        ajustarPosicaoNoProximoLayout = true;
        reorganizar();
    }

    /** Vai para o fim da conversa. */
    public void rolarParaOFim() {
        rolarNoProximoLayout = true;
        reorganizar();
    }

    private LinhaMensagem criarLinha(MensagemTela mensagem) {
        LinhaMensagem linha = new LinhaMensagem(tema, mensagem);
        linha.definirLarguraMaxima(larguraMaximaDosBaloes);
        return linha;
    }

    private void reorganizar() {
        conteudo.invalidate();
        if (EventQueue.isDispatchThread()) {
            revalidate();
            repaint();
        }
    }

    /** O balão ocupa no máximo 70% da largura; ao mudar a largura da janela, o texto é rearrumado. */
    @Override
    public void doLayout() {
        super.doLayout();
        int disponivel = getViewport().getWidth();
        if (disponivel <= 0) {
            return;
        }
        int maxima = Math.max(120, (int) (disponivel * FRACAO_DA_LARGURA));
        if (maxima != larguraMaximaDosBaloes) {
            larguraMaximaDosBaloes = maxima;
            linhas.forEach(l -> l.definirLarguraMaxima(maxima));
            conteudo.invalidate();
            ((LayoutManager2) conteudo.getLayout()).invalidateLayout(conteudo);
        }
    }

    /** Painel interno: acompanha a largura da janela e aplica a rolagem depois de cada layout. */
    private final class Conteudo extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        Conteudo() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
        }

        @Override
        public void doLayout() {
            super.doLayout();
            if (ajustarPosicaoNoProximoLayout) {
                ajustarPosicaoNoProximoLayout = false;
                int delta = getHeight() - alturaAntesDaInsercao;
                getViewport().setViewPosition(new Point(0, Math.max(0, posicaoAntesDaInsercao + delta)));
            }
            if (rolarNoProximoLayout) {
                rolarNoProximoLayout = false;
                int y = Math.max(0, getHeight() - getViewport().getHeight());
                getViewport().setViewPosition(new Point(0, y));
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Tema.espaco(3);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height - Tema.espaco(2) : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
