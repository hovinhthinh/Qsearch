from constants import *
from dictionary import Dictionary


def _get_srl_sentences(filepath, use_se_marker=False):
    """ Read tokenized SRL sentences from file.
      File format: {predicate_id} [word0, word1 ...] ||| [label0, label1 ...]
      Return:
        A list of sentences, with structure: [[words], predicate, [labels]]
    """
    sentences = []
    with open(filepath) as f:
        for line in f.readlines():
            if line.find('\t') != -1:  # the first arg is the original sentence
                line = line.split('\t')[1]
            inputs = line.strip().split('|||')
            lefthand_input = inputs[0].strip().split()
            # If gold tags are not provided, create a sequence of dummy tags.
            righthand_input = inputs[2].strip().split() if len(inputs) > 2 else ['O' for _ in lefthand_input[1:]]
            predicate = int(lefthand_input[0])
            entities_ft = inputs[1].strip().split()

            if use_se_marker:
                words = [START_MARKER] + lefthand_input[1:] + [END_MARKER]
                labels = [None] + righthand_input + [None]
                entities_ft = [None] + entities_ft + [None]
            else:
                words = lefthand_input[1:]
                labels = righthand_input
            sentences.append((words, predicate, entities_ft, labels))
    return sentences


def _get_pretrained_embeddings(filepath):
    """ Return: map {word -> [embeding dimensions]}
    """
    embeddings = dict()
    with open(filepath, 'r') as f:
        for line in f:
            info = line.strip().split()
            embeddings[info[0]] = [float(r) for r in info[1:]]

    embedding_size = len(embeddings.values()[0])
    print 'Embedding size={}'.format(embedding_size)
    embeddings[START_MARKER] = [random.gauss(0, 0.01) for _ in range(embedding_size)]
    embeddings[END_MARKER] = [random.gauss(0, 0.01) for _ in range(embedding_size)]
    for token in [UNKNOWN_TOKEN, TIME_TOKEN, QUANTITY_TOKEN]:
        if not token in embeddings:
            embeddings[token] = [random.gauss(0, 0.01) for _ in range(embedding_size)]
    return embeddings


def _get_srl_features(sentences, config):
    ''' TODO: Support adding more features.
    '''
    feature_names = config.features
    feature_sizes = config.feature_sizes
    use_se_marker = config.use_se_marker

    # [features][sentences][position] 1 or 0
    # [features][2, fsize] TODO: what is 2 here ? number of distinct values ?
    features = []
    feature_shapes = []
    for fname, fsize in zip(feature_names, feature_sizes):
        if fname == "predicate":
            offset = int(use_se_marker)
            features.append([[int(i == sent[1] + offset) for i in range(len(sent[0]))] for sent in sentences])
            feature_shapes.append([2, fsize])
        elif fname == "entity":
            ft = []
            for sent in sentences:
                sent_ft = []
                for ft_str in sent[2]:
                    if ft_str == "B":
                        sent_ft.append(1)
                    elif ft_str == "I":
                        sent_ft.append(2)
                    else:
                        sent_ft.append(0)
                ft.append(sent_ft)
            features.append(ft)
            feature_shapes.append([3, fsize])

    # [sentences][features][position]; [features][2, fsize]
    return (zip(*features), feature_shapes)


def string_sequence_to_ids(str_seq, dictionary, to_lowercase=False, pretrained_embeddings=None):
    """ TODO: If pretrained_embeddings is provided, strings not in the embeddings are considered UNKNOWN_TOKEN.
      Pretrained embeddings is a dictionary from strings to python list.
    """
    ids = []
    for s in str_seq:
        if s is None:
            ids.append(-1)
            continue
        if to_lowercase and s != TIME_TOKEN and s != QUANTITY_TOKEN:
            s = s.lower()
        if (pretrained_embeddings is not None) and not (s in pretrained_embeddings):
            s = UNKNOWN_TOKEN
        ids.append(dictionary.add(s))
    return ids


