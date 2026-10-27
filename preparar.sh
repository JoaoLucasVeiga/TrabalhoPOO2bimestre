#!/bin/sh
# Compila o projeto na primeira vez (precisa de Java 21 e internet so nessa vez).
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
    echo "Java nao encontrado. Instale o Java 21 ou mais novo e tente de novo."
    exit 1
fi
if [ -f target/chat-multiusuario-1.0.0.jar ] && [ -d target/lib ]; then
    exit 0
fi
echo "Compilando o projeto pela primeira vez. Isso pode levar alguns minutos..."
if ! sh ./mvnw -B -q package -DskipTests; then
    echo "Nao foi possivel compilar. Confira se o Java e a versao 21 ou mais nova (java -version)"
    echo "e se ha conexao com a internet, e tente de novo."
    exit 1
fi
