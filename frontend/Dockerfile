# 프론트엔드는 백엔드 jar 에 번들되므로, 이 컨테이너는 Caddy(HTTPS 종단 + 프록시)만 담당.
# 재현성 위해 마이너 태그 고정(권장: 운영 CI 에서 @sha256:... 다이제스트로 추가 고정)
FROM caddy:2.8-alpine
COPY Caddyfile /etc/caddy/Caddyfile
EXPOSE 80 443
