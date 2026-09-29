#!/usr/bin/env bash
# =====================================================================
#  시크릿 복호화 — 암호문 secrets.enc/*.enc → 평문 secrets/*
#    컨테이너 기동 "직전"에 실행 (docker compose up 전에).
#
#  마스터키(기본 ~/.config/annual-leave/master.key)가 있어야 복호화됩니다.
#
#  사용: bash secrets-decrypt.sh
#  키 위치 변경: AL_KEY_FILE=/경로/master.key bash secrets-decrypt.sh
# =====================================================================
set -euo pipefail

KEY_FILE="${AL_KEY_FILE:-$HOME/.config/annual-leave/master.key}"
ENC_DIR="secrets.enc"
PLAIN_DIR="secrets"

command -v openssl >/dev/null 2>&1 || { echo "[ERR] openssl 필요"; exit 1; }
[ -f "$KEY_FILE" ] || { echo "[ERR] 마스터키 없음: $KEY_FILE"; exit 1; }

mkdir -p "$PLAIN_DIR"; chmod 700 "$PLAIN_DIR" 2>/dev/null || true
dec_one() { # $1=name
  local name="$1"
  [ -f "$ENC_DIR/$name.enc" ] || { echo "[ERR] 암호문 없음: $ENC_DIR/$name.enc"; exit 1; }
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
      -pass file:"$KEY_FILE" -in "$ENC_DIR/$name.enc" -out "$PLAIN_DIR/$name"
}

dec_one db_password
dec_one jwt_secret
# 파일 644: 컨테이너 비-root 사용자가 /run/secrets 읽기 가능(상위 디렉터리 700 이 호스트 보호)
chmod 644 "$PLAIN_DIR"/* 2>/dev/null || true
echo "[OK] 복호화 완료 → $PLAIN_DIR/ (이제 docker compose up 가능)"
