import sys
import time
from abc import abstractmethod, ABC
from threading import Thread, Event, Lock


class _Progress:
    def __init__(self):
        self.et_d = self.et_h = self.et_m = self.et_s = 0
        self.current = self.speed = 0
        self.total = self.percent = -1
        self.eta_d = self.eta_h = self.eta_m = self.eta_s = -1


class Monitor(ABC, Thread):
    def __init__(self, name=None, total=-1, period=10, out=sys.stdout):
        Thread.__init__(self)

        self._name = name
        self._total = total
        self._period = period
        self._out = out

        self._stop_event = Event()
        self._stopped = False

        self._last_tick = 0

    def set_total(self, total):
        self._total = total

    @abstractmethod
    def get_current(self):
        pass

    def _log(self, str):
        self._out.write(str)
        self._out.write('\n')

    def log_start(self):
        self._log('MONITOR [{}]: STARTED.'.format(self._name))

    def log_done(self):
        self._log('MONITOR [{}]: DONE'.format(self._name))

    def log_shutdown(self):
        self._log('MONITOR [{}]: SHUTDOWN.'.format(self._name))

    def log_progress(self, progress: _Progress):
        current_str = '{}/{}'.format(progress.current, '--' if progress.total == -1 else progress.total)
        percent_str = '' if progress.total == -1 else ' {:.2f}%'.format(progress.percent)
        speed_str = '{:.2f}/sec'.format(progress.speed)

        et_str = '{}d {:02d}:{:02d}:{:02d}'.format(progress.et_d, progress.et_h, progress.et_m, progress.et_s)
        eta_str = '--d --:--:--' if progress.total == -1 else \
            '{}d {:02d}:{:02d}:{:02d}'.format(progress.eta_d, progress.eta_h, progress.eta_m, progress.eta_s)
        self._log('MONITOR [{}]: et: {}    current: {}{}    speed: {}    eta: {}'.format(
            self.name, et_str, current_str, percent_str, speed_str, eta_str))

    def shutdown(self):
        if not self._stopped:
            self._stopped = True
            self._stop_event.set()
            return True
        else:
            return False

    def run(self):
        self.log_start()

        if self._total == 0:
            self._stopped = True
            self.log_done()
            return

        start_time = time.time()
        while not self._stopped:
            self._stop_event.wait(self._period)
            current = self.get_current()
            if current < 0 or 0 <= self._total < current:
                raise Exception('get_current() is invalid.')

            et_sec = int(time.time() - start_time)
            progress = _Progress()
            progress.et_d = int(et_sec / 24 / 3600)
            progress.et_h = int((et_sec % (24 * 3600)) / 3600)
            progress.et_m = int((et_sec % 3600) / 60)
            progress.et_s = int(et_sec % 60)
            progress.current = current

            if self._total != -1:
                progress.total = self._total
                progress.percent = current / self._total * 100

            progress.speed = (current - self._last_tick) / self._period

            if abs(progress.speed) >= 1e-2:
                rm_sec = int((self._total - current) / progress.speed)
                progress.eta_d = int(rm_sec / 24 / 3600)
                progress.eta_h = int((rm_sec % (24 * 3600)) / 3600)
                progress.eta_m = int((rm_sec % 3600) / 60)
                progress.eta_s = int(rm_sec % 60)

            self.log_progress(progress)

            self._last_tick = current

            if current == self._total:
                self._stopped = True

        current = self.get_current()

        if current < 0 or 0 <= self._total < current:
            raise Exception('get_current() is invalid.')

        if current == self._total:
            self.log_done()
        else:
            self.log_shutdown()


class CounterMonitor(Monitor):
    def __init__(self, name=None, total=-1, period=10, out=sys.stdout):
        super().__init__(name, total, period, out)
        self._curr = 0
        self._lock = Lock()

    def inc(self, count=1):
        with self._lock:
            self._curr += count
            return self._curr

    def get_current(self):
        return self._curr
