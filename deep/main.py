import os

import numpy as np
import tensorflow as tf
import random
from model.config import *
from model.glove import load_glove
from model.node import get_model

dict, embedding = load_glove()  # of word not in dict then index should be len(dict)


def _load_training_data(data_path):
    positive_samples = []
    with open(os.path.join(data_path, 'train.txt')) as f:  # train.txt contains only positive samples
        for line in f:
            arr = line.strip().lower().split('\t')
            positive_samples.append([*arr, 1])
    return positive_samples


def _convert_input_to_tensor(data):
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

    # TODO: shuffle data
    return entity_type_desc, quantity_desc, label if len(label) > 0 else None


##########

data = _load_training_data('data')

(entity_type_desc, quantity_desc, label), loss, optimizer = get_model(embedding)

init_op = tf.global_variables_initializer()
with tf.Session() as sess:
    # writer = tf.summary.FileWriter("output", sess.graph)
    sess.run(init_op)
    for epoch in range(100):
        e, q, l = _convert_input_to_tensor(data)
        batches = np.array_split(e, batch_size), np.array_split(q, batch_size), np.array_split(l, batch_size)
        epoch_loss = 0
        for i in range(len(batches[0])):
            l, _ = sess.run([loss, optimizer], feed_dict={
                entity_type_desc: batches[0][i],
                quantity_desc: batches[1][i],
                label: batches[2][i],
            })
            epoch_loss += l / len(batches)

        print('loss: %.3f' % epoch_loss)
    # writer.close()
