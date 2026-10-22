# Revisões do projeto

Revisões feitas em modo revisor ao fim da etapa (i): apontam o que existe no código, com arquivo e linha, e dizem o que foi corrigido e o que ficou como decisão. As linhas se referem a `src/main/java/br/unicesumar/chat/`. A revisão de arquitetura foi repetida depois da interface gráfica (seção 3), e a seção 5 traz a revisão de usabilidade.

## 1. Concorrência

### 1.1 Estado compartilhado e o lock que protege cada item

| Estado | Onde | Quem acessa | Proteção |
|---|---|---|---|
| Lista de clientes conectados | `server/ClientRegistry.java:21` | threads dos clientes, thread de accept, serviços | `synchronized(lock)` em todo acesso (linhas 27, 36, 43, 50, 60, 69, 84, 101, 150). Quem percorre trabalha sobre uma cópia (`listar`, `transmitir`:127). |
| Ordem global "salvar e transmitir" | `server/ChatService.java:31` | `enviarMensagem`:49, `entrar`:66, `sair`:89, `mudarDeSala`:150 | `synchronized(ordem)`: todos os clientes veem a mesma ordem de mensagens. |
| Escrita no socket de cada cliente | `server/ClienteHandler.java:131` | qualquer thread durante o broadcast | `synchronized enviar` mais fila por cliente em `server/SaidaAssincrona.java:36-40` (a escrita só enfileira e nunca bloqueia). |
| Estado do cliente (`ativo`, `usuario`, `sala`) | `server/ClienteHandler.java:37-39` | thread do cliente e threads de broadcast | `volatile`. A identificação é atribuída só por `ClientRegistry.identificar`:101. |
| Fechamento único da conexão | `server/ClienteHandler.java:36` | qualquer thread | `AtomicBoolean` (`encerrar`:171 é idempotente). |
| Socket de escuta e flags de parada | `server/Servidor.java:29-31` | thread principal e thread de accept | `volatile`; `iniciar`:91 é `synchronized`. |
| Fila de escrita por cliente | `server/SaidaAssincrona.java:36-40` | threads de broadcast e thread de escrita | `LinkedBlockingQueue`, `AtomicLong`, `AtomicBoolean`, `volatile`. |
| Nome em uso (verificar e atribuir) | `server/ClientRegistry.java:101-110` | threads de login | sob o mesmo `lock`, uma operação só. |
| Cadastro de usuário | `persistence/jdbc/JdbcUsuarioRepository.java:38` | threads de registro | `INSERT OR IGNORE` sobre `UNIQUE COLLATE NOCASE`: atômico no banco. |
| Banco de dados | `persistence/jdbc/*` | todas as threads | nenhuma `Connection` é compartilhada (uma por operação, com try-with-resources); WAL e `busy_timeout=5000` em `ConnectionFactory.java:31-32`. |
| Repositórios em memória (só testes) | `persistence/memory/*` | testes | métodos `synchronized` e `ConcurrentHashMap`. |
| Exportação e log | `infra/ExportadorHistorico.java:50`, `infra/LogArquivo.java:51` | threads dos clientes | `synchronized` (nome livre e criação do arquivo numa etapa só). |
| Conexão do cliente | `client/net/ConexaoSocket.java:21-25,51` | thread de leitura e thread do teclado | campos `volatile` e `enviar` `synchronized`. |

Ordem dos locks: `ChatService.ordem` pode tomar `ClientRegistry.lock` (em `entrar`) e o monitor de um `ClienteHandler` (em `enviar`). Nenhum deles chama de volta para o `ChatService`, e o `ClientRegistry` nunca escreve em cliente enquanto segura seu lock. Os locks de `LogArquivo` e `ExportadorHistorico` são folhas: nunca seguram outro lock. Não encontrei locks em ordem trocada, então não há ciclo.

### 1.2 Riscos encontrados

