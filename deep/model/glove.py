import os

import numpy as np

from model.config import embedding_size

_glove_path = os.path.join('/home/hvthinh/Qsearch/resources/glove', 'glove.6B.' + str(embedding_size) + 'd.txt')


def load_glove():
    dict = {}
    embedding = [[0.0 for i in range(embedding_size)]] # first index is 'nothing' token

    print('loading Glove from %s' % _glove_path)
    with open(_glove_path) as f:
        for line in f:
            arr = line.strip().split()
            dict[arr[0]] = len(dict)
            embedding.append([float(v) for v in arr[1:]])

    return dict, np.asarray(embedding, dtype=np.float32)


if __name__ == '__main__':
    dict, embedding = load_glove()
    print(embedding.shape)
