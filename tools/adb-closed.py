"""Check that the TV's network ADB port is really closed.

Usage: python3 tools/adb-closed.py <tv ip> [port]      (Windows: py -3 tools\\adb-closed.py <tv ip>)

Only "connection refused" proves the port is closed. A timeout or "no route to host" can also mean a
firewall, a sleeping TV or macOS Local Network privacy blocking the check, so it proves nothing.
"""
import socket
import sys

if len(sys.argv) < 2:
    print(__doc__.strip())
    sys.exit(2)

host = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 5555
s = socket.socket()
s.settimeout(4)
try:
    s.connect((host, port))
    print(f"OPEN: {host}:{port} accepts connections. ADB is still reachable.")
    sys.exit(1)
except ConnectionRefusedError:
    print(f"CLOSED (connection refused): {host}:{port}")
except OSError as e:
    print(f"UNKNOWN ({e.__class__.__name__}: {e}): the TV may be off or unreachable. This does not prove the port is closed.")
    sys.exit(2)
finally:
    s.close()
