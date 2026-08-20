#!/bin/sh
# Backup do banco da LK Transportes.
#
# Uso manual:
#   ./backup.sh
#
# Automático, todo dia às 3h (crontab -e na VPS):
#   0 3 * * * cd /caminho/lk-transportes && ./backup.sh >> backup.log 2>&1
#
# Guarda os últimos 14 dias e apaga os mais velhos.

set -e
cd "$(dirname "$0")"

DESTINO="${DESTINO:-./backups}"
DIAS_MANTIDOS="${DIAS_MANTIDOS:-14}"
mkdir -p "$DESTINO"

. ./.env 2>/dev/null || { echo "Sem .env — rode a partir da pasta do projeto."; exit 1; }
BANCO="${POSTGRES_DB:-lktransportes}"
USUARIO="${POSTGRES_USER:-lk}"

ARQUIVO="$DESTINO/lk-$(date +%Y%m%d-%H%M).sql.gz"

# --clean deixa o dump capaz de restaurar por cima de um banco existente.
docker compose exec -T banco pg_dump -U "$USUARIO" --clean --if-exists "$BANCO" | gzip > "$ARQUIVO"

# Um dump vazio ou minúsculo é sinal de falha — melhor gritar do que guardar lixo.
TAMANHO=$(wc -c < "$ARQUIVO")
if [ "$TAMANHO" -lt 1000 ]; then
    echo "FALHOU: o backup saiu com $TAMANHO bytes. Verifique se o banco está no ar."
    rm -f "$ARQUIVO"
    exit 1
fi

find "$DESTINO" -name 'lk-*.sql.gz' -mtime +"$DIAS_MANTIDOS" -delete

echo "Backup: $ARQUIVO ($(echo "$TAMANHO" | awk '{printf "%.1f KB", $1/1024}'))"
echo
echo "Para restaurar:"
echo "  gunzip -c $ARQUIVO | docker compose exec -T banco psql -U $USUARIO $BANCO"
