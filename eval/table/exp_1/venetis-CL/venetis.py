import json
import random

import numpy as np
from sklearn import svm

truth_table_files = ['eval/table/equity/dataset/AnnotatedTables-19092016/dataset_ground_annotation_linking.json',
                     'eval/table/wiki_diff/table_ground_annotation_linking.json',
                     'eval/table/wiki_random/wiki_random_annotation_linking.json']

TRAIN_RATE = 0.25

samples = []
n_true = 0

# load training data
for file in truth_table_files:
    with open(file, 'r') as f:
        lines = f.readlines()

    for line in lines:
        tt = json.loads(line)

        subject_col = set(tt['quantityToEntityColumn'])
        subject_col.discard(-1)

        # now compute features
        for c in range(tt['nColumn']):

            uniq_vals = set()
            n_numeric_cells = 0
            avg_num_words = 0
            var_num_words = 0

            n_words = []
            for r in range(tt['nDataRow']):
                cell_text = tt['data'][r][c]['text']
                uniq_vals.add(cell_text)
                n_words.append(len(cell_text.split(' ')))

                try:
                    float(cell_text)
                    n_numeric_cells += 1
                except:
                    pass

            f = [len(uniq_vals) / tt['nDataRow'], n_numeric_cells / tt['nDataRow'], np.var(n_words), np.mean(n_words),
                 c]

            if c in subject_col:
                if len(subject_col) == 1:
                    samples.append([f, 1])
                    n_true += 1
            else:
                samples.append([f, 0])

print('pos/total: %d/%d' % (n_true, len(samples)))

random.Random(129).shuffle(samples)

train = list(zip(*samples[0:int(TRAIN_RATE * len(samples))]))

# train
clf = svm.SVR(kernel='rbf')
clf.fit(train[0], train[1])

# predict
for file in truth_table_files:
    with open(file, 'r') as f:
        lines = f.readlines()
    macro_avg = []

    for line in lines:
        tt = json.loads(line)

        fs = []
        # now compute features
        for c in range(tt['nColumn']):

            uniq_vals = set()
            n_numeric_cells = 0
            avg_num_words = 0
            var_num_words = 0

            n_words = []
            for r in range(tt['nDataRow']):
                cell_text = tt['data'][r][c]['text']
                uniq_vals.add(cell_text)
                n_words.append(len(cell_text.split(' ')))

                try:
                    float(cell_text)
                    n_numeric_cells += 1
                except:
                    pass

            f = [len(uniq_vals) / tt['nDataRow'], n_numeric_cells / tt['nDataRow'], np.var(n_words), np.mean(n_words),
                 c]

            fs.append(f)

        subject_col = np.argmax(clf.predict(fs))

        res = []

        for c in range(tt['nColumn']):
            v = tt['quantityToEntityColumnGroundTruth'][c]
            if v != -1:
                res.append(1 if v == subject_col else 0)

        macro_avg.append(np.mean(res))

    print('%s: %.3f' % (file, np.mean(macro_avg)))
