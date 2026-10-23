# Compatibility and security

## Receiver access

The tested receiver is a Marantz CINEMA 40. Commands use its local TCP control connection (port 23), HTTP capability APIs (8080, with 80 fallback), and its advertised mode list. Receiver Web Control may redirect to a different port. Other models and firmware can behave differently.

Network control must be enabled on the receiver. Guest-network isolation, VPN routing, blocked multicast, or another controller occupying the receiver connection may prevent access. Discovery is best-effort; manual private IPv4 entry is available.

## Boundaries

- The browser and Windows HTTP service binds to `127.0.0.1:8765`, with Host, Origin, and request-token checks.
- Receiver targets must be private IPv4 addresses. XML responses are size-limited and reject entity/DOCTYPE declarations; redirects in receiver API requests are rejected.
- Commands are validated and changes require receiver confirmation. Queued volume/bass taps stop on an error, loss of connection, or unconfirmed change. Unconfirmed commands are not replayed automatically.
- Receiver protocols are unencrypted. Use a trusted LAN and do not forward receiver ports or the app server to the internet.
- The app volume limit constrains requests from this app only. Other remotes and power-on settings can exceed it.

Local settings are excluded from Git. Windows stores them under `%LOCALAPPDATA%\Marantz Remote`; browser settings live beside `server.py`. GitHub build artifacts contain the application, not your local receiver settings.

## Reporting a problem

For ordinary defects, include the platform, receiver model, firmware if known, steps to reproduce, and the result. Remove tokens, personal network details, and private logs before sharing. Keep security-sensitive reports private to the repository owner.
