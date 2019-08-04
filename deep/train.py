import math
import tensorflow as tf

from model.data import *
from model.node import get_model

dict, embedding = load_glove()  # if word not in dict then index should be len(embedding)

##########
data_path = 'data'

data = load_training_data(data_path)

(entity_type_desc, quantity_desc, label), loss, optimizer = get_model(embedding)

init_op = tf.global_variables_initializer()

saver = tf.train.Saver()
with tf.Session() as sess:
    sess.run(init_op)

    best_lost = math.inf
    for epoch in range(max_num_epoches):
        e, q, l = convert_input_to_tensor(data, dict, embedding)
        batches = np.array_split(e, batch_size), np.array_split(q, batch_size), np.array_split(l, batch_size)
        epoch_loss = 0
        for i in range(len(batches[0])):
            bl, _ = sess.run([loss, optimizer], feed_dict={
                entity_type_desc: batches[0][i],
                quantity_desc: batches[1][i],
                label: batches[2][i],
            })
            epoch_loss += bl / len(batches)

        print('epoch %d loss: %.5f' % (epoch, epoch_loss))

        if (epoch % save_model_frequency == (save_model_frequency - 1)) and (epoch_loss < best_lost):
            best_lost = epoch_loss
            print('saving model')
            saver.save(sess, os.path.join(data_path, 'model.ckpt'))