| Arquivo | Linha | Risco | Correção ou decisão |
|---|---|---|---|
| `server/ChatService.java` | 49-51 | A gravação no banco acontece dentro de `ordem`. Um `INSERT` lento atrasa todos os envios (gargalo, não deadlock). | **Decisão:** manter. A ordem igual para todos é o requisito. Teste: `ChatServiceConcorrenciaTest` (8 threads, 3 destinatários, mesma ordem, `@RepeatedTest(10)`). |
| `server/ChatService.java` | 115-131 | Verificar e agir sem lock em `enviarPrivada`: o destinatário é localizado e só depois recebe a mensagem, e pode sair entre os dois passos. | **Decisão:** tratar o resultado. Se a entrega falha, o remetente recebe erro e o registro fica gravado sem ser exibido a ninguém. Testes: `destinatarioQueCaiuNoMeioDoEnvioGeraErroSemConfirmarAoRemetente`, `ChatServicePrivadaConcorrenciaTest`. |
| `server/ClientRegistry.java` | 127-142 | Cliente removido durante a transmissão. | Resolvido: percorre cópia, `enviar` devolve `false` e o cliente é removido sem afetar os outros. Testes: `adicionarERemoverEmVariasThreadsEnquantoOutraTransmite...`, `ClienteLentoTest`. |
| `server/ClientRegistry.java` | 142 | `encerrar()` pode ser chamado com `ChatService.ordem` tomado, e `SaidaAssincrona.close` espera até 500 ms (`SaidaAssincrona.java:136`) se a saída não falhou. | **Justificativa:** só se chega a esse ponto quando `enviar` devolveu `false`, isto é, a saída já falhou (não espera) ou já foi fechada (`close` é idempotente). A espera não ocorre sob o lock. Teste: `clienteQueEstouraOLimiteEDesconectado...`. |
| `server/ClienteHandler.java` | 131-138 | `enviar` é `synchronized`; uma escrita lenta seguraria o monitor. | Resolvido pela fila por cliente: `write` só enfileira. Testes: `SaidaAssincronaTest`, `ClienteLentoTest`. |
| `server/SaidaAssincrona.java` | write | Entre a checagem de fechada e o `add`, um `write` pode enfileirar depois do marcador de fim. | **Justificativa:** os bytes são descartados e a conexão já está encerrando. Sem efeito visível. |
| `server/Servidor.java` | 111-193 | Conexão chegando logo depois de `parar()` ainda era aceita. | **Corrigido:** `parar` espera o fim da thread de accept (`join`:193) e descarta socket tardio. Teste: `pararFechaOSocketDeEscutaENaoAceitaMaisConexoes` (25 execuções sem falha). |
| `server/CommandService.java` | history/buscar | Leitura de `origem.sala()` sem lock. | **Justificativa:** o campo é `volatile`, e a sala de um cliente só muda pela própria thread dele, que processa os comandos em sequência. |
| `server/Servidor.java` | 134 | Cliente fica visível no registro antes de `aoConectar`. | Resolvido: todo broadcast filtra por `usuario().isPresent()`. Teste: `naoTransmiteParaClienteAindaNaoIdentificado`. |
| `infra/ExportadorHistorico.java` | 50 | Escolher o nome e criar o arquivo são dois passos. | Resolvido: `synchronized` mais `CREATE_NEW`. Teste: `oitoThreadsExportandoAoMesmoTempo...`. |

A condição de corrida em si está demonstrada de forma isolada em `src/test/java/br/unicesumar/chat/demo/CorridaDeDados.java` (fora do núcleo do chat): duas threads somando 1.000.000 vezes cada em um contador sem sincronização perdem incrementos em quase toda rodada (por exemplo, 1.165.411 de 2.000.000), enquanto as versões com `synchronized` e `AtomicLong` dão sempre 2.000.000. É o mesmo problema que a lista de clientes teria sem o `lock` do `ClientRegistry`.

## 2. JDBC

| Item | Resultado | Evidência |
|---|---|---|
| `PreparedStatement` em toda consulta com dados | Cumpre | `JdbcMensagemRepository.java:69, 96, 166`; `JdbcUsuarioRepository.java:38, 56` |
| Nenhum SQL concatenado com dados do usuário | Cumpre | As constantes `INSERIR`, `ULTIMAS_PUBLICAS`, `POR_USUARIO`, `POR_TEXTO` e `POR_PERIODO` usam `+` só entre literais. Os valores entram por parâmetros. A busca por texto usa `instr`, então `%` e `_` não são curingas (teste com injeção: `porTextoComAspasENaoQuebraNemInjetaSql`). |
| Uso de `Statement` | Único caso justificado | `ConnectionFactory.java:61` executa o script `sql/criar_tabelas.sql`, um arquivo fixo, sem nenhum dado do usuário. |
| try-with-resources | Cumpre | `Connection`, `PreparedStatement` e `ResultSet` em todos os métodos (linhas 68-76, 95-99, 166-168; 38, 56-58). |
| Exceções tratadas | Cumpre | Toda `SQLException` é registrada (`SEVERE`) e relançada como `RepositorioException` com mensagem de "o que fazer" (linhas 82, 106, 175; 43, 65; `ConnectionFactory.java:48, 67`). |
| Conexão compartilhada entre threads | Não há | Cada operação abre e fecha a sua. |
| Exceção engolida (regra do projeto) | **2 achados, corrigidos** | `client/net/ConexaoSocket.java:94` (catch vazio) e `server/AuthService.java:148` (hash malformado retornava `false` sem registrar). Teste novo: `AuthServiceLogTest`. |

