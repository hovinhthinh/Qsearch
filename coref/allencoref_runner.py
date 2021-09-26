# python==3.7, allennlp==2.1.0, allennlp-models==2.1.0
import json
import resource
import sys


def limit_memory(mem_in_GB):
    soft, hard = resource.getrlimit(resource.RLIMIT_AS)
    resource.setrlimit(resource.RLIMIT_AS, (mem_in_GB * 1024 * 1024 * 1024, hard))


# RAM-hungry
limit_memory(20)

from allennlp.predictors.predictor import Predictor

predictor = Predictor.from_path("https://storage.googleapis.com/allennlp-public-models/coref-spanbert-large-2021.03.10.tar.gz")
# document = [['I', 'have', 'a', 'dog', '.'], ['He', 'is', 'cute']]
# print(predictor.predict_instance(predictor._dataset_reader.text_to_instance(document)))


# document example: [['I', 'have', 'a', 'dog', '.'], ['He', 'is', 'cute']]
def do_coref(document):
    res = []
    for c in predictor.predict_instance(predictor._dataset_reader.text_to_instance(document))['clusters']:
        cluster = []
        for start, end in c:
            for i, sent in enumerate(document):
                if start >= len(sent):
                    start -= len(sent)
                    end -= len(sent)
                else:
                    cluster.append({'sent': i, 'start': start, 'end': end + 1})
                    break
        res.append(cluster)
    return res


if __name__ == "__main__":
    print('__coref_ready__')

    while True:
        line = sys.stdin.readline().strip()
        d = json.loads(line)  # {'document': <content>}
        out = do_coref(d['document'])
        print("__interactive_result__\t%s" % json.dumps(out), flush=True)
