''' Framework independent evaluator. Not in use yet.
'''
from __future__ import print_function

import numpy


class TaggerEvaluator(object):
    # data: x: [nSent][seq_len][n_features], y: [nSent][seq_len], len: [nSent], weight: [nSent][seq_len]
    def __init__(self, data, label_dict):
        self.data = data
        self.best_accuracy = 0.0
        self.has_best = False
        self.label_dict = label_dict

    def compute_accuracy(self, predictions):
        _, y, _, weights = self.data
        sub_recall_total = {}
        sub_recall_correct = {}
        for i, j in numpy.ndindex(predictions.shape):
            if not weights[i][j]:
                continue
            sub_recall_total[y[i][j]] = 1 if y[i][j] not in sub_recall_total else sub_recall_total[y[i][j]] + 1
            if predictions[i][j] == y[i][j]:
                sub_recall_correct[y[i][j]] = 1 if y[i][j] not in sub_recall_correct \
                    else sub_recall_correct[y[i][j]] + 1

        sub_prec_total = {}
        sub_prec_correct = {}
        for i, j in numpy.ndindex(predictions.shape):
            if not weights[i][j]:
                continue
            sub_prec_total[predictions[i][j]] = 1 if predictions[i][j] not in sub_prec_total \
                else sub_prec_total[predictions[i][j]] + 1
            if predictions[i][j] == y[i][j]:
                sub_prec_correct[predictions[i][j]] = 1 if predictions[i][j] not in sub_prec_correct \
                    else sub_prec_correct[predictions[i][j]] + 1

        num_correct = numpy.sum(numpy.equal(predictions, y) * weights)
        num_total = numpy.sum(weights)
        self.accuracy = (100.0 * num_correct) / num_total
        print("Total accuracy: {:.3f} ({}/{})".format(self.accuracy, num_correct, num_total))

        avg_prec = 0
        avg_recal = 0
        avg_f1 = 0
        for tag_id in sub_recall_total:
            prec_correct = sub_prec_correct[tag_id] if tag_id in sub_prec_correct else 0
            prec_total = sub_prec_total[tag_id] if tag_id in sub_prec_total else 0
            recall_correct = sub_recall_correct[tag_id] if tag_id in sub_recall_correct else 0
            recall_total = sub_recall_total[tag_id] if tag_id in sub_recall_total else 0
            print("\t- Tag '{}':".format(self.label_dict.idx2str[tag_id]), end='')

            prec = float(prec_correct) / prec_total if prec_total != 0 else -1
            recall = float(recall_correct) / recall_total if recall_total != 0 else -1

            print("\tPrec: {:.3f} ({}/{})".format(prec, prec_correct, prec_total), end='')
            print("\tRecall: {:.3f} ({}/{})".format(recall, recall_correct, recall_total), end='')
            print("\tF1: {:.3f}".format(2.0 * (prec * recall) / (prec + recall) if prec > 0 or recall > 0 else -1))

            avg_prec += prec / len(sub_recall_total)
            avg_recal += recall / len(sub_recall_total)
            avg_f1 += 2.0 * (prec * recall) / (prec + recall) / len(sub_recall_total)
        print("\tMacro-avg:\tPrec: {:.3f}\tRecall: {:.3f}\tF1: {:.3f}".format(avg_prec, avg_recal, avg_f1))

    def evaluate(self, predictions):
        self.compute_accuracy(predictions)
        self.has_best = self.accuracy > self.best_accuracy
        if self.has_best:
            print("Best accuracy so far: {:.3f}".format(self.accuracy))
            self.best_accuracy = self.accuracy
