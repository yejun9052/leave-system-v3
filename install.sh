#!/usr/bin/env bash
# =====================================================================
#  연차관리 시스템 설치 스크립트
#  - Docker Engine + docker compose 플러그인 설치
#  - 배포 패키지(annual-leave-deploy.tar.gz) 압축 해제
#  - (선택) .env 준비 후 컨테이너 기동
#
#  지원 OS : Ubuntu/Debian(apt) · CentOS/RHEL/Rocky/AlmaLinux(dnf/yum)
#
#  사용법 :
#    sudo bash install.sh                 # 대화형(OS 선택 메뉴)
#    sudo bash install.sh --os ubuntu     # 비대화형: ubuntu | centos
#    sudo bash install.sh -f ~/annual-leave-deploy.tar.gz   # 압축본 지정
# =====================================================================
set -euo pipefail

# ---- 설정 ----------------------------------------------------------
COMPOSE_FILE="docker-compose.prod.yml"
DEFAULT_TARBALL="annual-leave-deploy.tar.gz"
APP_DIR="annual-leave"

OS_CHOICE=""
TARBALL=""

# ---- 유틸 ----------------------------------------------------------
c_info()  { printf '\033[36m[INFO]\033[0m %s\n' "$*"; }
c_ok()    { printf '\033[32m[ OK ]\033[0m %s\n' "$*"; }
c_warn()  { printf '\033[33m[WARN]\033[0m %s\n' "$*"; }
c_err()   { printf '\033[31m[ERR ]\033[0m %s\n' "$*" >&2; }
die()     { c_err "$*"; exit 1; }

# root 권한 확보(필요 시 sudo 로 재실행)
ensure_root() {
  if [ "$(id -u)" -ne 0 ]; then
    c_info "root 권한이 필요합니다. sudo 로 다시 실행합니다..."
    exec sudo -E bash "$0" "$@"
  fi
}

# ---- 인자 파싱 -----------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    --os)  OS_CHOICE="${2:-}"; shift 2 ;;
    -f|--file) TARBALL="${2:-}"; shift 2 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^#//'; exit 0 ;;
    *) die "알 수 없는 옵션: $1 (--help 참고)";;
  esac
done

ensure_root "$@"

# ---- OS 선택 -------------------------------------------------------
detect_os() {
  if [ -r /etc/os-release ]; then
    . /etc/os-release
    case "${ID:-}${ID_LIKE:-}" in
      *ubuntu*|*debian*) echo "ubuntu" ;;
      *rhel*|*centos*|*fedora*|*rocky*|*alma*) echo "centos" ;;
      *) echo "" ;;
    esac
  fi
}

if [ -z "$OS_CHOICE" ]; then
  guess="$(detect_os || true)"
  echo "======================================================"
  echo "  설치할 서버 OS 를 선택하세요"
  echo "------------------------------------------------------"
  echo "   1) Ubuntu / Debian        (apt)"
  echo "   2) CentOS / RHEL / Rocky / AlmaLinux   (dnf/yum)"
  [ -n "$guess" ] && echo "   (자동 감지: $guess)"
  echo "======================================================"
  read -rp "선택 [1-2] (엔터=자동감지): " sel
  case "$sel" in
    1) OS_CHOICE="ubuntu" ;;
    2) OS_CHOICE="centos" ;;
    "") OS_CHOICE="$guess"; [ -n "$OS_CHOICE" ] || die "자동 감지 실패. --os ubuntu|centos 로 지정하세요." ;;
    *) die "잘못된 선택: $sel" ;;
  esac
fi
c_info "대상 OS: $OS_CHOICE"

# ---- Docker 설치 --------------------------------------------------
install_common_tools_ubuntu() {
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg tar
}

install_docker_ubuntu() {
  if command -v docker >/dev/null 2>&1; then c_ok "Docker 이미 설치됨 — 건너뜀"; return; fi
  c_info "Ubuntu/Debian용 Docker 저장소 등록 및 설치..."
  install_common_tools_ubuntu
  . /etc/os-release
  local repo_os="ubuntu"; case "${ID:-}" in debian) repo_os="debian";; esac
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL "https://download.docker.com/linux/${repo_os}/gpg" | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  local codename="${VERSION_CODENAME:-$(lsb_release -cs 2>/dev/null || echo stable)}"
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${repo_os} ${codename} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
}

install_docker_centos() {
  if command -v docker >/dev/null 2>&1; then c_ok "Docker 이미 설치됨 — 건너뜀"; return; fi
  c_info "CentOS/RHEL 계열 Docker 저장소 등록 및 설치..."
  local PM="yum"; command -v dnf >/dev/null 2>&1 && PM="dnf"
  $PM install -y tar
  if [ "$PM" = "dnf" ]; then
    dnf install -y dnf-plugins-core
    dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  else
    yum install -y yum-utils
    yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  fi
  $PM install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
}

case "$OS_CHOICE" in
  ubuntu) install_docker_ubuntu ;;
  centos) install_docker_centos ;;
  *) die "OS 는 ubuntu 또는 centos 여야 합니다 (입력: $OS_CHOICE)";;
esac

