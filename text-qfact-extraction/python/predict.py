''' Predict and output scores.

   - Reads model param file.
   - Runs data.
   - Remaps label indices.
   - Outputs protobuf file.
'''

import argparse

from neural_srl.shared import *
from neural_srl.shared.evaluation import TaggerEvaluator
from neural_srl.shared.inference import *
from neural_srl.shared.io_utils import *
from neural_srl.shared.measurements import Timer
from test import get_scores

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

        for i, slen in enumerate(num_tokens):
            sc = scores[i, :slen, :]

            pred, _ = dynamic_programming_decode(sc, test_sentences[i][0], test_sentences[i][2], data.word_dict,
                                                 data.label_dict, pred_pos[i])
            pred = numpy.array(pred, dtype=int)
            pred.resize(scores.shape[1])
            predictions.append(pred)

    # Evaluate
    predictions = numpy.stack(predictions, axis=0)

    print ('================================================')
    _, _, nt, _ = evaluator.data
    print_to_readable(predictions, nt, data.label_dict, args.input, None)