**Lacuna que permanece:** `JdbcMensagemRepository.converter`:113 cria `new Usuario(...)` com o nome lido do banco. Se alguém editar o arquivo `chat.db` à mão e gravar um nome inválido, uma `IllegalArgumentException` escapa em vez de `RepositorioException`. O risco só existe com edição manual do banco.

## 3. Arquitetura

| Regra do `AGENTS.md` | Resultado | Como foi verificado |
|---|---|---|
| `model` sem `java.sql`, `java.io`, `java.net` ou `javax.swing` | **Nenhuma violação** | Os únicos imports do pacote `model` são `java.time.*`, `java.util.*` e `java.util.regex.Pattern`. |
| `ChatController` sem `javax.swing` | **Cumpre** | Nenhum import de `javax.swing` ou `java.awt` em `client/controller` (verificado depois da interface). O controlador só conhece a interface `ChatView`. |
| Socket não conhece SQL; repositório não conhece socket | Cumpre | Nenhum SQL fora de `persistence/jdbc`; nenhum `java.net` em `persistence`. |
| `ChatService` depende da interface, nunca do JDBC | Cumpre | Importa apenas `persistence.MensagemRepository` (`ChatService.java:5`). |
| Implementação JDBC injetada no `main` | Cumpre | `ServidorMain` é a única classe fora de `persistence.jdbc` que a importa. |
| `Servidor` e `ClienteHandler` sem SQL nem regra de negócio | Cumpre | Delegam ao `ProcessadorEntrada`. |
| Cliente (terminal e gráfico) nunca conhece Repository | Cumpre | Nenhum import de `server` ou `persistence` em `client/`, nem `java.net` em `client/ui`. |
| Sem God Classes | **Atenção** | `CommandService` (342 linhas) e `ChatService` (204) são as maiores. Cada uma tem uma só responsabilidade (interpretar comandos / regras do chat), mas estão no limite. Dividir mais agora exigiria alterar testes aprovados; fica como ponto de melhoria. |

## 4. Rubrica (5,0 pontos)

| Critério | Máx. | Estimada | Evidência | O que falta |
|---|---|---|---|---|
| Servidor atende vários clientes | 1,3 | 1,3 | `Servidor.java:91` (iniciar), laço de accept em 107-125, uma thread por conexão em 134; `ClienteHandler implements Runnable` (28). Testes: `atendeVariosClientesAoMesmoTempo`, `ServidorDesconexaoIntegracaoTest` (queda em massa). | Sem keep-alive TCP: cliente que some sem aviso só é detectado pela fila ou pelo TCP. |
| Broadcast sem perder nem corromper | 1,2 | 1,2 | `ClientRegistry.transmitir`:127, `ChatService.enviarMensagem`:47. Testes: 8 threads, `@RepeatedTest(10)`, ordem igual para todos; clientes entrando e saindo durante o envio; cliente lento isolado. | Gargalo do lock durante a gravação (risco R1 acima). |
| Persistência JDBC | 1,0 | 1,0 | Salvar e recuperar no `JdbcMensagemRepository`, mesmo contrato de testes da versão em memória, histórico sobrevive a reiniciar o servidor (`ServidorJdbcIntegracaoTest`). | Nada. |
| Pacotes e responsabilidades | 0,5 | 0,4 | Pacotes como em `AGENTS.md`, dependências verificadas na seção 3. | `CommandService` e `ChatService` extensas. |
| Testes unitários | 0,5 | **0,5** | 905 testes, todos passam (17 deles precisam de tela e rodam com `xvfb-run -a mvn test`). Contrato compartilhado entre memória e JDBC; concorrência sem `Thread.sleep`. | Nada. O teste da sala usava um repositório falso que só falhava ao gravar; foi corrigido com um que falha ao ler. |
| Código e relatório | 0,5 | 0,5 | Código sem método público sem Javadoc (`mvn javadoc:javadoc` sem avisos "no comment"). | Nada. README, relatório, roteiro do vídeo e prints foram feitos na etapa (k). |
| **Total estimado hoje** | 5,0 | **4,9** | | Os 0,1 que faltam são de pacotes: `CommandService` e `ChatService` extensas. |

### Os 3 pontos mais fracos e o que foi feito

1. **Teste vermelho.** Corrigido com autorização do autor: o defeito era do repositório falso do teste, que não falhava na leitura. O código do servidor não mudou.
2. **Relatório e README ausentes.** Fazem parte da etapa (k); não foram antecipados aqui.
3. **Exceções engolidas.** Corrigido nos dois lugares (`ConexaoSocket.java:94`, `AuthService.java:148`), com teste novo para o segundo.

