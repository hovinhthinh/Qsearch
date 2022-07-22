import os

import random
from os.path import join

ROOT_DIR = join(os.path.dirname(os.path.abspath(__file__)), '../../../')

RANDOM_SEED = 120993
random.seed(RANDOM_SEED)

WORD_EMBEDDINGS = {"glove50": join(ROOT_DIR, '../resources/glove/glove.6B.50d.txt'),
                   "glove100": join(ROOT_DIR, '../resources/glove/glove.6B.100d.txt'),
                   "glove200": join(ROOT_DIR, '../resources/glove/glove.6B.200d.txt'),
                   "glove300": join(ROOT_DIR, '../resources/glove/glove.6B.300d.txt')}

START_MARKER = '<S>'
END_MARKER = '</S>'
UNKNOWN_TOKEN = '<UNKNOWN>'
QUANTITY_TOKEN = '<QUANTITY>'
TIME_TOKEN = '<TIME>'

ENTITY_LABEL= 'E'
CONTEXT_LABEL = 'X'
OTHER_LABEL = 'O'

TEMP_DIR = join(ROOT_DIR, 'temp')

if not os.path.exists(TEMP_DIR):
    os.makedirs(TEMP_DIR)
