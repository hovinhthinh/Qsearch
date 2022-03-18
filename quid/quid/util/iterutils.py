from itertools import islice

_no_padding = object()


def chunk(it, size, pad=_no_padding):
    it = iter(it)
    chunker = iter(lambda: tuple(islice(it, size)), ())
    if pad == _no_padding:
        yield from chunker
    else:
        for ch in chunker:
            yield ch if len(ch) == size else ch + (pad,) * (size - len(ch))
