''' Predict and output scores.

   - Reads model param file.
   - Runs data.
   - Remaps label indices.
   - Outputs protobuf file.
'''

import argparse

from neural_srl.shared import *
from neural_srl.shared.dictionary import Dictionary
from neural_srl.shared.evaluation import TaggerEvaluator
from neural_srl.shared.inference import *
from neural_srl.shared.io_utils import *
from neural_srl.shared.measurements import Timer
from neural_srl.shared.tagger_data import TaggerData
from neural_srl.theano.tagger import BiLSTMTaggerModel


def get_scores(config, model_path, word_dict_path, label_dict_path, input_path):
    with Timer('Data loading'):
        allow_new_words = True
        print ('Allow new words in test data: {}'.format(allow_new_words))

        # Load word and tag dictionary
        word_dict = Dictionary(unknown_token=UNKNOWN_TOKEN)
        label_dict = Dictionary()
        word_dict.load(word_dict_path)
        label_dict.load(label_dict_path)
        data = TaggerData(config, [], [], word_dict, label_dict, None, None)

        # Load test data.
        test_sentences, emb_inits, emb_shapes, pred_pos = reader.get_srl_test_data(
            input_path,
            config,
            data.word_dict,
            data.label_dict,
            allow_new_words)

        print ('Read {} sentences.'.format(len(test_sentences)))

        # Add pre-trained embeddings for new words in the test data.
        # if allow_new_words:
        data.embedding_shapes = emb_shapes
        data.embeddings = emb_inits

        # Batching.
        test_data = data.get_test_data(test_sentences, batch_size=config.dev_batch_size)

    with Timer('Model building and loading'):
        model = BiLSTMTaggerModel(data, config=config, fast_predict=True)
        model.load(model_path)
        dist_function = model.get_distribution_function()

    with Timer('Running model'):
        scores = None
        for i, batched_tensor in enumerate(test_data):
            x, _, num_tokens, weights = batched_tensor
            p, sc = dist_function(x, weights)
            scores = numpy.concatenate((scores, sc), axis=0) if i > 0 else sc

    return scores, data, test_sentences, test_data, pred_pos


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model',
                        type=str,
                        default='',
                        required=True,
                        help='Path to the model directory.')

    parser.add_argument('--input',
                        type=str,
                        default='',
                        required=True,
                        help='Path to the input file path (sequential tagging format).')

    parser.add_argument('--output',
                        type=str,
                        default='',
                        help='(Optional) Path for output predictions.')

    args = parser.parse_args()
    config = configuration.get_config(os.path.join(args.model, 'config'))

    # Detect available ensemble models.
    model_path = os.path.join(args.model, 'model.npz')
    word_dict_path = os.path.join(args.model, 'word_dict')
    label_dict_path = os.path.join(args.model, 'label_dict')

    # Compute local scores.
    # scores : [n_sents][seq_len][n_tags]
    # test_sentences:
    scores, data, test_sentences, test_data, pred_pos = get_scores(config,
                                                                   model_path,
                                                                   word_dict_path,
                                                                   label_dict_path,
                                                                   args.input)
    # Getting evaluator
    evaluator = TaggerEvaluator(data.get_test_data(test_sentences, batch_size=None), data.label_dict)
    with Timer("Decoding"):
        num_tokens = None

        # Collect sentence length information
        for (i, batched_tensor) in enumerate(test_data):
            _, _, nt, _ = batched_tensor
            num_tokens = numpy.concatenate((num_tokens, nt), axis=0) if i > 0 else nt

        predictions = []
        predictions_without_decoding = []

        for i, slen in enumerate(num_tokens):
            sc = scores[i, :slen, :]

            pred, _ = dynamic_programming_decode(sc, test_sentences[i][0], test_sentences[i][2], data.word_dict,
                                                 data.label_dict, pred_pos[i])
            pred = numpy.array(pred)
            pred.resize(scores.shape[1])
            predictions.append(pred)

            pred = numpy.argmax(sc, axis=1)
            pred.resize(scores.shape[1])
            predictions_without_decoding.append(pred)

    # Evaluate
    print "==================================================="
    print "Result without DP decoding layer:"
    predictions_without_decoding = numpy.stack(predictions_without_decoding, axis=0)
    evaluator.evaluate(predictions_without_decoding)
    print "==================================================="
    print "Result with DP decoding layer:"
    predictions = numpy.stack(predictions, axis=0)
    evaluator.evaluate(predictions)

    if args.output != '':
        print ('Writing to human-readable file: {}'.format(args.output))
        _, _, nt, _ = evaluator.data
        print_to_readable(predictions, nt, data.label_dict, args.input, args.output)
