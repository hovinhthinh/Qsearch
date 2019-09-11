import tensorflow as tf

from model.data import *
from model.node import get_model

print("GPU Available: ", tf.test.is_gpu_available())

word_dict, embedding = get_glove()  # if word not in dict then index should be len(embedding)

data_path = './data'

(entity_type_desc, quantity_desc, _), _, _, _, prob = get_model(embedding)

init_op = tf.global_variables_initializer()

saver = tf.train.Saver()

sess = tf.Session(config=tf.ConfigProto(allow_soft_placement=True))
saver.restore(sess, os.path.join(data_path, 'model.ckpt'))
print('__ready_to_predict__')


def get_score(entity_desc_text, quantity_desc_text):
    data = [(entity_desc_text, quantity_desc_text)]
    et, qt, _ = convert_input_to_tensor(data, word_dict)
    return sess.run([prob], feed_dict={
        entity_type_desc: et,
        quantity_desc: qt
    })[0][0]


if __name__ == "__main__":
    print(get_score('country', 'population'))
    print(get_score('european country', 'number of cities'))
    print(get_score('hybrid car', 'salary'))
