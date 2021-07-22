import json
import logging
from http.server import BaseHTTPRequestHandler, HTTPServer
from transformers import pipeline
from urllib.parse import urlparse, parse_qs

unmasker = pipeline('fill-mask', model='roberta-large')


class RoBERTa_FillMask(BaseHTTPRequestHandler):
    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != '/unmask':
            return
        params = parse_qs(url.query)
        if 'input' not in params:
            return
        output = unmasker(params['input'][0])
        self._set_response()
        self.wfile.write("{}".format(json.dumps(output)).encode('utf-8'))


def run(port=12993):
    logging.basicConfig(level=logging.INFO)
    server_address = ('0.0.0.0', port)
    httpd = HTTPServer(server_address, RoBERTa_FillMask)
    logging.info('Starting httpd...\n')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info('Stopping httpd...\n')


if __name__ == '__main__':
    from sys import argv

    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
