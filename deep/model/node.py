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
_hparams.num_hidden_layers = 2


def _attention(scope, input, attention_hidden_dim):  # input: [batch_size, n_word, embedding_size]
    with tf.variable_scope(scope):
        attention_weight = tf.layers.dense(input, attention_hidden_dim, name='weight', use_bias=False,
                                           activation=tf.nn.tanh)

        attention_weight_scale = tf.get_variable(dtype=tf.float32, shape=[attention_hidden_dim], name='weight_scale')

        attention_weight = tf.tensordot(attention_weight, attention_weight_scale, axes=[2, 0])  # Shimaoka, ACL2017

        attention_weight = tf.nn.softmax(attention_weight)

        output = tf.nn.relu(tf.reduce_sum(tf.multiply(input, tf.expand_dims(attention_weight, -1)), 1))
        return output


def _self_attention(scope, input, mode, pos_encoding):
    _hparams.pos = "timing" if pos_encoding else "none"

    with tf.variable_scope(scope):
        input = tf.expand_dims(input, 2)
        encoder = None
        if mode == tf.estimator.ModeKeys.TRAIN:
            encoder = transformer.TransformerEncoder(_hparams, mode=tf.estimator.ModeKeys.TRAIN)
        elif mode == tf.estimator.ModeKeys.PREDICT:
            encoder = transformer.TransformerEncoder(_hparams, mode=tf.estimator.ModeKeys.PREDICT)
        else:
            raise Exception('invalid mode')
        output = encoder({"inputs": input, "targets": 0, "target_space_id": 0})
        return flatten4d3d(output[0])


def _description_encoder(scope, word_embedding_t, description, mode, pos_encoding):
    with tf.variable_scope(scope):
        entity_type_emb = tf.nn.embedding_lookup(word_embedding_t, description)
        entity_type_emb = tf.layers.dense(entity_type_emb, _hparams.hidden_size, name='project', use_bias=True)

        transformer_output = _self_attention('self_attention', entity_type_emb, mode, pos_encoding)

        # attention
        entity_encoded = _attention('attention', transformer_output, feed_forward_dim_small)

        # feed forward
        return tf.layers.dense(entity_encoded, feed_forward_dim_medium, name='feed_forward', use_bias=True,
                               activation=tf.nn.relu)


def get_model(word_embedding, mode):
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
    entity_encoded = _description_encoder('entity_type_desc', word_embedding_t, entity_type_desc, mode, True)

    # encode quantity desc
    quantity_encoded = _description_encoder('quantity_desc', word_embedding_t, quantity_desc, mode, False)

    # fusion
    with tf.variable_scope('fusion'):
        fusion_bias = tf.get_variable('fusion_bias', shape=[], dtype=tf.float32)

        # score = tf.reduce_sum(tf.multiply(entity_encoded, quantity_encoded), 1) + fusion_bias
        both_encoded = tf.concat([entity_encoded, quantity_encoded], axis=-1)
        both_encoded = tf.layers.dense(both_encoded, feed_forward_dim_medium, name='feed_forward', use_bias=True, activation=tf.nn.relu)
        both_encoded = tf.layers.dense(both_encoded, feed_forward_dim_small, name='feed_forward_2', use_bias=True, activation=tf.nn.relu)
        fusion_scale = tf.get_variable(dtype=tf.float32, shape=[feed_forward_dim_small], name='fusion_scale')
        score = tf.reduce_sum(tf.multiply(both_encoded, fusion_scale), 1) + fusion_bias

    # optimizer
    with tf.variable_scope('optimizer'):
        if tf.test.is_gpu_available() and _n_GPUs > 1:
            with tf.device('/device:GPU:1'):
                n_true = tf.reduce_sum(tf.abs(tf.cast(tf.less(score, 0), tf.float32) - label))
                loss = tf.reduce_sum(tf.nn.sigmoid_cross_entropy_with_logits(labels=label, logits=score))
                prob = tf.sigmoid(score)
                return (entity_type_desc, quantity_desc, label), loss, tf.train.AdamOptimizer(learning_rate).minimize(
                    loss), n_true, prob
        else:
            n_true = tf.reduce_sum(tf.abs(tf.cast(tf.less(score, 0), tf.float32) - label))
            loss = tf.reduce_sum(tf.nn.sigmoid_cross_entropy_with_logits(labels=label, logits=score))
            prob = tf.sigmoid(score)
            return (entity_type_desc, quantity_desc, label), loss, tf.train.AdamOptimizer(learning_rate).minimize(
                loss), n_true, prob
