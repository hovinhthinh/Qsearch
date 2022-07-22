import argparse

import sys

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


def _print_stdout(predictions, num_tokens, label_dict, input, conf, output_path=None):
    """ Print predictions to human-readable format.
    """
    if output_path is not None:
        fout = open(output_path, 'w')
    sample_id = 0
    for line in input:
        if output_path is not None:
            fout.write(line.strip() + ' ||| ')
            fout.write(' '.join([label_dict.idx2str[p] for p in predictions[sample_id][0:num_tokens[sample_id]]]))
            fout.write('\n')
        else:
            print(' '.join((' '.join([label_dict.idx2str[p] for p in
                            predictions[sample_id][0:num_tokens[sample_id]]]), "%.6f" % conf[sample_id])))
        sample_id += 1
    if output_path is not None:
        fout.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',
                        type=str,
                        default='',
                        required=True,
                        help='Model path.')

    args = parser.parse_args()
    entity_feature_map = {"B": 1, "I": 2, "O": 0}

    model, data = _load_model(args.model)

    pred_function = model.get_distribution_function()
    print "<READY>"
    while True:
        # read sentence
        sentence = sys.stdin.readline().strip()
        if sentence.find('\t') != -1:  # the first arg is the original sentence
            sentence = sentence.split('\t')[1]
        inputs = sentence.split("|||")

        tokenized_sent = inputs[0].strip().split()
        qpos = int(tokenized_sent[0])
        tokenized_sent = tokenized_sent[1:]
        num_tokens = len(tokenized_sent)

        sents = []
        sents.append([string_sequence_to_ids(tokenized_sent, data.word_dict, True),
                      [1 if j == qpos else 0 for j in range(num_tokens)],
                      [entity_feature_map[ef] for ef in inputs[1].strip().split()],
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
        _print_stdout(predictions, [num_tokens], data.label_dict, [sentence], conf, None)

    # while loop.
