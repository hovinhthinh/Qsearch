import requests


def tag(sent, etags, qpos, end_point='http://varuna:2022/tag'):
    params = {
        'sent': ' '.join(sent),
        'etags': ' '.join(etags),
        'qpos': qpos
    }
    r = requests.get(url=end_point, params=params).json()
    return r['tags'], r['conf']


sent = ['BMW', 'i8', 'has', 'a', 'battery', 'range', 'of', '<QUANTITY>', 'and', 'costs', '<QUANTITY>', 'in', 'Germany']
etags = ['B', 'I', 'O', 'O', 'O', 'O', 'O', 'O', 'O', 'O', 'O', 'O', 'B']

print(tag(sent, etags, 7))
print(tag(sent, etags, 10))
