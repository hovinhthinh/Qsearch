import os
import openai

openai.api_key = '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'

import json
import logging
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs


class RoBERTa_FillMask(BaseHTTPRequestHandler):
    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != '/complete':
            return
        params = parse_qs(url.query)
        if 'prompt' not in params:
            return

        response = openai.Completion.create(
            engine="curie",
            prompt=params['prompt'],
            temperature=0,
            max_tokens=8,
            top_p=1,
            frequency_penalty=0.0,
            presence_penalty=0.0,
            stop=["\n"]
        )

        self._set_response()
        self.wfile.write("{}".format(json.dumps(response)).encode('utf-8'))


def run(port=6993):
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
