from pymongo import MongoClient

_client = None
_db = None


def open_client():
    global _client, _db
    if _client is not None:
        _client.close()
    _client = MongoClient('d5io03', 27017)
    _db = _client['quid']


def close_client():
    global _client, _db
    _client.close()
    _client = _db = None


def use_db(name='quid'):
    global _db
    _db = _client[name]


def get_collection(name):
    return _db[name]
