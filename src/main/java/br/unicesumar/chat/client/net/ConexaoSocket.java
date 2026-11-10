package br.unicesumar.chat.client.net;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Adaptador real de {@link ConexaoChat}: TCP com o protocolo de texto. */
public class ConexaoSocket implements ConexaoChat {

    private static final Logger LOG = Logger.getLogger(ConexaoSocket.class.getName());
    private static final int TEMPO_PARA_CONECTAR_MS = 5000;

    private final String host;
    private final int porta;
    private final SocketFactory fabrica;
    private volatile Socket socket;
    private volatile BufferedReader entrada;
    private volatile PrintWriter saida;
    private volatile boolean aberta;
    private volatile boolean fechada;
    private volatile int tempoDeEspera;

    /** Cria a conexão (ainda fechada) para o servidor indicado. */
    public ConexaoSocket(String host, int porta) {
        this(host, porta, SocketFactory.getDefault());
    }

    /** Cria a conexão usando a fábrica de sockets dada (por exemplo, a de um contexto TLS). */
    public ConexaoSocket(String host, int porta, SocketFactory fabrica) {
        this.host = host;
        this.porta = porta;
        this.fabrica = java.util.Objects.requireNonNull(fabrica, "fabrica");
    }

    @Override
    public void abrir() throws IOException {
        Socket novo = fabrica.createSocket();
        try {
            novo.connect(new InetSocketAddress(host, porta), TEMPO_PARA_CONECTAR_MS);
        } catch (IOException e) {
            novo.close();
            throw new IOException("Não foi possível conectar a " + host + ":" + porta
                    + ". Verifique se o servidor está em execução.", e);
        }
        if (novo instanceof SSLSocket tls) {
            try {
                novo.setSoTimeout(TEMPO_PARA_CONECTAR_MS);
                tls.startHandshake();
            } catch (IOException e) {
                novo.close();
                throw new IOException("Falha no TLS com " + host + ":" + porta + ". Confira se o servidor foi "
                        + "iniciado com --tls e se o certificado (.crt) é o do servidor.", e);
            }
        }
        novo.setSoTimeout(tempoDeEspera);
        socket = novo;
        entrada = new BufferedReader(new InputStreamReader(novo.getInputStream(), StandardCharsets.UTF_8));
        saida = new PrintWriter(new OutputStreamWriter(novo.getOutputStream(), StandardCharsets.UTF_8));
        aberta = true;
    }

    /** {@inheritDoc} Várias threads podem enviar; a escrita é sincronizada. */
    @Override
    public synchronized void enviar(String texto) throws IOException {
        PrintWriter out = saida;
        if (!aberta || out == null) {
            throw new IOException("A conexão está fechada. Conecte-se novamente.");
        }
        out.print(Protocolo.codificarEntrada(texto) + "\n");
        out.flush();
        if (out.checkError()) {
            fechar();
            throw new IOException("Falha ao enviar. A conexão com o servidor foi perdida.");
        }
    }

    @Override
    public Evento receber() throws IOException {
        BufferedReader in = entrada;
        if (in == null) {
            throw new IOException("A conexão ainda não foi aberta.");
        }
        while (true) {
            String linha;
            try {
                linha = in.readLine();
            } catch (IOException e) {
                if (fechada) {
                    return null;
                }
                throw e;
            }
            if (linha == null) {
                return null;
            }
            Evento evento = Protocolo.decodificar(linha);
            if (evento instanceof Evento.Ping) {
                enviar("/pong"); // o servidor só quer saber se ainda estamos aqui
                continue;
            }
            return evento;
        }
    }

    /** {@inheritDoc} Usa {@code SO_TIMEOUT} do socket; pode ser chamado antes de abrir (vale ao abrir). */
    @Override
    public void definirTempoDeEspera(int milissegundos) {
        if (milissegundos < 0) {
            throw new IllegalArgumentException("O tempo de espera não pode ser negativo.");
        }
        tempoDeEspera = milissegundos;
        Socket s = socket;
        if (s != null && !fechada) {
            try {
                s.setSoTimeout(milissegundos);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Não foi possível definir o tempo de espera: {0}", e.getMessage());
            }
        }
    }

    @Override
    public void fechar() {
        fechada = true;
        aberta = false;
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                LOG.log(Level.INFO, "Falha ao fechar o socket (a conexão já havia caído): {0}", e.getMessage());
            }
        }
    }

    @Override
    public boolean aberta() {
        return aberta;
    }
}
