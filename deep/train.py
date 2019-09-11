import math
import tensorflow as tf

import util.timer as timer
from model.data import *
from model.node import get_model

print("GPU Available: ", tf.test.is_gpu_available())

word_dict, embedding = get_glove()  # if word not in dict then index should be len(embedding)

##########
data_path = './data'
training_data = get_training_data(os.path.join(data_path, 'train.gz'))
# training_data = []

(entity_type_desc, quantity_desc, label), loss, optimizer, n_true = get_model(embedding)

# print('nodes:')
# for n in tf.get_default_graph().as_graph_def().node:
#     print(n.name)

init_op = tf.global_variables_initializer()

saver = tf.train.Saver()

print('start training')
with tf.Session(config=tf.ConfigProto(allow_soft_placement=True)) as sess:
    sess.run(init_op)

    best_lost = math.inf
    for epoch in range(max_num_epoches):
        with timer.elap('epoch %d' % (epoch)):
            data = sample_epoch_training_data(training_data)
            # data = [('asian country', 'population', 1)]
            et, qt, ls = convert_input_to_tensor(data, word_dict)

            n_chunk = (len(ls) - 1) // batch_size + 1
            batches = [np.array_split(et, n_chunk), np.array_split(qt, n_chunk), np.array_split(ls, n_chunk)]

            epoch_loss = 0
            prec = 0
            for i in range(n_chunk):
                _, nt, bl = sess.run([optimizer, n_true, loss], feed_dict={
                    entity_type_desc: batches[0][i],
                    quantity_desc: batches[1][i],
                    label: batches[2][i],
                })
                epoch_loss += bl / len(ls)
                prec += nt / len(ls)

            print('epoch %d loss: %.5f prec: %.3f' % (epoch, epoch_loss, prec))

            if (epoch % save_model_frequency == (save_model_frequency - 1)) and (epoch_loss < best_lost):
                best_lost = epoch_loss
                print('saving model')
                saver.save(sess, os.path.join(data_path, 'model.ckpt'))
