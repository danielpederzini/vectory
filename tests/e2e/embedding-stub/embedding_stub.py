import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from http.server import ThreadingHTTPServer


EMBEDDING_DIMENSIONS = 1024
EMBEDDING_ENDPOINT = "/v1/embeddings"
TOPIC_MARKER_PREFIX = "[E2E_TOPIC:"
TOPIC_MARKER_SUFFIX = "]"
TOPIC_VECTOR_COMPONENT_INDEX = {
    "coffee": 0,
    "hiking": 1,
    "photography": 2,
    "cooking": 3,
}


def create_topic_embedding(topic_name):
    topic_component_index = TOPIC_VECTOR_COMPONENT_INDEX.get(topic_name)
    if topic_component_index is None:
        raise ValueError(f"no deterministic embedding configured for topic: {topic_name}")

    embedding_components = [0.0] * EMBEDDING_DIMENSIONS
    embedding_components[topic_component_index] = 1.0
    return embedding_components


def extract_topic_name(post_text):
    marker_start_index = post_text.find(TOPIC_MARKER_PREFIX)
    if marker_start_index < 0:
        raise ValueError(f"missing topic marker in post text: {post_text}")

    topic_start_index = marker_start_index + len(TOPIC_MARKER_PREFIX)
    topic_end_index = post_text.find(TOPIC_MARKER_SUFFIX, topic_start_index)
    if topic_end_index < 0:
        raise ValueError(f"unterminated topic marker in post text: {post_text}")
    return post_text[topic_start_index:topic_end_index]


def get_embedding_request_text(embedding_input):
    if not isinstance(embedding_input, dict) or "text" not in embedding_input:
        raise ValueError("only text embedding inputs are supported")
    return embedding_input["text"]


class EmbeddingRequestHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        if self.path != EMBEDDING_ENDPOINT:
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        try:
            request_body = self.read_request_body()
            embedding_inputs = request_body["input"]
            embedding_response = self.create_embedding_response(embedding_inputs)
            self.send_json_response(HTTPStatus.OK, embedding_response)
        except (KeyError, ValueError, json.JSONDecodeError) as exception:
            self.send_json_response(HTTPStatus.BAD_REQUEST, {"error": str(exception)})

    def read_request_body(self):
        content_length = self.headers.get("Content-Length")
        request_bytes = (
            self.read_fixed_length_request_body(int(content_length))
            if content_length is not None
            else self.read_chunked_request_body()
        )
        return json.loads(request_bytes.decode("utf-8"))

    def read_fixed_length_request_body(self, content_length):
        return self.rfile.read(content_length)

    def read_chunked_request_body(self):
        request_body_chunks = []
        while True:
            chunk_size_line = self.rfile.readline().decode("ascii").strip()
            chunk_size = int(chunk_size_line.split(";", 1)[0], 16)
            if chunk_size == 0:
                self.rfile.readline()
                break
            request_body_chunks.append(self.rfile.read(chunk_size))
            self.rfile.read(2)
        return b"".join(request_body_chunks)

    def create_embedding_response(self, embedding_inputs):
        response_data = []
        for embedding_input in embedding_inputs:
            post_text = get_embedding_request_text(embedding_input)
            topic_name = extract_topic_name(post_text)
            response_data.append({"embedding": create_topic_embedding(topic_name)})
        return {"data": response_data}

    def send_json_response(self, response_status, response_body):
        encoded_response_body = json.dumps(response_body).encode("utf-8")
        self.send_response(response_status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded_response_body)))
        self.end_headers()
        self.wfile.write(encoded_response_body)

    def log_message(self, format_string, *format_arguments):
        return


def run_embedding_stub():
    embedding_server = ThreadingHTTPServer(("0.0.0.0", 8080), EmbeddingRequestHandler)
    embedding_server.serve_forever()


if __name__ == "__main__":
    run_embedding_stub()