def get_srl_data(config, train_data_path, dev_data_path, vocab_path=None, label_path=None):
    use_se_marker = config.use_se_marker
    raw_train_sents = _get_srl_sentences(train_data_path, use_se_marker)
    raw_dev_sents = _get_srl_sentences(dev_data_path, use_se_marker)
    word_to_embeddings = _get_pretrained_embeddings(WORD_EMBEDDINGS[config.word_embedding])

    # Prepare word dictionary.
    word_dict = Dictionary(unknown_token=UNKNOWN_TOKEN)
    if use_se_marker:
        word_dict.add_all([START_MARKER, END_MARKER])
    if vocab_path != None:
        with open(vocab_path, 'r') as f_vocab:
            for line in f_vocab:
                word_dict.add(line.strip())
        word_dict.accept_new = False
        print 'Load {} words. Dictionary freezed.'.format(word_dict.size())

    # Prepare label dictionary.
    label_dict = Dictionary()
    if label_path != None:
        with open(label_path, 'r') as f_labels:
            for line in f_labels:
                label_dict.add(line.strip())
        label_dict.set_unknown_token(OTHER_LABEL)
        label_dict.accept_new = False
        print 'Load {} labels. Dictionary freezed.'.format(label_dict.size())

    # Get tokens and labels
    # [sentences][token_ids]
    train_tokens = [string_sequence_to_ids(sent[0], word_dict, True, word_to_embeddings) for sent in raw_train_sents]
    # [sentences][label_ids]
    train_labels = [string_sequence_to_ids(sent[-1], label_dict) for sent in raw_train_sents]

    if label_dict.accept_new:
        label_dict.set_unknown_token(OTHER_LABEL)
        label_dict.accept_new = False

    # [sentences][token_ids]
    dev_tokens = [string_sequence_to_ids(sent[0], word_dict, True, word_to_embeddings) for sent in raw_dev_sents]
    # [sentences][label_ids]
    dev_labels = [string_sequence_to_ids(sent[-1], label_dict) for sent in raw_dev_sents]

    # Get features
    print 'Extracting features'
    # [sentences][features][position]; [features][2, fsize]

    train_features, feature_shapes = _get_srl_features(raw_train_sents, config)
    dev_features, feature_shapes2 = _get_srl_features(raw_dev_sents, config)

    # For additional features. Unused now.
    feature_dicts = []
    for feature in config.features:
        feature_dicts.append(None)

    # [sentences][[token_ids], [feature_1_positions], [feature_2_positions],..., [labels_ids]]
    # e.g. feature 1 is the predicate position
    train_sents = []
    dev_sents = []
    for i in range(len(train_tokens)):
        train_sents.append((train_tokens[i],) + tuple(train_features[i]) + (train_labels[i],))
    for i in range(len(dev_tokens)):
        dev_sents.append((dev_tokens[i],) + tuple(dev_features[i]) + (dev_labels[i],))

    print("Extraced {} words and {} tags".format(word_dict.size(), label_dict.size()))
    print("Max training sentence length: {}".format(max([len(s[0]) for s in train_sents])))
    print("Max development sentence length: {}".format(max([len(s[0]) for s in dev_sents])))

    word_embedding = [word_to_embeddings[w] for w in word_dict.idx2str]

    # [nVocab, embedding_size]
    word_embedding_shape = [len(word_embedding), len(word_embedding[0])]
    return (train_sents, dev_sents, word_dict, label_dict,
            [word_embedding, None, None],
            [word_embedding_shape] + feature_shapes)


def get_srl_test_data(filepath, config, word_dict, label_dict, allow_new_words=True,
                      load_all_pre_trained_word_embedding=False):
    if label_dict.accept_new:
        label_dict.set_unknown_token(OTHER_LABEL)
        label_dict.accept_new = False

    if filepath != None and filepath != '':
        samples = _get_srl_sentences(filepath, config.use_se_marker)
    else:
        samples = []
    word_to_embeddings = _get_pretrained_embeddings(WORD_EMBEDDINGS[config.word_embedding])
    if load_all_pre_trained_word_embedding:
        for w in word_to_embeddings:
            word_dict.add(w)
    word_dict.accept_new = allow_new_words
    if allow_new_words:
        tokens = [string_sequence_to_ids(sent[0], word_dict, True, word_to_embeddings) for sent in samples]
    else:
        tokens = [string_sequence_to_ids(sent[0], word_dict, True) for sent in samples]

    labels = [string_sequence_to_ids(sent[-1], label_dict) for sent in samples]
    srl_features, feature_shapes = _get_srl_features(samples, config)

    sentences = []
    for i in range(len(tokens)):
        sentences.append((tokens[i],) + tuple(srl_features[i]) + (labels[i],))

    word_embedding = [word_to_embeddings[w] for w in word_dict.idx2str]
    word_embedding_shape = [len(word_embedding), len(word_embedding[0])]

    pred_pos = [sent[1] for sent in samples]

    return (sentences, [word_embedding, None, None], [word_embedding_shape, ] + feature_shapes, pred_pos)
