package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Sala;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.protocol.ProtocoloException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cuida de uma única conexão de cliente. A thread dela lê linhas e as entrega
 * ao {@link ProcessadorEntrada}; qualquer thread pode chamar {@link #enviar}.
 * Não tem SQL nem regra de negócio.
 */
public class ClienteHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClienteHandler.class.getName());

    private final BufferedReader entrada;
    private final PrintWriter saida;
    private final AutoCloseable conexao;
    private final ProcessadorEntrada processador;
    private final AtomicBoolean fechada = new AtomicBoolean(false);
    private volatile boolean ativo = true;
    private volatile Usuario usuario;
    private volatile String sala = Sala.PADRAO;

    /**
     * Cria o handler sobre streams quaisquer (permite testar sem rede).
     *
     * @param entrada     de onde vêm as linhas do cliente
     * @param saida       para onde vão os eventos
     * @param conexao     recurso fechado ao encerrar (ex.: o socket)
     * @param processador quem interpreta o que o cliente envia
     */
    public ClienteHandler(InputStream entrada, OutputStream saida, AutoCloseable conexao,
            ProcessadorEntrada processador) {
        this.entrada = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        this.saida = new PrintWriter(new OutputStreamWriter(saida, StandardCharsets.UTF_8));
        this.conexao = Objects.requireNonNull(conexao, "conexao");
        this.processador = Objects.requireNonNull(processador, "processador");
    }

    /** Cria o handler de um socket aceito pelo servidor. */
    public static ClienteHandler de(Socket socket, ProcessadorEntrada processador)
            throws IOException {
        return new ClienteHandler(socket.getInputStream(), socket.getOutputStream(), socket,
                processador);
    }

    /** Laço de leitura: roda na thread da conexão até o cliente sair ou cair. */
    @Override
    public void run() {
        try {
            processador.aoConectar(this);
            String linha;
            while (ativo && (linha = entrada.readLine()) != null) {
                tratar(linha);
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "Conexão encerrada: {0}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado na conexão: " + e.getMessage(), e);
        } finally {
            encerrar();
            avisarDesconexao();
        }
    }

    private void tratar(String linhaCodificada) {
        try {
            processador.aoReceber(this, Protocolo.decodificarEntrada(linhaCodificada));
        } catch (ProtocoloException e) {
            LOG.log(Level.WARNING, "Linha inválida: {0}", e.getMessage());
            enviar(new Evento.Erro("Não entendi o que você enviou: " + e.getMessage()
                    + " Tente digitar a mensagem de novo."));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Falha ao processar a linha: " + e.getMessage(), e);
            enviar(new Evento.Erro("Ocorreu um erro ao processar seu pedido. Tente novamente."));
        }
    }

    private void avisarDesconexao() {
        try {
            processador.aoDesconectar(this);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Falha ao tratar a desconexão: " + e.getMessage(), e);
        }
    }

    /**
     * Envia um evento ao cliente. Pode ser chamado por qualquer thread (ex.:
     * durante um broadcast); a escrita é sincronizada para não misturar linhas.
     *
     * @return {@code false} se o handler já foi encerrado ou a escrita falhou
     */
    public synchronized boolean enviar(Evento evento) {
        if (!ativo) {
            return false;
        }
        saida.print(Protocolo.codificar(evento) + "\n");
        saida.flush();
        if (saida.checkError()) {
            LOG.log(Level.INFO, "Falha ao escrever para o cliente; marcando como inativo.");
            ativo = false;
            return false;
        }
        return true;
    }

    /** Usuário identificado nesta conexão; vazio até a identificação. */
    public Optional<Usuario> usuario() {
        return Optional.ofNullable(usuario);
    }

    /** Sala em que o cliente está (padrão: "geral"). */
    public String sala() {
        return sala;
    }

    /** Só o {@link ClientRegistry} identifica clientes, para checar nomes de forma atômica. */
    void definirUsuario(Usuario novo) {
        this.usuario = novo;
    }

    /** Muda a sala atual do cliente. */
    void definirSala(String nova) {
        this.sala = Objects.requireNonNull(nova, "sala");
    }

    /** Indica se a conexão ainda está em uso. */
    public boolean ativo() {
        return ativo;
    }

    /** Encerra a conexão. Pode ser chamado várias vezes e de qualquer thread. */
    public void encerrar() {
        ativo = false;
        if (fechada.compareAndSet(false, true)) {
            try {
                conexao.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Falha ao fechar a conexão: " + e.getMessage(), e);
            }
        }
    }
}
