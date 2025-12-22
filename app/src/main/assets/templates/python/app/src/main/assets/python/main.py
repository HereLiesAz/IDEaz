import json
import threading
import sys
import importlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import ui_schema as ui

# State
state = {
    "counter": 0,
    "message": "Hello from Python!"
}

# Keep a reference to the server to shut it down if needed,
# though for simple module reloading we might not need to restart the socket.
httpd = None

def render_ui():
    """Generates the UI JSON based on current state."""
    # We use the 'ui' module which might be reloaded.
    return ui.Scaffold(
        children=[
            ui.Column(
                children=[
                    ui.Text(text=state["message"], font_size=24),
                    ui.Text(text=f"Count: {state['counter']}", font_size=48),
                    ui.Button(text="Increment", on_click="increment"),
                    ui.Button(text="Decrement", on_click="decrement"),
                    # Add a TextField example since we added it to renderer
                    ui.TextField(value="Editable (Stub)", on_value_change="no_op")
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
            try:
                response = json.dumps(render_ui())
                self.wfile.write(response.encode('utf-8'))
            except Exception as e:
                err = json.dumps({"type": "Text", "properties": {"text": f"Error: {str(e)}", "color": "#FF0000"}})
                self.wfile.write(err.encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/action":
            try:
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
            except Exception as e:
                self.send_response(500)
                self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

def run_server():
    global httpd
    if httpd:
        return # Already running

    server_address = ('127.0.0.1', 5000)
    httpd = HTTPServer(server_address, RequestHandler)
    print("Starting Python UI Server on port 5000...")
    httpd.serve_forever()

def reload_modules():
    """Called from Kotlin to reload modules."""
    print("Reloading modules...")
    try:
        # Reload helper modules
        if 'ui_schema' in sys.modules:
            importlib.reload(sys.modules['ui_schema'])

        # We can't easily reload __main__ if we are inside it,
        # but since 'main' is imported by PythonService, it is a module.
        if 'main' in sys.modules:
            importlib.reload(sys.modules['main'])

        print("Reload complete.")
        return "OK"
    except Exception as e:
        print(f"Reload failed: {e}")
        return str(e)

if __name__ == "__main__":
    run_server()
