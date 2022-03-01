import gzip
import json
import math
import multiprocessing as mp
import os
import time

import requests
from nltk.stem import PorterStemmer
from nltk.tokenize import word_tokenize

from quantity.kb import parse

_SERVER_HOST = 'http://varuna:10000'
_IDS_ENDPOINT = _SERVER_HOST + '/ids'
_CONTENT_ENDPOINT = _SERVER_HOST + '/get'

_headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36'
}


def _get_all_ids():
    return json.loads(requests.get(_IDS_ENDPOINT, headers=_headers).content)


def _get_content(id):
    return json.loads(requests.get(_CONTENT_ENDPOINT, params={'id': id}, headers=_headers).content)


def _qt_recog_func(id):
    content = _get_content(id)
    qts = []
    for u in parse(content.pop('content')):
        qts.append({
            'span': u.span,
            'surface': u.surface,
            'value': u.value,
            'unit': str(u.unit),
            'kb_unit': None if u.kb_unit is None else {
                'entity': u.kb_unit.entity,
                'wd_entry': u.kb_unit.wd_entry,
                'si_unit': u.kb_unit.si_unit,
                'conversion_to_si': u.kb_unit.conversion_to_si
            }
        })
    content['qts'] = qts
    return content


def _recognize_quantities(output_file):
    ids = _get_all_ids()

    f = gzip.open(output_file, 'wt')
    f.write('[\n')

    n_printed = 0
    start = time.time()
    with mp.Pool(64) as pool:
        for doc in pool.imap_unordered(_qt_recog_func, ids):
            f.write(json.dumps(doc))
            n_printed += 1
            if n_printed > 0:
                f.write(',\n')
            if time.time() > start + 10:
                print('\rProcessed: {}'.format(n_printed))
                start = time.time()

    f.write('\n]')
    f.close()


_STEMMER = PorterStemmer()


# augmented frequency: tf = 0.5 + 0.5 * (f / max_f)
def _tf_set(content):
    tf = {}
    tokenized = word_tokenize(content)
    for w in tokenized:
        stemmed = _STEMMER.stem(w)
        if stemmed in tf:
            tf[stemmed] += 1
        else:
            tf[stemmed] = 1

    m = max(tf.values())
    for w in tf:
        tf[w] = 0.5 + 0.5 * tf[w] / m
    return tf


def _wordset_stemming_func(id):
    return _tf_set(_get_content(id).pop('content')).keys()


def _create_df_wikipedia():
    ids = _get_all_ids()
    count = {
        '_N_DOC': len(ids)
    }

    n_printed = 0
    start = time.time()
    with mp.Pool(64) as pool:
        for doc in pool.imap_unordered(_wordset_stemming_func, ids):
            for w in doc:
                if w not in count:
                    count[w] = 1
                else:
                    count[w] += 1
            n_printed += 1
            if time.time() > start + 10:
                print('\rProcessed: {}'.format(n_printed))
                start = time.time()

    with gzip.open(os.path.join(os.path.dirname(__file__), 'df_wiki.gz'), 'wt') as f:
        f.write(json.dumps(count))


class IDF:
    _DEFAULT_IDF = None
    _ROBERTSON_IDF = None
    _OOV_DEFAULT_IDF = None
    _OOV_ROBERTSON_IDF = None
    _MIN_IDF = 1e-6

    @staticmethod
    def _load_idf_wikipedia():
        IDF._DEFAULT_IDF = {}
        IDF._ROBERTSON_IDF = {}

        df = json.loads(gzip.open(os.path.join(os.path.dirname(__file__), 'df_wiki.gz'), 'rt').read())

        n_doc = df['_N_DOC']
        for w, f in df.items():
            if w == '_N_DOC':
                continue
            IDF._DEFAULT_IDF[w] = max(IDF._MIN_IDF, math.log(n_doc / (f + 1)))
            IDF._ROBERTSON_IDF[w] = max(IDF._MIN_IDF, math.log10(n_doc - f + 0.5) / (f + 0.5))

        IDF._OOV_DEFAULT_IDF = max(IDF._MIN_IDF, math.log(n_doc / (0 + 1)))  # df = 0
        IDF._OOV_ROBERTSON_IDF = max(IDF._MIN_IDF, math.log10((n_doc - 0 + 0.5) / (0 + 0.5)))  # df = 0

    @staticmethod
    def get_default_idf(word, allow_oov=True, stemming=True):
        idf = IDF._DEFAULT_IDF.get(_STEMMER.stem(word) if stemming else word)
        if idf is not None:
            return idf
        else:
            return IDF._OOV_DEFAULT_IDF if allow_oov else IDF._MIN_IDF

    @staticmethod
    def get_robertson_idf(word, allow_oov=True, stemming=True):
        idf = IDF._ROBERTSON_IDF.get(_STEMMER.stem(word) if stemming else word)
        if idf is not None:
            return idf
        else:
            return IDF._OOV_ROBERTSON_IDF if allow_oov else IDF._MIN_IDF


IDF._load_idf_wikipedia()


def td_idf_doc_sim(content_1, content_2):
    tf_1 = _tf_set(content_1)
    tf_2 = _tf_set(content_2)

    for w in tf_1:
        tf_1[w] *= IDF.get_default_idf(w, stemming=False)
    for w in tf_2:
        tf_2[w] *= IDF.get_default_idf(w, stemming=False)

    dot_prod = sum([tf_1.get(w, 0) * tf_2.get(w, 0) for w in set.union(set(tf_1.keys()), set(tf_2.keys()))])
    len_1 = math.sqrt(sum([v ** 2 for v in tf_1.values()]))
    len_2 = math.sqrt(sum([v ** 2 for v in tf_2.values()]))

    return dot_prod / len_1 / len_2


if __name__ == '__main__':
    # _create_df_wikipedia()
    # _recognize_quantities('/GW/D5data-14/hvthinh/quid/wikipedia_quantities.gz')
    pass
