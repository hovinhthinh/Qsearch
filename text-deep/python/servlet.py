import SimpleHTTPServer
import SocketServer
import argparse
import json
import logging
import math

try:
    from urllib.parse import urlparse, parse_qs
except ImportError:
    from urlparse import urlparse, parse_qs

from neural_srl.shared import *
from neural_srl.shared.dictionary import Dictionary
from neural_srl.shared.inference import *
from neural_srl.shared.reader import string_sequence_to_ids
from neural_srl.shared.tagger_data import TaggerData
from neural_srl.theano.tagger import BiLSTMTaggerModel


def _load_model(model_path):
    config = configuration.get_config(os.path.join(model_path, 'config'))
    # Load word and tag dictionary
    word_dict = Dictionary(unknown_token=UNKNOWN_TOKEN)
    label_dict = Dictionary()
    word_dict.load(os.path.join(model_path, 'word_dict'))
    label_dict.load(os.path.join(model_path, 'label_dict'))
    data = TaggerData(config, [], [], word_dict, label_dict, None, None)

    test_sentences, emb_inits, emb_shapes, _ = reader.get_srl_test_data(None, config, data.word_dict, data.label_dict,
                                                                        False, True)
    data.embedding_shapes = emb_shapes
    data.embeddings = emb_inits
    model = BiLSTMTaggerModel(data, config=config, fast_predict=True)
    model.load(os.path.join(model_path, 'model.npz'))
    return model, data


entity_feature_map = {"B": 1, "I": 2, "O": 0}

model, data, pred_function = None, None, None


class QfactTaggingHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    def _set_response(self):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != '/tag':
            return
        params = parse_qs(url.query)
        tokenized_sent = params['sent'][0].strip().split()
        qpos = int(params['qpos'][0])
        etags = params['etags'][0].strip().split()
        num_tokens = len(tokenized_sent)

        sents = []
        sents.append([string_sequence_to_ids(tokenized_sent, data.word_dict, True),
                      [1 if j == qpos else 0 for j in range(num_tokens)],
                      [entity_feature_map[ef] for ef in etags],
                      [0 for _ in range(num_tokens)]])
        predicates = [qpos]
        # Semantic role labeling.
        x, _, _, weights = data.get_test_data(sents, batch_size=None)
        srl_pred, scores = pred_function(x, weights)

        predictions = []
        conf = []
        for i, sc in enumerate(scores):
            pred, scr = dynamic_programming_decode(sc, sents[i][0], sents[i][2], data.word_dict, data.label_dict,
                                                   predicates[i])
            pred = numpy.array(pred, dtype=int)
            scr = numpy.array(scr, dtype=float)
            pred.resize(scores.shape[1])
            scr.resize(scores.shape[1])
            conf.append(numpy.average(scr))
            predictions.append(pred)

        predictions = numpy.stack(predictions, axis=0)

        resp = {
            'tags': [data.label_dict.idx2str[l] for l in predictions[0]],
            'conf': math.exp(conf[0])
        }

        self._set_response()
        self.wfile.write("{}".format(json.dumps(resp)).encode('utf-8'))


def run(port=2022):
    logging.basicConfig(level=logging.INFO)
    httpd = SocketServer.TCPServer(("0.0.0.0", port), QfactTaggingHandler)
    logging.info('Starting httpd...\n')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    logging.info('Stopping httpd...\n')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',
                        type=str,
                        default='',
                        required=True,
                        help='Model path.')
    args = parser.parse_args()
    global model, data, pred_function

    model, data = _load_model(args.model)
    pred_function = model.get_distribution_function()

    print('READY')

    run()