# ---- Docker 서비스 기동 & 권한 ------------------------------------
systemctl enable --now docker 2>/dev/null || service docker start || true
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  c_ok "Docker + compose 설치 확인: $(docker --version) / $(docker compose version | head -n1)"
else
  die "Docker 설치 검증 실패. 로그를 확인하세요."
fi
# sudo 로 실행한 원래 사용자를 docker 그룹에 추가(선택)
if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ]; then
  usermod -aG docker "$SUDO_USER" 2>/dev/null && \
    c_info "'$SUDO_USER' 를 docker 그룹에 추가함 (적용하려면 재로그인 필요)"
fi

# ---- 배포 패키지 위치 결정 ----------------------------------------
if [ -f "$COMPOSE_FILE" ]; then
  # 이미 압축을 푼 패키지 폴더 안에서 실행된 경우
  c_ok "현재 폴더가 배포 패키지입니다 ($COMPOSE_FILE 발견) — 압축 해제 생략"
else
  # 압축본 자동 탐색(인자로 준 경우 우선)
  if [ -z "$TARBALL" ]; then
    for cand in "./$DEFAULT_TARBALL" "$HOME/$DEFAULT_TARBALL" "$(dirname "$0")/$DEFAULT_TARBALL"; do
      [ -f "$cand" ] && { TARBALL="$cand"; break; }
    done
  fi

  if [ -n "$TARBALL" ] && [ -f "$TARBALL" ]; then
    c_info "배포 패키지 압축 해제: $TARBALL"
    tar xzf "$TARBALL"
    c_ok "압축 해제 완료 → ./$APP_DIR"
    cd "$APP_DIR"
  elif [ -d "$APP_DIR" ]; then
    c_warn "압축본을 못 찾았지만 '$APP_DIR' 디렉터리가 있습니다 — 이를 사용합니다."
    cd "$APP_DIR"
  else
    c_warn "배포 패키지($DEFAULT_TARBALL)를 찾지 못했습니다."
    c_warn "압축본을 서버로 올린 뒤 -f 로 지정하거나, 같은 폴더에 두고 다시 실행하세요."
    echo
    c_ok "Docker 설치는 완료되었습니다. 이후 수동으로:"
    echo "    tar xzf $DEFAULT_TARBALL && cd $APP_DIR"
    echo "    cp .env.prod.example .env   # 값 수정 후"
    echo "    docker compose -f $COMPOSE_FILE up -d --build"
    exit 0
  fi
fi

# ---- 시크릿 준비 (DB 비밀번호 / JWT 서명키) ------------------------
if [ -d secrets.enc ] && [ -f secrets-decrypt.sh ]; then
  # 암호화 모드: 암호문(secrets.enc) 을 마스터키로 복호화
  c_info "암호화된 시크릿(secrets.enc) 발견 → 복호화"
  bash secrets-decrypt.sh
else
  # 평문 모드: 시크릿이 없으면 무작위 생성
  gen_secret() {  # $1=길이
    if command -v openssl >/dev/null 2>&1; then
      openssl rand -base64 64 | tr -dc 'A-Za-z0-9' | cut -c1-"$1"
    else
      head -c 128 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-"$1"
    fi
  }
  mkdir -p secrets
  [ -f secrets/db_password ] || { printf '%s' "$(gen_secret 28)" > secrets/db_password; c_ok "secrets/db_password 생성(무작위)"; }
  [ -f secrets/jwt_secret ]  || { printf '%s' "$(gen_secret 64)" > secrets/jwt_secret;  c_ok "secrets/jwt_secret 생성(무작위)"; }
  chmod 700 secrets 2>/dev/null || true
  # 파일은 644: 컨테이너 비-root(appuser) 사용자가 /run/secrets 를 읽어야 함.
  # 상위 디렉터리가 700 이라 호스트의 다른 사용자는 진입 불가 → 실질 보호 유지.
  chmod 644 secrets/* 2>/dev/null || true
  c_info "암호화 저장을 원하면: bash secrets-encrypt.sh 실행(마스터키 생성·secrets.enc 생성)"
fi

# ---- .env 준비 ----------------------------------------------------
if [ ! -f ".env" ]; then
  cp .env.prod.example .env
  c_warn ".env 를 생성했습니다. 반드시 값을 수정하세요:"
  echo "    - SITE_ADDRESS / APP_ORIGIN (접속 주소·포트)"
  echo "    - POSTGRES_PASSWORD / JWT_SECRET (강력한 값)"
  echo "    - LICENSE_KEY / LICENSE_HOST (발급받은 라이선스)"
  echo
  echo "    nano .env    # 편집 후 저장"
  echo
  c_info "수정을 마쳤으면 아래로 기동하세요:"
  echo "    docker compose -f $COMPOSE_FILE up -d --build"
  exit 0
fi

# ---- 기동 여부 확인 ------------------------------------------------
echo
read -rp "지금 컨테이너를 기동할까요? (.env 설정이 끝났을 때만) [y/N]: " go
case "$go" in
  y|Y)
    docker compose -f "$COMPOSE_FILE" up -d --build
    echo
    docker compose -f "$COMPOSE_FILE" ps
    c_ok "기동 완료. 브라우저에서 접속 주소로 확인하세요."
    ;;
  *)
    c_info ".env 확인 후 아래로 기동하세요:"
    echo "    docker compose -f $COMPOSE_FILE up -d --build"
    ;;
esac
