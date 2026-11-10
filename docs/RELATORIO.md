# Chat Multiusuário com Histórico

**Relatório de arquitetura e concorrência**

Programação Orientada a Objetos · 2º bimestre · github.com/JoaoLucasVeiga/TrabalhoPOO2bimestre

## 1. Visão geral

O projeto é um chat cliente-servidor em Java 21 que usa sockets TCP. O servidor aceita vários clientes ao mesmo tempo, retransmite cada mensagem para todos os conectados e grava o histórico em um banco SQLite por JDBC. Quando um cliente conecta, recebe as últimas mensagens já gravadas.

Além do núcleo pedido, o sistema tem lista de usuários online, conversas privadas com histórico, salas, busca no histórico, login com senha protegida por PBKDF2 e um administrador que exporta o histórico, remove, bane e limpa salas. A interface gráfica, em Swing, tem três colunas (salas e conversas, chat e membros), avatares e mensagens em bloco.

## 2. Arquitetura

O código está dividido em pacotes, e cada um tem uma responsabilidade:

- **model:** Usuario, Mensagem e Sala. É o domínio puro e não conhece rede, banco nem tela.

- **persistence:** as interfaces MensagemRepository (mensagens, conversas privadas e limpeza de sala), UsuarioRepository e BanimentoRepository. Todo o SQL fica nas implementações JDBC, em persistence.jdbc.

- **server:** Servidor, ClienteHandler, ClientRegistry e os serviços (ChatService, CommandService, SalaService, AuthService, Administradores e PreparoDeAdmin).

- **client:** o cliente de terminal, o controlador (que sabe se o usuário vê uma sala ou uma conversa privada) e as telas Swing (JanelaChat, PainelLateral, LinhaMensagem e AvatarIcon).

- **protocol e infra:** a codificação das linhas de texto trocadas pelo socket, e a exportação, o log em arquivo e a leitura da configuração do servidor.

O caminho de uma mensagem é este:

1.  O cliente envia uma linha de texto pelo socket.

2.  O ClienteHandler, que roda na thread daquele cliente, lê a linha e a entrega ao processador.

3.  O ChatService grava a mensagem pelo MensagemRepository e a transmite pelo ClientRegistry.

4.  Cada cliente conectado recebe a mensagem pela sua própria saída.

Algumas decisões de projeto: o ChatService depende da interface do repositório, e a classe JDBC só é escolhida no main. Cada interface tem um adaptador real e um falso (repositório em memória e conexão falsa), o que permite testar sem rede e sem banco. Mensagem é um objeto de valor imutável, Usuario é uma entidade identificada pelo nome e Sala é a raiz do agregado.

## 3. Administração e conversas privadas

**Administrador.** A conta do administrador não está no código. Ela vem do arquivo servidor.properties, que não vai para o GitHub, ou de variáveis de ambiente. O servidor cria a conta na partida, se ela não existir, com a senha guardada por PBKDF2 e salt. Sem essa configuração, ninguém é administrador e os comandos de administração ficam bloqueados. Só o administrador usa /exportar, /kick, /limpar, /ban, /unban e /bans, e ele não lê mensagens privadas.

**Banimento.** O nome banido fica gravado no banco e continua valendo depois de reiniciar o servidor. Quem foi banido não entra com /login, mesmo com a senha certa, e não cria conta com esse nome. O banimento é por nome de usuário, não por endereço de rede.

**Conversas privadas.** Cada conversa entre dois usuários é guardada no banco e consultada por par, nos dois sentidos. Dá para escrever a quem está offline, desde que tenha conta, e a pessoa vê a mensagem quando entrar. Na interface, há uma lista de conversas com contador de não lidas, e a conversa abre na área principal, separada do chat da sala. Cada usuário só enxerga as próprias conversas.

## 4. Concorrência

Cada cliente roda em uma thread própria. Os recursos que várias threads usam ao mesmo tempo, e como cada um foi protegido, estão na tabela abaixo.

| **Recurso compartilhado**          | **Risco**                                                     | **Como foi tratado**                                                                       |
|------------------------------------|---------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Lista de clientes (ClientRegistry) | Threads entram, saem e percorrem a lista ao mesmo tempo       | Lock privado em todo acesso. O broadcast percorre uma cópia da lista.                      |
| Ordem das mensagens (ChatService)  | Duas threads poderiam entregar mensagens em ordens diferentes | Salvar e transmitir acontecem sob o mesmo lock. Todos veem a mesma ordem.                  |
| Escrita no socket de cada cliente  | Um cliente lento travaria o broadcast                         | Fila por cliente e thread de escrita. Acima de 256 KB pendentes, o cliente é desconectado. |
| Nome de usuário                    | Dois logins iguais ao mesmo tempo                             | Verificar e atribuir é uma operação só. O banco tem UNIQUE.                                |
| Banco de dados                     | Várias threads gravando                                       | Uma Connection por operação, com WAL e busy_timeout de 5 s.                                |

