import gzip
import json
import math
import multiprocessing as mp
import os
import time
from functools import lru_cache

from nltk.stem import PorterStemmer
from nltk.tokenize import word_tokenize

from quantity.kb import parse
from util.iterutils import chunk
from util.mongo import get_collection, open_client, close_client
from util.monitor import CounterMonitor

_STEMMER = PorterStemmer()
_QT_TOKEN = '__QT__'

_headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36'
}


def _get_all_ids():
    return [doc['_id'] for doc in get_collection('enwiki-09-2021').find({}, {'_id': 1})]


_get_content_collection = None


@lru_cache
def _get_content(doc_id):
    global _get_content_collection
    if _get_content_collection is None:
        open_client()
        _get_content_collection = get_collection('enwiki-09-2021')
    return _get_content_collection.find_one({'_id': doc_id})


def _convert_to_train_doc(qt, passages):
    content = '\n'.join(passages)
    return {
        'qt': qt,
        'text': '{}{}{}'.format(content[:qt['span'][0]], _QT_TOKEN, content[qt['span'][1]:]),
    }


def index_wikipedia():
    open_client()
    m = CounterMonitor(name='IndexWikipedia')
    m.start()
    collection = get_collection('enwiki-09-2021')
    collection.drop()
    buffer = []
    buffer_size = 128
    for line in gzip.open('/GW/D5data-14/hvthinh/enwiki-09-2021/standardized.json.gz', 'rt'):
        o = json.loads(line.strip())
        buffer.append({
            '_id': o['source'].split('?curid=')[-1],
            'title': o['title'],
            'source': o['source'],
            'passages': o['content'].split('\n'),
        })
        m.inc()
        if len(buffer) == buffer_size:
            collection.insert_many(buffer)
            buffer.clear()
    collection.insert_many(buffer)
    m.shutdown()