Também corrigi a demonstração da condição de corrida, que quase não mostrava perda de incrementos porque uma thread terminava antes de a outra começar: agora as threads largam juntas.

## 5. Usabilidade da interface gráfica

Revisão feita com as capturas de tela reais (`docs/prints/`) e com testes. Os achados 1 a 4 foram vistos nas capturas, não pelos testes, e cada um virou um teste novo antes da correção.

| Item | Resultado | Evidência |
|---|---|---|
| Contraste mínimo de 4,5:1 | Cumpre, nos dois temas | `TemaTest.todosOsParesTextoFundoTemContrasteMinimoDe4e5NosDoisModos` (16 pares por modo). O azul de destaque do tema escuro dava só 3,45:1 usado como texto; criei a cor `textoDestaque` (7,0:1). |
| Fonte do corpo de pelo menos 13 pt | Cumpre | `Tema.TAMANHO_CORPO` = 14; testes de fonte em cada painel. Só remetente, horário e legendas usam 12 pt, como pedido ("fonte menor"). |
| Atalhos | Cumpre | Enter envia (`PainelEntradaTest`) e entra (`PainelLoginTest`); Ctrl+F e Ctrl+Q (`PainelChatTest`, `JanelasGuiTest`); Esc fecha o diálogo de busca; Enter também aciona sala e usuário nas listas. |
| Mensagens de erro dizem o que houve e o que fazer | Cumpre | Ex.: "Não foi possível conectar a localhost:5000. Verifique se o servidor está em execução."; "A mensagem tem 501 caracteres e o limite é 500. Encurte o texto."; "Você está desconectado do servidor. Entre novamente para continuar." |
| Ordem de Tab e foco inicial | Cumpre | `OrdemDeFocoTest`; foco inicial em Usuário no login e na caixa de mensagem no chat (`JanelasGuiTest`). Desconectado, a caixa fica desligada e o foco cai no primeiro componente disponível, de propósito. |
| Nenhuma atualização fora da EDT | Cumpre | `VerificadorDeEdt` (um `RepaintManager` que registra todo `repaint`/`revalidate` fora da EDT): chamar todos os métodos da `ChatView` de uma thread de rede e processar um fluxo completo gera zero violações. Toda chamada de rede passa por `Segundo.plano` (`SwingWorker`). |
| Layout só com gerenciadores permitidos | Cumpre | 8 `BorderLayout`, 6 `GridBagLayout`, 2 `BoxLayout`; nenhum `setBounds` nem `setLayout(null)`. |
| Cores e fontes só em `Tema` | Cumpre | Nenhum `new Color`, `new Font` ou constante de cor fora de `Tema.java`. |
| Estados de botão padronizados | Cumpre | `Botoes` aplica ativo, desabilitado e carregando; `BotoesTest`, `PainelLoginTest`, `PainelBuscaTest`. |
| Dicas e nomes acessíveis | Cumpre | Todos os botões, campos e listas têm tooltip e nome acessível (testes em cada painel). |
| Redimensionar | Cumpre | Janela mínima de 800 x 560; os balões quebram em 70% da largura e se rearrumam (`PainelMensagensTest.estreitarAJanela...`). |

### Achados corrigidos

1. **Balões quebravam o texto cedo demais** (a largura era medida de um jeito e o texto desenhado de outro). Passou a medir com o mesmo motor que desenha. Teste: `LinhaMensagemTest.textoQueCabeNaLarguraMaximaNaoQuebraEmDuasLinhas`.
2. **Trocar de tema desfazia bordas e margens**, porque o FlatLaf as reinstala depois que os componentes tinham sido ajustados. O `Tema` agora avisa os componentes depois de instalar o visual. Testes: `TemaTest.instalarOLookAndFeelAvisaOsOuvintesDepoisDeInstalar...` e `JanelasGuiTest.depoisDeTrocarOTema...`.
3. **A barra de ferramentas ocupava só metade da largura** e seus botões pareciam texto solto. Testes: `PainelChatTest.barraDeFerramentasOcupaALarguraTodaDaJanela` e `osBotoesDaBarraTemAparenciaDeBotao`.
4. **O campo Porta era cortado** quando o erro aparecia no login. Teste: `PainelLoginTest.oCampoDaPortaNaoEncolhe...`.

### Limitações que ficam

- Quando o usuário está lendo mensagens antigas e chegam mensagens novas, a janela não rola (como pedido), mas também **não avisa** que há mensagens novas abaixo.
- Os campos do login não têm mnemônicos (Alt + letra).
- Os testes de janela rodaram só em Linux com `xvfb`; não foram testados em Windows nem em macOS.
- A senha digitada trafega sem criptografia (não há TLS), como já descrito na revisão de segurança.
