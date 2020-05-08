# python 3.6, spacy 2.1.3, neuralcoref 4.0
import json
import neuralcoref
import resource
import spacy
import sys

nlp = spacy.load('en')
# Add neural coref to SpaCy's pipe
neuralcoref.add_to_pipe(nlp)


def limit_memory(mem_in_GB):
    soft, hard = resource.getrlimit(resource.RLIMIT_AS)
    resource.setrlimit(resource.RLIMIT_AS, (mem_in_GB * 1024 * 1024 * 1024, hard))


# paragraph should be tokenized
def do_coref(paragraph):
    doc = nlp(paragraph)

    res = []
    for c in doc._.coref_clusters:
        cluster = []
        for m in c.mentions:
            cluster.append({'start': m.start_char, 'end': m.end_char, 'text': m.text})
        res.append(cluster)
    return res


if __name__ == "__main__":
    limit_memory(4)
    print('__coref_ready__')

    while True:
        # read sentence
        line = sys.stdin.readline().strip()
        d = json.loads(line)  # {'paragraph': <content>}
        out = do_coref(d['paragraph'])
        print("__interactive_result__\t%s" % json.dumps(out), flush=True)
