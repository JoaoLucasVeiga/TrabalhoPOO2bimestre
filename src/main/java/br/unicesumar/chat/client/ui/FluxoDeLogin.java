package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.ChatController;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Liga o {@link PainelLogin} ao {@link ChatController}: conecta em segundo plano
 * ({@link Segundo}) e devolve o resultado na EDT, mostrando o erro em linha ou entrando no chat.
 */
public class FluxoDeLogin implements PainelLogin.Acoes {

    private static final Logger LOG = Logger.getLogger(FluxoDeLogin.class.getName());

    private final PainelLogin painel;
    private final ChatController controlador;
    private final Runnable aoEntrar;
    private Runnable aoConcluir = () -> { };

    /**
     * Cria o fluxo.
     *
     * @param aoEntrar chamado na EDT quando o login der certo
     */
    public FluxoDeLogin(PainelLogin painel, ChatController controlador, Runnable aoEntrar) {
        this.painel = Objects.requireNonNull(painel, "painel");
        this.controlador = Objects.requireNonNull(controlador, "controlador");
        this.aoEntrar = Objects.requireNonNull(aoEntrar, "aoEntrar");
    }

    /** Registra algo a executar (na EDT) ao fim de cada tentativa, depois de o painel ser atualizado. */
    void aoConcluirTentativa(Runnable gancho) {
        this.aoConcluir = Objects.requireNonNull(gancho, "gancho");
    }

    @Override
    public void entrar(String host, String porta, String usuario, String senha, boolean criarConta) {
        painel.limparErro();
        painel.definirConectando(true, criarConta);
        Segundo.plano(() -> controlador.conectar(host, porta, usuario, senha, criarConta), resultado -> {
            painel.definirConectando(false, criarConta);
            if (resultado.sucesso()) {
                aoEntrar.run();
            } else {
                painel.mostrarErro(resultado.erro());
            }
            aoConcluir.run();
        }, falha -> {
            LOG.log(Level.SEVERE, "Falha inesperada ao conectar: " + falha.getMessage(), falha);
            painel.definirConectando(false, criarConta);
            painel.mostrarErro("Ocorreu um erro inesperado ao conectar. Tente novamente.");
            aoConcluir.run();
        });
    }
}
