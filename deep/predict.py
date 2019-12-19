import argparse
import os

parser = argparse.ArgumentParser()
parser.add_argument("-g", "--gpu", help="force using gpu", action="store_true")
parser.add_argument("-d", "--device", help="gpu device index")
args = parser.parse_args()

if not args.gpu:
    os.environ["CUDA_VISIBLE_DEVICES"] = "-1"
else:
    if args.device:
        os.environ["CUDA_VISIBLE_DEVICES"] = args.device

import sys
import tensorflow as tf

from model.data import *
from model.node import get_model

print("GPU Available: ", tf.test.is_gpu_available())

word_dict, embedding = get_glove()  # if word not in dict then index should be len(embedding)

data_path = './data'

(entity_type_desc, quantity_desc, _), _, _, _, prob = get_model(embedding, tf.estimator.ModeKeys.PREDICT)

saver = tf.train.Saver()

# NUM_PARALLEL_EXEC_UNITS = 10
# os.environ["OMP_NUM_THREADS"] = "NUM_PARALLEL_EXEC_UNITS"
# os.environ["KMP_BLOCKTIME"] = "30"
# os.environ["KMP_SETTINGS"] = "1"
# os.environ["KMP_AFFINITY"] = "granularity=fine,verbose,compact,1,0"
# config = tf.ConfigProto(intra_op_parallelism_threads=NUM_PARALLEL_EXEC_UNITS, inter_op_parallelism_threads=2,
#                         allow_soft_placement=True, device_count={'CPU': NUM_PARALLEL_EXEC_UNITS})

config = tf.ConfigProto(allow_soft_placement=True)

sess = tf.Session(config=config)
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
