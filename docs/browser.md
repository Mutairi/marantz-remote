# Browser remote

Requires Python 3 with no additional packages.

From the repository root:

```sh
sh start.sh
```

On Windows, the equivalent command is `python server.py`. Open http://127.0.0.1:8765/ on that same computer. Stop the service with Ctrl+C.

Expand **Volume limit & connection**, select **Change**, and search for a receiver or enter its private IPv4 address. Discovery requires local multicast; manual entry works when multicast discovery is unavailable. The initial fallback address is `10.10.10.40`; select your own receiver if it differs.

Settings are stored in `.remote-settings.json`, excluded from Git. Input names are read from the receiver and refreshed every 30 seconds. Sound adjustments are under the input and mode selectors. The ON/OFF labels are toggle buttons; bass and containment are beside their associated toggle.

**MORE SETTINGS ↗** opens the receiver's own Web Control interface. Browser access to this app is restricted to the hosting computer; use the standalone Windows or Android app on another device.

[Back to overview](../README.md)
