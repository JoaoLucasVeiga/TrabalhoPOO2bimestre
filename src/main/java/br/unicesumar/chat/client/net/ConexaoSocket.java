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
import java.nio.charset.StandardCharsets;

/** Adaptador real de {@link ConexaoChat}: TCP com o protocolo de texto. */
public class ConexaoSocket implements ConexaoChat {

    private static final int TEMPO_PARA_CONECTAR_MS = 5000;

    private final String host;
    private final int porta;
    private volatile Socket socket;
    private volatile BufferedReader entrada;
    private volatile PrintWriter saida;
    private volatile boolean aberta;
    private volatile boolean fechada;

    /** Cria a conexão (ainda fechada) para o servidor indicado. */
    public ConexaoSocket(String host, int porta) {
        this.host = host;
        this.porta = porta;
    }

    @Override
    public void abrir() throws IOException {
        Socket novo = new Socket();
        try {
            novo.connect(new InetSocketAddress(host, porta), TEMPO_PARA_CONECTAR_MS);
        } catch (IOException e) {
            novo.close();
            throw new IOException("Não foi possível conectar a " + host + ":" + porta
                    + ". Verifique se o servidor está em execução.", e);
        }
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
        String linha;
        try {
            linha = in.readLine();
        } catch (IOException e) {
            if (fechada) {
                return null;
            }
            throw e;
        }
        return linha == null ? null : Protocolo.decodificar(linha);
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
                // fechar um socket que já caiu não tem o que tratar
            }
        }
    }

    @Override
    public boolean aberta() {
        return aberta;
    }
}
