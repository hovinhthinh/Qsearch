import subprocess

import tensorflow as tf
from tensor2tensor.layers.common_layers import flatten4d3d
from tensor2tensor.models import transformer

from model.config import *

try:
    _n_GPUs = str(subprocess.check_output(["nvidia-smi", "-L"])).count('UUID')
except:
    _n_GPUs = 0

_hparams = transformer.transformer_base()
_hparams.num_hidden_layers = 6

_transformer_encoder = transformer.TransformerEncoder(_hparams, mode=tf.estimator.ModeKeys.TRAIN)


def _attention(scope, input, masking, attention_hidden_dim):  # input: [batch_size, n_word, embedding_size]
    with tf.variable_scope(scope):
        attention_weight = tf.layers.dense(input, attention_hidden_dim, name='weight', use_bias=True,
                                           activation=tf.tanh)

        attention_weight_scale = tf.get_variable(dtype=tf.float32, shape=[attention_hidden_dim],
                                                 name='weight_scale')

        attention_weight = tf.tensordot(attention_weight, attention_weight_scale, axes=[2, 0])  # Shimaoka, ACL2017

        # attention_weight = tf.nn.softmax(tf.add(attention_weight, masking))
        attention_weight = tf.nn.softmax(attention_weight)

        output = tf.reduce_sum(tf.multiply(input, tf.expand_dims(attention_weight, -1)), 1)
        return output


def _self_attention(scope, input, masking, hidden_dim, attention_dim):
    with tf.variable_scope(scope):
        input = tf.expand_dims(input, 2)
        output = _transformer_encoder({"inputs": input, "targets": 0, "target_space_id": 0})
        return flatten4d3d(output[0])

        # Q = tf.layers.dense(input, hidden_dim, name='query', use_bias=True)
        # K = tf.layers.dense(input, hidden_dim, name='key', use_bias=True)
        # V = tf.layers.dense(input, attention_dim, name='value', use_bias=True)
        #
        # attention = tf.matmul(Q, K, transpose_b=True)
        # # scale
        # attention = tf.divide(attention, tf.sqrt(tf.cast(tf.shape(K)[-1], dtype=tf.float32)))
        #
        # # adder for attention mask
        # adder = tf.expand_dims(masking, 1)
        #
        # attention = tf.add(attention, adder)
        # attention = tf.nn.softmax(attention)
        #
        # return tf.matmul(attention, V)


def _description_encoder(scope, word_embedding_t, description):
    with tf.variable_scope(scope):
        entity_type_emb = tf.nn.embedding_lookup(word_embedding_t, description)
        entity_type_emb = tf.layers.dense(entity_type_emb, _hparams.hidden_size, name='project', use_bias=True)
        masking = (1.0 - tf.cast(tf.greater(description, 0), tf.float32)) * -10000.0  # Ignore padding, Like BERT

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

        # entity_type_emb = tf.multiply(entity_type_emb, tf.tensordot(masking,
        #                                                             tf.constant([1 for i in range(embedding_size)],
        #                                                                         dtype=tf.float32), axes=0))

        mixing_output = _self_attention('self_attention', entity_type_emb, masking, attention_hidden_dim,
                                        attention_output_dim)

        # attention
        entity_encoded = _attention('attention', mixing_output, masking, attention_hidden_dim)

        # feed forward
        return tf.layers.dense(entity_encoded, feed_forward_dim, name='feed_forward', use_bias=True)


def get_model(word_embedding):
    print('building model')
    # constants
    with tf.variable_scope('input'):
        word_embedding_t = tf.constant(word_embedding, name='word_embedding', dtype=tf.float32)
        padding_embedding = tf.get_variable('padding_embedding', shape=[1, embedding_size], dtype=tf.float32)
        unknown_embedding = tf.get_variable('unknown_embedding', shape=[1, embedding_size], dtype=tf.float32)
        word_embedding_t = tf.concat([padding_embedding, word_embedding_t, unknown_embedding], axis=0)

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
    if tf.test.is_gpu_available() and _n_GPUs > 1:
        with tf.device('/device:GPU:1'):
            quantity_encoded = _description_encoder('quantity_desc', word_embedding_t, quantity_desc)
    else:
        quantity_encoded = _description_encoder('quantity_desc', word_embedding_t, quantity_desc)

    # fusion
    with tf.variable_scope('fusion'):
        fusion_bias = tf.get_variable('fusion_bias', shape=[], dtype=tf.float32)
        score = tf.reduce_sum(tf.multiply(entity_encoded, quantity_encoded), 1) + fusion_bias

    # optimizer
    with tf.variable_scope('optimizer'):
        n_true = tf.reduce_sum(tf.abs(tf.cast(tf.less(score, 0), tf.float32) - label))
        loss = tf.reduce_sum(tf.nn.sigmoid_cross_entropy_with_logits(labels=label, logits=score))
        return (entity_type_desc, quantity_desc, label), loss, tf.train.AdamOptimizer(learning_rate).minimize(
            loss), n_true
