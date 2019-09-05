import gzip
import json
import os
import random

import numpy as np

from model.config import *

_blocked_general_types = {
    'owl thing',
    'physical entity',
    'object',
    'whole',
    'yagolegalactorgeo',
    'yagolegalactor',
    'yagopermanentlylocatedentity',
    'living thing',
    'organism',
    'causal agent',
    'person',
    'people',
    'people associated with buildings and structures',
    'people associated with places',
    'abstraction',
    'yagogeoentity',
    'artifact',
    'european people',
    'objects',
    'physical objects'
}

# load entity types
_yago_type = None
_entity_to_types = None


def load_yago_type():
    global _yago_type
    _yago_type = []
    global _entity_to_types
    _entity_to_types = {}

    yago_types_set = set()
    print('loading Yago entity types from %s' % yago_type_path)
    with gzip.open(yago_type_path, 'rt', encoding='utf-8') as f:
        for line in f:
            arr = line.strip().split('\t')
            types = json.loads(arr[1])
            yago_types_set.update(types)
            _entity_to_types[arr[0]] = [t for t in types if t not in _blocked_general_types]
    for type in yago_types_set:
        _yago_type.append(type)


def get_glove():
    _glove_path = os.path.join(glove_path, 'glove.6B.' + str(embedding_size) + 'd.txt')

    word_dict = {}
    embedding = [[0.0 for i in range(embedding_size)]]  # first index is 'padding' token, len(embedding) is 'unknown'

    print('loading Glove from %s' % _glove_path)
    with open(_glove_path) as f:
        for line in f:
            arr = line.strip().split()
            word_dict[arr[0]] = len(word_dict)
            embedding.append([float(v) for v in arr[1:]])

    return word_dict, np.asarray(embedding, dtype=np.float32)


# load training data, each line contains: <entity>[tab]<context>
def get_training_data(input_path):
    training_data = []  # [[entity, context]]
    with gzip.open(input_path, 'rt', encoding='utf-8') as f:
        for line in f:
            arr = line.strip().split('\t')
            training_data.append(arr)
    return training_data


# training_data is the output from 'get_training_data'
def sample_epoch_training_data(training_data):
    if _yago_type is None:
        load_yago_type()
    # convert training_datas -> triples [[entity_type_desc, quantity_desc, label (0/1)]]
    return []
    # TODO


def convert_input_to_tensor(data, word_dict, embedding):
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
                etd_decoded.append(word_dict[etd[i]] if etd[i] in word_dict else len(embedding))
            else:
                etd_decoded.append(0)
        entity_type_desc.append(etd_decoded)

        # quantity description
        qd = sample[1].strip().split()
        qd_decoded = []
        for i in range(max_quantity_desc_len):
            if i < len(qd):
                qd_decoded.append(word_dict[qd[i]] if qd[i] in word_dict else len(embedding))
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
