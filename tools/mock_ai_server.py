"""Mock OpenAI-compatible SSE server for emulator E2E testing.
Emulator reaches the host at http://10.0.2.2:8080
Endpoints:
  GET  /v1/models
  POST /v1/chat/completions  (SSE stream)
"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

REPLY = (
    "夜色像一层薄薄的墨，缓缓浸透了临江城的屋檐。林晚握紧手中的青铜古镜，"
    "镜面上流转的纹路忽然亮起——那是三百年前封印松动的征兆。\"终于来了吗？\"她低声呢喃，"
    "指尖凝出一缕淡金色的灵力。远处的钟楼敲响了第十二声，一个身影从阴影中缓步走出，"
    "正是她等了三年的那个人。"
)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[mock]", fmt % args)

    def _send_sse_headers(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

    def do_GET(self):
        if self.path.endswith("/models"):
            body = json.dumps({
                "object": "list",
                "data": [
                    {"id": "mock-novel-long", "object": "model"},
                    {"id": "mock-mini-fast", "object": "model"},
                ],
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path.endswith("/chat/completions"):
            length = int(self.headers.get("Content-Length", 0))
            payload = json.loads(self.rfile.read(length) or b"{}")
            print("[mock] chat request: model=%s messages=%d" % (
                payload.get("model"), len(payload.get("messages", []))))
            self._send_sse_headers()
            # 分片吐出回复，模拟真实流式
            pieces = [REPLY[i:i + 6] for i in range(0, len(REPLY), 6)]
            for j, piece in enumerate(pieces):
                chunk = {
                    "id": "chatcmpl-mock", "object": "chat.completion.chunk",
                    "model": payload.get("model", "mock"),
                    "choices": [{"index": 0, "delta": {"content": piece}, "finish_reason": None}],
                }
                self.wfile.write(b"data: " + json.dumps(chunk, ensure_ascii=False).encode() + b"\n\n")
                self.wfile.flush()
                time.sleep(0.08)
            # usage
            usage_chunk = {
                "id": "chatcmpl-mock", "object": "chat.completion.chunk",
                "model": payload.get("model", "mock"),
                "choices": [],
                "usage": {"prompt_tokens": 1234, "completion_tokens": 210},
            }
            self.wfile.write(b"data: " + json.dumps(usage_chunk).encode() + b"\n\n")
            stop_chunk = dict(usage_chunk, choices=[{"index": 0, "delta": {}, "finish_reason": "stop"}])
            self.wfile.write(b"data: " + json.dumps(stop_chunk).encode() + b"\n\n")
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        else:
            self.send_response(404)
            self.end_headers()


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 8080), Handler)
    print("[mock] OpenAI-compatible mock server on http://127.0.0.1:8080  (emulator: http://10.0.2.2:8080)")
    server.serve_forever()
