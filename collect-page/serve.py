#!/usr/bin/env python3
"""Static server for the collect page with caching disabled.

Custom Tabs have no refresh button, so a cached collect.js would silently keep
running old code on the device. Usage: python3 serve.py [port]
"""
import http.server
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # The SDK bundle is big (~3.7MB) and rarely changes: let the browser
        # cache it so only the first run pays the download. Page files stay
        # uncached for fast iteration. In production, serve the bundle
        # versioned with a long-lived immutable cache.
        if self.path.endswith("UnicoCheckBuilder.min.js"):
            self.send_header("Cache-Control", "public, max-age=86400")
        else:
            self.send_header("Cache-Control", "no-store")
        super().end_headers()


print(f"Serving collect page on http://localhost:{PORT} (bundle cached, page files uncached)")
http.server.ThreadingHTTPServer(("", PORT), NoCacheHandler).serve_forever()
