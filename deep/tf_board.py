import numpy as np
import tensorflow as tf

from model.config import *

from model.node import get_optimizer

word_embedding = np.zeros([n_vocab, embedding_size], dtype=np.float32)

(entity_type_desc, quantity_desc, label), loss, optimizer = get_optimizer(word_embedding)

init_op = tf.global_variables_initializer()
with tf.Session() as sess:
    # writer = tf.summary.FileWriter("output", sess.graph)
    sess.run(init_op)
    sess.run(optimizer, feed_dict={
        entity_type_desc: [[1, 2, 3, 4, 0]],
        quantity_desc: [[2, 3, 1, 0, 0]],
        label: [1],
    })
    # writer.close()
