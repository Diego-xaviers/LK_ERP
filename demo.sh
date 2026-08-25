#!/usr/bin/env bash
#
# Sobe a LK inteira num processo só, para a equipe testar.
#
#   ./demo.sh                              → só na sua rede (http://localhost:8080)
#   ./demo.sh https://algo.trycloudflare.com  → com o endereço público do túnel
#
# O endereço público importa porque é ele que vai DENTRO do pacote do agente de
# telemetria. Se estiver errado, o agente do motorista não acha o servidor.
#
# Painel e API saem do mesmo processo e do mesmo endereço: um túnel só, nenhum
# CORS, e um endereço só para passar para a galera.
set -e
cd "$(dirname "$0")"

PUBLICA="${1:-http://localhost:8080}"
ENV_FILE=".env.demo"

if [ ! -f "$ENV_FILE" ]; then
  echo "Falta o $ENV_FILE. Copie de .env.demo.exemplo e preencha." >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a; . "./$ENV_FILE"; set +a

export URL_API="${PUBLICA%/}/api"

echo "==> 1/3  Compilando o painel (chamando a API no mesmo endereço)"
cd frontend
[ -d node_modules ] || npm ci

# MSYS_NO_PATHCONV: no Git Bash do Windows, o shell converte o "/api" num
# caminho ("C:/Program Files/Git/api") antes do Vite ver. O painel compila sem
# erro nenhum e só quebra em uso, chamando um endereço que não existe.
MSYS_NO_PATHCONV=1 VITE_API_URL=/api npm run build

# Conferência: o bundle TEM que chamar /api relativo. Se a linha acima falhar
# de novo por qualquer motivo, é melhor parar aqui do que descobrir com a
# equipe já testando.
if ! grep -q '"/api"' dist/assets/index-*.js; then
  echo "ERRO: o painel não ficou apontando para /api relativo." >&2
  echo "      Veja com: grep -o '.\{14\}/api.\{6\}' frontend/dist/assets/index-*.js" >&2
  exit 1
fi

echo "==> 2/3  Colocando o painel dentro do backend"
cd ../backend
rm -rf src/main/resources/static
mkdir -p src/main/resources/static
cp -r ../frontend/dist/* src/main/resources/static/

echo "==> 3/3  Subindo (perfil demo, banco em arquivo em backend/dados/)"
echo
echo "    Painel:   $PUBLICA"
echo "    Agente:   $URL_API"
echo "    Gestor:   $GESTOR_INICIAL_EMAIL"
echo
exec ./mvnw -o spring-boot:run -Dspring-boot.run.profiles=demo
