#!/usr/bin/env python3
"""Serve a video for repeatable Android HTTP seek/connection-loss regression checks.

Example:
  python3 scripts/seek_test_server.py /path/to/180-second-video.mp4 --delay 0.08
  adb reverse tcp:8906 tcp:8906
  adb shell am start -a android.intent.action.VIEW -d http://127.0.0.1:8906/video.mp4 \
    -t video/mp4 -n app.marlboroadvance.mpvex.debug/app.marlboroadvance.mpvex.ui.player.PlayerActivity

Drag back/forward, pause and drag, seek to both boundaries, then exit during a drag
and reopen. With --fault-file /tmp/mpvex-drop, creating that file interrupts all
current/future responses. Remove it and seek again to check recovery. Natural EOF
must still honor the player's close/autoplay preference.
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import time
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--port", type=int, default=8906)
    parser.add_argument("--delay", type=float, default=0.0, help="Delay per 64 KiB chunk")
    parser.add_argument("--fault-file", type=Path)
    args = parser.parse_args()
    if not args.video.is_file() or args.delay < 0:
        parser.error("Supply an existing video and a nonnegative delay")

    class Handler(BaseHTTPRequestHandler):
        def do_HEAD(self):
            self.serve_video(send_body=False)

        def do_GET(self):
            self.serve_video(send_body=True)

        def serve_video(self, send_body):
            if urlsplit(self.path).path != "/video.mp4":
                self.send_error(404)
                return
            size = args.video.stat().st_size
            start, end = 0, size - 1
            header = self.headers.get("Range")
            if header:
                match = re.fullmatch(r"bytes=(\d*)-(\d*)", header)
                if not match or not any(match.groups()):
                    self.send_error(400, "Expected a single byte range")
                    return
                first, last = match.groups()
                if first:
                    start = int(first)
                    end = min(int(last), end) if last else end
                else:
                    start = max(0, size - int(last))
                if start > end or start >= size:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.end_headers()
                    return
            self.send_response(206 if header else 200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if header:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            if not send_body:
                return
            self.log_message("range=%s-%s", start, end)
            try:
                with args.video.open("rb") as video:
                    video.seek(start)
                    remaining = end - start + 1
                    while remaining:
                        if args.fault_file and args.fault_file.exists():
                            self.log_message("injected disconnect with %s bytes remaining", remaining)
                            self.close_connection = True
                            return
                        data = video.read(min(65536, remaining))
                        if not data:
                            return
                        self.wfile.write(data)
                        self.wfile.flush()
                        remaining -= len(data)
                        if args.delay:
                            time.sleep(args.delay)
            except (BrokenPipeError, ConnectionResetError):
                # mpv cancels the previous request when the user seeks again.
                pass

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Serving {args.video} at http://127.0.0.1:{server.server_port}/video.mp4", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
