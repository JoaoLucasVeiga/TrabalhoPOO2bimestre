#!/bin/sh
# Mostra na pratica mensagens simultaneas: varios clientes enviam ao mesmo tempo e o programa
# confere se todos receberam tudo, sem perda e na mesma ordem.
# O servidor precisa estar rodando. Opcional: ./demonstracao-simultanea.sh [servidor] [porta] [clientes] [mensagens] [sala]
cd "$(dirname "$0")" || exit 1
sh ./preparar.sh || exit 1
exec java -cp target/chat-multiusuario-1.0.0.jar br.unicesumar.chat.DemonstracaoSimultaneaMain "$@"
