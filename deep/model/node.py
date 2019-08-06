import numpy as np
import tensorflow as tf

from model.config import *


def _attention(scope, input, attention_hidden_dim):  # input: [batch_size, n_word, embedding_size]
    with tf.variable_scope(scope):
        attention_weight = tf.layers.dense(input, attention_hidden_dim, name='weight', use_bias=True,
                                           activation=tf.tanh)

        attention_weight_scale = tf.get_variable(dtype=tf.float32, shape=[attention_hidden_dim],
                                                 name='weight_scale')

        attention_weight = tf.nn.softmax(tf.tensordot(attention_weight, attention_weight_scale, axes=[2, 0]))

        output = tf.reduce_sum(tf.multiply(input, tf.expand_dims(attention_weight, -1)), 1)
        return output


def _self_attention(scope, input, hidden_dim, attention_dim):
    with tf.variable_scope(scope):
        Q = tf.layers.dense(input, hidden_dim, name='query', use_bias=False)
        K = tf.layers.dense(input, hidden_dim, name='key', use_bias=False)
        V = tf.layers.dense(input, attention_dim, name='value', use_bias=False)

        attention = tf.matmul(Q, K, transpose_b=True)
        # scale
        attention = tf.divide(attention, tf.sqrt(tf.cast(tf.shape(K)[-1], dtype=tf.float32)))

        attention = tf.nn.softmax(attention)

        return tf.matmul(attention, V)


def _description_encoder(scope, word_embedding_t, description):
    with tf.variable_scope(scope):
        entity_type_emb = tf.nn.embedding_lookup(word_embedding_t, description)

        masking = tf.cast(tf.greater(description, 0), tf.int32)

        # bi-lstm
        # entity_forward_cell = tf.nn.rnn_cell.LSTMCell(lstm_hidden_dim)
        # entity_backward_cell = tf.nn.rnn_cell.LSTMCell(lstm_hidden_dim)
        # (output_fw, output_bw), _ = (
        #     tf.nn.bidirectional_dynamic_rnn(
        #         entity_forward_cell,
        #         entity_backward_cell,
        #         entity_type_emb,
        #         sequence_length=tf.reduce_sum(masking, 1),
        #         dtype=tf.float32
        #     )
        # )
        # mixing_output = tf.concat([output_fw, output_bw], 2)

        mixing_output = _self_attention('self_attention', entity_type_emb, attention_hidden_dim, attention_output_dim)

        # attention
        entity_encoded = _attention('attention', mixing_output, attention_hidden_dim)

        # feed forward
        feed_forward_matrix = tf.get_variable(dtype=tf.float32, shape=[attention_output_dim, feed_forward_dim],
                                              name='feed_forward_matrix')
        feed_forward_bias = tf.get_variable(dtype=tf.float32, shape=[feed_forward_dim],
                                            name='feed_forward_bias')

        return tf.tanh(tf.nn.bias_add(tf.matmul(entity_encoded, feed_forward_matrix), feed_forward_bias))


def get_model(word_embedding):
    print('building model')
    # constants
    with tf.variable_scope('input'):
        word_embedding_t = tf.constant(word_embedding, name='word_embedding', dtype=np.float32)
        unknown_embedding = tf.get_variable('unknown_embedding', shape=[1, embedding_size], dtype=np.float32)
        word_embedding_t = tf.concat([word_embedding_t, unknown_embedding], axis=0)

    # placeholders
    with tf.variable_scope('placeholders'):
        # is_train = tf.placeholder(dtype=tf.bool, name='is_train')
        entity_type_desc = tf.placeholder(dtype=tf.int32, name='entity_type_desc',
                                          shape=[None,
                                                 max_entity_type_desc_len])  # 0 means nothing; otherwise 1->n_vocab
        quantity_desc = tf.placeholder(dtype=tf.int32, name='quantity_type_desc',
                                       shape=[None, max_quantity_desc_len])  # 0 means nothing; otherwise 1->n_vocab
        label = tf.placeholder(dtype=tf.float32, name='label', shape=[None])
    # graph
    # encode entity type
    entity_encoded = _description_encoder('entity_type_desc', word_embedding_t, entity_type_desc)

    # encode quantity desc
    quantity_encoded = _description_encoder('quantity_desc', word_embedding_t, quantity_desc)

    # fusion
    with tf.variable_scope('fusion'):
        fusion_bias = tf.get_variable('fusion_bias', shape=[], dtype=tf.float32)
        score = tf.reduce_sum(tf.multiply(entity_encoded, quantity_encoded), 1) + fusion_bias

    # optimizer
    with tf.variable_scope('optimizer'):
        loss = tf.reduce_mean(tf.nn.sigmoid_cross_entropy_with_logits(labels=label, logits=score))
        return (entity_type_desc, quantity_desc, label), loss, tf.train.AdamOptimizer(learning_rate).minimize(loss)
