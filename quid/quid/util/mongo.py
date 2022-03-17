from pymongo import MongoClient

_client = MongoClient('d5io03', 27017)
_db = _client['quid']


def use_db(name):
    global _db
    _db = _client[name]


def get_collection(name):
    return _db[name]
