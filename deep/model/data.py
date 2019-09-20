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


def _load_yago_type():
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
            filtered_types = [t for t in types if t not in _blocked_general_types]
            if len(filtered_types) > 0:
                _entity_to_types[arr[0]] = filtered_types
    for type in yago_types_set:
        _yago_type.append(type)


# first index is 'padding' token, len(word_dict) + 1 is 'unknown'
def get_glove():
    _glove_path = os.path.join(glove_path, 'glove.6B.' + str(embedding_size) + 'd.txt')

    word_dict = {}
    embedding = []

    print('loading Glove from %s' % _glove_path)
    with open(_glove_path) as f:
        for line in f:
            arr = line.strip().split()
            word_dict[arr[0]] = len(word_dict) + 1
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
    # convert training_data -> triples [[entity_type_desc, quantity_desc, label (0/1)]]
    if _yago_type is None:
        _load_yago_type()

    n_pos = 0
    n_neg = 0
    samples = []
    for entity, context in training_data:
        if entity in _entity_to_types:
            n_pos += 1
            samples.append([random.choice(_entity_to_types[entity]), context, 1])
        n_neg += 1
        samples.append([random.choice(_yago_type), context, 0])
    print('randomized %d positive and %d negative training samples' % (n_pos, n_neg))
    return samples


def convert_input_to_tensor(data, word_dict, is_train=True):
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
                etd_decoded.append(word_dict[etd[i]] if etd[i] in word_dict else len(word_dict) + 1)
            else:
                etd_decoded.append(0)
        entity_type_desc.append(etd_decoded)

        # quantity description
        qd = sample[1].strip().split()
        qd_decoded = []
        for i in range(max_quantity_desc_len):
            if i < len(qd):
                qd_decoded.append(word_dict[qd[i]] if qd[i] in word_dict else len(word_dict) + 1)
            else:
                qd_decoded.append(0)
        quantity_desc.append(qd_decoded)

    # shuffle data
    if len(label) > 0:
        tmp = [(entity_type_desc[i], quantity_desc[i], label[i]) for i in range(len(entity_type_desc))]
        if is_train:
            random.shuffle(tmp)
        entity_type_desc = [v[0] for v in tmp]
        quantity_desc = [v[1] for v in tmp]
        label = [v[2] for v in tmp]
        return entity_type_desc, quantity_desc, label
    else:
        tmp = [(entity_type_desc[i], quantity_desc[i]) for i in range(len(entity_type_desc))]
        if is_train:
            random.shuffle(tmp)
        entity_type_desc = [v[0] for v in tmp]
        quantity_desc = [v[1] for v in tmp]
        return entity_type_desc, quantity_desc, None
