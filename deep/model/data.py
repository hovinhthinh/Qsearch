import os
import random
import gzip
import json
import numpy as np
import sys

from model.config import *

# load entity types
_entity_to_types = {}
with gzip.open(yago_type_path, 'rt') as f:
    for line in f:
        arr = line.strip().split('\t')
        _entity_to_types[arr[0]] =  json.loads(arr[1])

def load_glove():
    _glove_path = os.path.join(glove_path, 'glove.6B.' + str(embedding_size) + 'd.txt')

    dictionary = {}
    embedding = [[0.0 for i in range(embedding_size)]]  # first index is 'nothing' token

    print('loading Glove from %s' % _glove_path)
    with open(_glove_path) as f:
        for line in f:
            arr = line.strip().split()
            dictionary[arr[0]] = len(dictionary)
            embedding.append([float(v) for v in arr[1:]])

    return dictionary, np.asarray(embedding, dtype=np.float32)


def load_input_data(data_path):
    positive_samples = []
    with open(os.path.join(data_path, 'train.txt')) as f:  # train.txt contains only positive samples
        for line in f:
            arr = line.strip().lower().split('\t')
            positive_samples.append([*arr, 1])
    return positive_samples

def sample_training_data():
    pass
    # TODO

def convert_input_to_tensor(data, dict, embedding):
    entity_type_desc = []
    quantity_desc = []
    label = []
    for sample in data:
        if len(sample) > 2:
            label.append(sample[2])
        # entity description
        etd = sample[0].strip().split()
        etd_decoded = []
        for i in range(max_entity_type_desc_len):
            if i < len(etd):
                etd_decoded.append(dict[etd[i]] if etd[i] in dict else len(embedding))
            else:
                etd_decoded.append(0)
        entity_type_desc.append(etd_decoded)

        # quantity description
        qd = sample[1].strip().split()
        qd_decoded = []
        for i in range(max_quantity_desc_len):
            if i < len(qd):
                qd_decoded.append(dict[qd[i]] if qd[i] in dict else len(embedding))
            else:
                qd_decoded.append(0)
        quantity_desc.append(qd_decoded)

    # shuffle data
    if len(label) > 0:
        tmp = [(entity_type_desc[i], quantity_desc[i], label[i]) for i in range(len(entity_type_desc))]
        random.shuffle(tmp)
        entity_type_desc = [v[0] for v in tmp]
        quantity_desc = [v[1] for v in tmp]
        label = [v[2] for v in tmp]
        return entity_type_desc, quantity_desc, label
    else:
        tmp = [(entity_type_desc[i], quantity_desc[i]) for i in range(len(entity_type_desc))]
        random.shuffle(tmp)
        entity_type_desc = [v[0] for v in tmp]
        quantity_desc = [v[1] for v in tmp]
        return entity_type_desc, quantity_desc, None
