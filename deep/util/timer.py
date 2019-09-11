from datetime import timedelta
from timeit import default_timer as timer


class _timer(object):

    def __init__(self, msg):
        self._msg = msg

    def __enter__(self):
        self._start = timer()
        return self

    def __exit__(self, *args):
        print("[TIME_ELAPSED]-%s: %s" % (self._msg, timedelta(seconds=timer() - self._start)))


def elap(msg):
    return _timer(msg)