def _qt_recog_func(doc_id):
    content = _get_content(doc_id)
    passages = content['passages']
    qts = []
    for i, p in enumerate(passages):
        for u in parse(p):
            qts.append({
                'p_idx': i,
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
    return {
        'doc_id': content['_id'],
        'source': content['source'],
        'qts': qts
    }


def _export_quantities_to_mongo(quantity_file):
    m = CounterMonitor(name='LoadQuantities')
    m.start()

    domain_2_qts = {}
    with gzip.open(quantity_file, 'rt') as f:
        for line in f:
            m.inc()
            try:
                doc = json.loads(line.strip()[:-1])
            except:
                print('Err:', line)
                continue
            for qt in doc['qts']:
                unit = qt['kb_unit']
                if unit is not None:
                    unit = unit['si_unit'] if unit['si_unit'] is not None else unit['entity']
                export_qt = {
                    'doc_id': doc['doc_id'],
                    'source': doc['source']
                }
                export_qt.update(qt)

                if unit not in domain_2_qts:
                    domain_2_qts[unit] = []
                domain_2_qts[unit].append(export_qt)
    m.shutdown()

    domain_2_qts = list(domain_2_qts.items())
    domain_2_qts.sort(reverse=True, key=lambda k: len(k[1]))

    print('Domain stats:')
    for l in domain_2_qts:
        print(l[0], len(l[1]))

    open_client()

    def _export_domain_to_mongo(domain, qts):
        for qt in qts:
            scale = 1 if qt['kb_unit'] is None or qt['kb_unit']['conversion_to_si'] is None \
                else qt['kb_unit']['conversion_to_si']
            qt['n_value'] = qt['value'] * scale
        qts.sort(key=lambda k: k['n_value'])

        collection = get_collection('.'.join(['quantities', str(domain)]))
        collection.drop()
        collection.create_index('_id')

        m = CounterMonitor('ExportQuantitiesToMongo-{}'.format(domain), len(qts))
        m.start()
        for ch in chunk(qts, 128):
            collection.insert_many(ch)
            m.inc(len(ch))

    for domain, qts in domain_2_qts:
        if len(qts) >= 10000:
            _export_domain_to_mongo(domain, qts)


def _recognize_quantities(output_file):
    open_client()
    ids = _get_all_ids()
    close_client()

    f = gzip.open(output_file, 'wt')
    f.write('[\n')

    n_printed = 0
    start = time.time()
    with mp.Pool(256) as pool:
        for doc in pool.imap_unordered(_qt_recog_func, ids):
            f.write(json.dumps(doc))
            n_printed += 1
            if n_printed > 0:
                f.write(',\n')
            if time.time() > start + 10:
                print('\rProcessed: {}'.format(n_printed))
                start = time.time()
    f.write(']\n')
    f.close()


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
    if len(tf) > 0:
        m = max(tf.values())
        for w in tf:
            tf[w] = 0.5 + 0.5 * tf[w] / m
    return tf


def _wordset_stemming_func(doc_id):
    return list(_tf_set(' '.join(_get_content(doc_id).pop('passages'))).keys())


def _create_df_wikipedia():
    open_client()
    ids = _get_all_ids()
    close_client()
    count = {
        '_N_DOC': len(ids)
    }

    n_printed = 0
    start = time.time()
    with mp.Pool(256) as pool:
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


def tdidf_doc_sim(content_1, content_2):
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


def _numeric_dist(a, b):
    return 0 if a == b == 0 else abs(a - b) / max(abs(a), abs(b))


_QTS = None


def _qt_pair_eval_func(input):
    l, r, thresholds = input
    output = []
    for i in range(l, r):
        ca = _get_content(_QTS[i]['doc_id'])['passages']
        for j in range(i + 1, r):
            rel_dist = _numeric_dist(_QTS[i]['n_value'], _QTS[j]['n_value'])
            if rel_dist > thresholds['max_candidate_relative_qt_dist']:
                break
            if _QTS[i]['doc_id'] == _QTS[j]['doc_id']:
                continue
            cb = _get_content(_QTS[j]['doc_id'])['passages']
            # TF-IDF par sim
            par_sim = tdidf_doc_sim(ca[_QTS[i]['p_idx']], cb[_QTS[j]['p_idx']])
            if par_sim < thresholds['min_tfidf_par_sim']:
                continue
            # TF-IDF doc sim
            doc_sim = tdidf_doc_sim(' '.join(ca), ' '.join(cb))
            if doc_sim < thresholds['min_tfidf_doc_sim']:
                continue
            output.append({
                'doc_1': _convert_to_train_doc(_QTS[i], ca),
                'doc_2': _convert_to_train_doc(_QTS[j], cb),
                'tfidf_doc_sim': doc_sim,
                'tfidf_par_sim': par_sim,
                'qt_dist': rel_dist,
                'cl_size': r - l,
            })
    return output


def _generate_positive_training_pairs(domain,
                                      max_cluster_size=100,
                                      max_onchain_relative_qt_dist=0,
                                      min_tfidf_doc_sim=0.1,
                                      min_tfidf_par_sim=0.1,
                                      max_candidate_relative_qt_dist=0,
                                      ):
    open_client()
    qts = [doc for doc in get_collection('.'.join(['quantities', domain])).find({})]
    close_client()

    for qt in qts:
        qt.pop('_id')

    global _QTS
    _QTS = qts

    eval_input = []

    r = 0
    for l in range(len(qts)):
        if l < r:
            continue
        r = l + 1
        while r < len(qts) and _numeric_dist(qts[r - 1]['n_value'], qts[r]['n_value']) <= max_onchain_relative_qt_dist:
            r += 1
        if r - l > max_cluster_size or r - l == 1:
            continue
        eval_input.append((l, r, {
            'min_tfidf_doc_sim': min_tfidf_doc_sim,
            'min_tfidf_par_sim': min_tfidf_par_sim,
            'max_candidate_relative_qt_dist': max_candidate_relative_qt_dist,
        }))

    m = CounterMonitor('GeneratePositivePairs-{}'.format(domain), len(eval_input))
    m.start()
    cnt = 0
    with mp.Pool(256) as pool:
        pool_output = pool.imap_unordered(_qt_pair_eval_func, eval_input)

        open_client()
        collection = get_collection('.'.join(['train', 'positive', domain]))
        collection.drop()
        collection.create_index('_id')

        for pos_samples in pool_output:
            for sample in pos_samples:
                cnt += 1
                sample['_id'] = cnt
            if len(pos_samples) > 0:
                collection.insert_many(pos_samples)
            m.inc()


def _sample_collection(source, destination,
                       min_tdidf_doc_sim=0,
                       min_tdidf_par_sim=0,
                       n=50):
    open_client()
    source = get_collection(source)
    destination = get_collection(destination)
    destination.drop()
    filtered = source.aggregate([
        {'$match': {'tfidf_doc_sim': {'$gte': min_tdidf_doc_sim},
                    'tfidf_par_sim': {'$gte': min_tdidf_par_sim}}},
        {'$sample': {'size': n}}
    ])
    destination.insert_many(filtered)


def _calculate_precision(collection):
    open_client()
    total = 0
    labeled = 0
    positive = 0
    for doc in get_collection(collection).find():
        total += 1
        if 'ok' not in doc:
            continue
        labeled += 1
        positive += (1 if doc['ok'] else 0)
    print('Precision: {} ({}/{}) -- from Total: {}'.format(None if labeled == 0 else positive / labeled, positive,
                                                           labeled, total))


if __name__ == '__main__':
    # index_wikipedia()
    # _create_df_wikipedia()
    # _recognize_quantities('/GW/D5data-14/hvthinh/quid/wikipedia_quantities.gz')
    # _export_quantities_to_mongo('/GW/D5data-14/hvthinh/quid/wikipedia_quantities.gz')

    # IDF._load_idf_wikipedia()
    # _generate_positive_training_pairs('<Metre>')

    _sample_collection('train.positive.<Metre>', 'train.positive.<Metre>.shuf')
    # print(_calculate_precision('train.positive.<Metre>.shuf'))