Os campos lidos por mais de uma thread são volatile ou AtomicBoolean. Quando um cliente cai de repente, a leitura ou a escrita falha, o ClienteHandler encerra a conexão uma única vez, o cliente sai do registro e o servidor continua atendendo os outros.

**Demonstração da condição de corrida.** Duas threads somam 1.000.000 de vezes cada em um contador, e o esperado é 2.000.000. Sem sincronização, o resultado variou: 1.950.533, 1.196.893, 1.983.247 e 1.994.274 em rodadas diferentes. Com synchronized ou AtomicLong, deu sempre 2.000.000. Isso acontece porque as duas threads leem o mesmo valor, as duas somam um e um dos incrementos se perde. O mesmo problema ocorreria na lista de clientes sem o lock.

**Riscos que ficaram.** A gravação no banco acontece dentro do lock de ordem, então um INSERT lento atrasa todos os envios. Foi mantido assim porque a mesma ordem para todos é o requisito. Também não há keep-alive TCP.

## 5. Testes

São 1125 testes com JUnit 5, todos passando. Os testes unitários não usam rede nem banco real. Os de integração usam SQLite em arquivo temporário e sockets em loopback. Os testes de concorrência usam 8 threads, @RepeatedTest(10), CountDownLatch e join, sem Thread.sleep. O repositório em memória e o JDBC passam pelo mesmo contrato de testes.

## 6. Onde a IA foi usada

- O projeto foi desenvolvido com apoio do Claude (IA), que escreveu o código e os testes em etapas, seguindo TDD: primeiro o teste falhando, depois o mínimo de código. Cada etapa terminou com os testes verdes e um commit.

- A IA também fez revisões de concorrência, JDBC, arquitetura e usabilidade (documento docs/REVISOES.md), apontando arquivo e linha de cada risco.

- Validação: os testes foram executados várias vezes, o servidor foi rodado de verdade com três clientes, as telas foram conferidas por capturas e a entrega foi comparada com a rubrica. As decisões de arquitetura foram aprovadas pelo autor antes de cada etapa.

- Erros da IA que foram encontrados: ela afirmou que os testes tinham passado em 8 rodadas sem ter contado. Passamos a conferir o resultado real de cada rodada, o que revelou testes instáveis, que foram consertados. Houve também um teste vermelho cujo defeito estava no próprio teste (um repositório falso que não falhava ao ler), corrigido depois.

- Depois da primeira entrega, a IA também implementou o administrador, o banimento, as conversas privadas e a nova interface, sempre com testes primeiro e em etapas.

- Regra seguida: nenhum teste foi alterado só para passar. Os testes antigos que descreviam um comportamento que mudou de propósito (por exemplo, as mensagens privadas no meio da sala e os balões de mensagem) só foram atualizados com autorização do autor.

## 7. Limitações

- A senha trafega sem criptografia, porque não há TLS. O hash PBKDF2 com salt protege só o que fica gravado no banco.

- Sem keep-alive, um cliente que some sem aviso só é notado pela fila de escrita ou pelo TCP.

- A gravação dentro do lock de ordem pode atrasar os envios sob carga alta.

- As mensagens não lidas das conversas privadas são contadas só enquanto a janela está aberta; não ficam guardadas entre sessões.

- O banimento é por nome de usuário: quem foi banido pode tentar criar outro nome. Depois de /limpar, as telas já abertas só perdem as mensagens antigas ao reentrar na sala.

- Ao ler mensagens antigas, a janela não avisa que chegaram mensagens novas.

- CommandService e ChatService são as maiores classes e estão no limite do tamanho aceitável.

- Os testes automáticos de janela rodaram só em Linux. No Windows, o iniciar-tudo.bat abriu o servidor e as janelas do chat.

## 8. Como executar

Baixe o repositório, instale o Java 21 e, na pasta do projeto, dê dois cliques em iniciar-tudo.bat (Windows). Na primeira vez ele compila o projeto sozinho e depois abre o servidor e três janelas do chat. Para usar o administrador, copie servidor.properties.exemplo para servidor.properties e preencha. Os detalhes e os comandos do chat estão no README.
