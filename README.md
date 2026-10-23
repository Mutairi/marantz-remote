# Marantz Remote

Run `sh start.sh`, then open http://127.0.0.1:8765 on this computer.
Requires Python 3 and network access to CINEMA 40 at 10.10.10.40:23. No packages or passwords needed. Stop with Ctrl+C.

Provides power, master volume, mute, common inputs, listening modes, current status and a scrolling receiver log. Uses one persistent receiver connection and retries automatically. Status comes from receiver responses; a successful send alone does not confirm the requested change. Unsupported sound modes may be ignored by the receiver. Source devices determine the incoming audio format.

The service listens only on this computer. It does not expose the receiver controls to the LAN. Close other Telnet clients if the receiver rejects simultaneous connections. Network Control must be enabled on the receiver for standby access.

The Marantz wordmark is fetched from the manufacturer's public website. This is a personal, unofficial remote.
