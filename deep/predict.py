import os

os.environ["CUDA_VISIBLE_DEVICES"] = "-1"

import sys
import tensorflow as tf

from model.data import *
from model.node import get_model

print("GPU Available: ", tf.test.is_gpu_available())

word_dict, embedding = get_glove()  # if word not in dict then index should be len(embedding)

data_path = './data'

(entity_type_desc, quantity_desc, _), _, _, _, prob = get_model(embedding, tf.estimator.ModeKeys.PREDICT)

saver = tf.train.Saver()

sess = tf.Session(config=tf.ConfigProto(allow_soft_placement=True))
saver.restore(sess, os.path.join(data_path, 'model.ckpt'))
print('__ready_to_predict__')


def get_score(entity_desc_text, quantity_desc_text):
    data = [(entity_desc_text, quantity_desc_text)]
    et, qt, _ = convert_input_to_tensor(data, word_dict, is_train=False)
    return sess.run([prob], feed_dict={
        entity_type_desc: et,
        quantity_desc: qt
    })[0][0]


def get_scores(entity_desc_text_arr, quantity_desc_text):
    data = []
    for entity_desc_text in entity_desc_text_arr:
        data.append((entity_desc_text, quantity_desc_text))
    et, qt, _ = convert_input_to_tensor(data, word_dict, is_train=False)
    p = sess.run([prob], feed_dict={
        entity_type_desc: et,
        quantity_desc: qt
    })[0]

    return p


if __name__ == "__main__":
    # print(get_best_entity_desc_text(['team', 'stadium', 'dog'], 'capacity'))
    # print(get_best_entity_desc_text(['team', 'stadium', 'dog'], 'number of foots'))
    while True:
        # read sentence
        line = sys.stdin.readline().strip()
        d = json.loads(line)  # {'entities_desc': [<EDS>], 'quantity_desc': '<QD>'}
        out = get_scores(d["type_desc"], d['quantity_desc'])
        print("%s" % json.dumps(out.tolist()))
