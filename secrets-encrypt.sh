#!/usr/bin/env bash
# =====================================================================
#  시크릿 암호화 — DB 비밀번호 / JWT 서명키를 마스터키로 암호화
#    평문 secrets/*  →  암호문 secrets.enc/*.enc  (AES-256-CBC, PBKDF2)
#
#  · 마스터키는 앱 폴더 "밖"(기본 ~/.config/annual-leave/master.key)에 보관
#    → 앱 폴더(secrets.enc 포함)를 백업/공유해도 키가 없으면 복호화 불가.
#  · secrets/ 에 기존 평문이 있으면 그 값을, 없으면 무작위 값을 생성해 암호화.
#
#  사용: bash secrets-encrypt.sh
#  키 위치 변경: AL_KEY_FILE=/경로/master.key bash secrets-encrypt.sh
# =====================================================================
set -euo pipefail

KEY_FILE="${AL_KEY_FILE:-$HOME/.config/annual-leave/master.key}"
ENC_DIR="secrets.enc"
PLAIN_DIR="secrets"

command -v openssl >/dev/null 2>&1 || { echo "[ERR] openssl 필요"; exit 1; }

mkdir -p "$(dirname "$KEY_FILE")"; chmod 700 "$(dirname "$KEY_FILE")" 2>/dev/null || true
if [ ! -f "$KEY_FILE" ]; then
  openssl rand -base64 48 > "$KEY_FILE"; chmod 600 "$KEY_FILE"
  echo "[+] 마스터키 생성: $KEY_FILE"
  echo "    ★ 이 키를 안전하게 백업하세요. 분실하면 복호화가 불가능합니다."
fi

gen() { openssl rand -base64 64 | tr -dc 'A-Za-z0-9' | cut -c1-"$1"; }

mkdir -p "$ENC_DIR"
enc_one() { # $1=name $2=length
  local name="$1" len="$2" val
  if [ -f "$PLAIN_DIR/$name" ]; then
    val="$(cat "$PLAIN_DIR/$name")"          # 기존 평문 유지
  else
    val="$(gen "$len")"                        # 없으면 무작위 생성
  fi
  printf '%s' "$val" | openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt \
      -pass file:"$KEY_FILE" -out "$ENC_DIR/$name.enc"
  echo "[+] 암호화 → $ENC_DIR/$name.enc"
}

enc_one db_password 28
enc_one jwt_secret 64
chmod 600 "$ENC_DIR"/*.enc 2>/dev/null || true

echo "[OK] 완료. '$ENC_DIR/' 는 백업/보관해도 안전(암호문)."
echo "     평문 '$PLAIN_DIR/' 는 삭제해도 됩니다(배포 때 secrets-decrypt.sh 로 복원)."
