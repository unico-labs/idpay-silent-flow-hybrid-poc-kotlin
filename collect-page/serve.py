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
        # A self-hosted SDK bundle gets a long-lived cache: repeat visits skip
        # the download and reuse the compiled code. Page files stay uncached.
        if "unico-webframe" in self.path:
            self.send_header("Cache-Control", "public, max-age=31536000, immutable")
        else:
            self.send_header("Cache-Control", "no-store")
        super().end_headers()


print(f"Serving collect page on http://localhost:{PORT} (page files uncached)")
http.server.ThreadingHTTPServer(("", PORT), NoCacheHandler).serve_forever()
