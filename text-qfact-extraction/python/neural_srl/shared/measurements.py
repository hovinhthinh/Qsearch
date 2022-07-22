import datetime
import time


class Timer:
    def __init__(self, name, active=True):
        self.name = name if active else None

    def __enter__(self):
        self.start = time.time()
        self.last_tick = self.start
        if self.name is not None:
            print("{}.".format(self.name))
        return self

    def __exit__(self, *args):
        if self.name is not None:
            print("{} duration was {}.".format(self.name, self._readable(time.time() - self.start)))

    def _readable(self, seconds):
        return str(datetime.timedelta(seconds=int(seconds)))

    def tick(self, message):
        current = time.time()
        print("{} took {} ({} since last tick).".format(message, self._readable(current - self.start),
                                                        self._readable(current - self.last_tick)))
        self.last_tick = current
