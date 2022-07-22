import numpy

import theano
from neural_srl.shared.numpy_utils import random_normal_initializer
from theano import config as theano_config

floatX = theano_config.floatX


def numpy_floatX(data):
    return numpy.asarray(data, dtype=floatX)


def get_variable(name, shape, initializer=None, dtype=floatX):
    if initializer != None:
        param = initializer(shape, dtype)
    else:
        param = random_normal_initializer()(shape, dtype)
    print "Initializing ", name, shape

    return theano.shared(value=param, name=name, borrow=True)
