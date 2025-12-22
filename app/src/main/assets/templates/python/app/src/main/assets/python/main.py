import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
import ui_schema as ui

# State
state = {
    "counter": 0,
    "message": "Hello from Python!"
}

def render_ui():
    """Generates the UI JSON based on current state."""
    return ui.Scaffold(
        children=[
            ui.Column(
                children=[
                    ui.Text(text=state["message"], font_size=24),
                    ui.Text(text=f"Count: {state['counter']}", font_size=48),
                    ui.Button(text="Increment", on_click="increment"),
                    ui.Button(text="Decrement", on_click="decrement")
                ]
            )
        ]
    ).to_dict()

class RequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/ui":
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            response = json.dumps(render_ui())
            self.wfile.write(response.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/action":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            action_data = json.loads(post_data.decode('utf-8'))

            action = action_data.get("action")
            if action == "increment":
                state["counter"] += 1
            elif action == "decrement":
                state["counter"] -= 1

            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            response = json.dumps(render_ui())
            self.wfile.write(response.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

def run_server():
    server_address = ('127.0.0.1', 5000)
    httpd = HTTPServer(server_address, RequestHandler)
    print("Starting Python UI Server on port 5000...")
    httpd.serve_forever()

if __name__ == "__main__":
    run_server()
