import argparse
import numpy
import os
import shutil

import time

from neural_srl.shared import *
from neural_srl.shared.evaluation import TaggerEvaluator
from neural_srl.shared.measurements import Timer
from neural_srl.shared.tagger_data import TaggerData
from neural_srl.theano.tagger import BiLSTMTaggerModel


def _evaluate_tagger(model, eval_function, batched_dev_data, evaluator, writer, global_step):
    # [sentences][tags]
    predictions = None
    dev_loss = 0
    for i, batched_tensor in enumerate(batched_dev_data):
        x, y, _, weights = batched_tensor
        p, loss = eval_function(x, weights, y)
        predictions = numpy.concatenate((predictions, p), axis=0) if i > 0 else p
        dev_loss += loss / len(batched_dev_data)

    print ('Dev loss={:.6f}'.format(dev_loss))
    evaluator.evaluate(predictions)
    writer.write('{}\t{}\t{:.6f}\t{:.2f}\t{:.2f}\n'.format(global_step,
                                                           time.strftime("%Y-%m-%d %H:%M:%S"),
                                                           dev_loss,
                                                           evaluator.accuracy,
                                                           evaluator.best_accuracy))
    writer.flush()
    if evaluator.has_best:
        model.save(os.path.join(args.model, 'model'))


def _train_tagger(args):
    config = configuration.get_config(args.config)

    with Timer('Data loading'):
        vocab_path = args.vocab if args.vocab != '' else None
        label_path = args.labels if args.labels != '' else None

        # Data and evaluator for SRL.
        data = TaggerData(config,
                          *reader.get_srl_data(config, args.train, args.dev, vocab_path, label_path))

        evaluator = TaggerEvaluator(data.get_development_data(), data.label_dict)

        batched_dev_data = data.get_development_data(batch_size=config.dev_batch_size)
        print ('Dev data has {} batches.'.format(len(batched_dev_data)))

    with Timer('Preparing'):
        if not os.path.isdir(args.model):
            print ('Directory {} does not exist. Creating new.'.format(args.model))
            os.makedirs(args.model)
        else:
            if len(os.listdir(args.model)) > 0:
                print ('[WARNING] Log directory {} is not empty, previous checkpoints might be overwritten'
                       .format(args.model))
        shutil.copyfile(args.config, os.path.join(args.model, 'config'))
        # # Save word and label dict to model directory.
        data.word_dict.save(os.path.join(args.model, 'word_dict'))
        data.label_dict.save(os.path.join(args.model, 'label_dict'))

    with Timer('Building model'):
        model = BiLSTMTaggerModel(data, config=config)
        loss_function = model.get_loss_function()
        eval_function = model.get_eval_function()

    writer = open(os.path.join(args.model, 'checkpoints.tsv'), 'w')
    writer.write('step\tdatetime\tdev_loss\tdev_accuracy\tbest_dev_accuracy\n')

    global_step = 0
    epoch = 0
    while epoch < config.max_epochs:
        train_loss = 0.0
        with Timer("Epoch_%d" % epoch):
            train_data = data.get_training_data(include_last_batch=True)
            for batched_tensor in train_data:
                x, y, _, weights = batched_tensor
                train_loss += loss_function(x, weights, y)
                global_step += 1
        print("Epoch {}, loss={:.3f}".format(epoch, train_loss / len(train_data)))
        epoch += 1
        if epoch % config.checkpoint_every_x_epochs == 0:
            print "============================================================"
            with Timer('Evaluation'):
                _evaluate_tagger(model, eval_function, batched_dev_data, evaluator, writer, global_step)
            print "============================================================"
    writer.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',
                        type=str,
                        default='',
                        required=True,
                        help='Config file for the neural architecture and hyper-parameters.')

    parser.add_argument('--model',
                        type=str,
                        default='',
                        required=True,
                        help='Path to the directory for saving model and checkpoints.')

    parser.add_argument('--train',
                        type=str,
                        default='',
                        required=True,
                        help='Path to the training data, which is a single file in sequential tagging format.')

    parser.add_argument('--dev',
                        type=str,
                        default='',
                        required=True,
                        help='Path to the devevelopment data, which is a single file in the sequential tagging format.')

    parser.add_argument('--vocab',
                        type=str,
                        default='',
                        help='(Optional) A file containing the pre-defined vocabulary mapping. Each line contains a text string for the word mapped to the current line number.')

    parser.add_argument('--labels',
                        type=str,
                        default='',
                        help='(Optional) A file containing the pre-defined label mapping. Each line contains a text string for the label mapped to the current line number.')

    args = parser.parse_args()
    _train_tagger(args)
