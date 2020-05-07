import numpy

from constants import *


def dynamic_programming_decode(score, token_idx, entity_feature, word_dict, label_dict, pred_pos):
    """
    This should only be used at test time.
    Decode the highest scoring sequence of tags with constraints:
    - Has at most 1 entity (E) at the beginning of the chosen entity
    - Tokens inside entities has to be tagged with other
    - Quantity tokens must be tagged with other
    Args:
      score: A [seq_len][num_tags] matrix of unary potentials.
      transition_params: A [num_tags, num_tags] matrix of binary potentials.
    Returns:
      [tags] [tag_score]
    """

    # score: [tokens][tags]
    seq_len = score.shape[0]
    ntags = score.shape[1]
    other_tag_id = label_dict.str2idx[OTHER_LABEL]
    e_tag_id = label_dict.str2idx[ENTITY_LABEL]
    x_tag_id = label_dict.str2idx[CONTEXT_LABEL]

    eb_feature_id = 1
    ei_feature_id = 2

    dp = numpy.full((score.shape[0], score.shape[1], 2), numpy.NINF)
    trace = numpy.zeros((score.shape[0], score.shape[1], 2, 2), dtype=int)
    # i: pos; j: tag; l: n_et
    dp[0][other_tag_id][0] = score[0][other_tag_id]
    if entity_feature[0] == eb_feature_id:
        dp[0][e_tag_id][1] = score[0][e_tag_id]
    if word_dict.idx2str[token_idx[0]] != QUANTITY_TOKEN:
        dp[0][x_tag_id][0] = score[0][x_tag_id]
    for i in range(1, seq_len):
        for j in range(ntags):
            for l in range(2):
                if word_dict.idx2str[token_idx[i]] == QUANTITY_TOKEN:
                    if j != other_tag_id:
                        continue
                    p = numpy.argmax(dp[i - 1, :, l])
                    trace[i][j][l] = (p, l)
                    dp[i][j][l] = dp[i - 1][p][l] + score[i][j]
                elif entity_feature[i] == ei_feature_id:
                    if j != other_tag_id:
                        continue
                    p = numpy.argmax(dp[i - 1, :, l])
                    trace[i][j][l] = (p, l)
                    dp[i][j][l] = dp[i - 1][p][l] + score[i][j]
                elif j == e_tag_id:
                    if entity_feature[i] != eb_feature_id or l == 0:
                        continue
                    p = numpy.argmax(dp[i - 1, :, 0])
                    trace[i][j][l] = (p, 0)
                    dp[i][j][l] = dp[i - 1][p][0] + score[i][j]
                else:  # context tag and other tag
                    p = numpy.argmax(dp[i - 1, :, l])
                    trace[i][j][l] = (p, l)
                    dp[i][j][l] = dp[i - 1][p][l] + score[i][j]
    tags = []
    tags_score = []
    l = 0 if numpy.max(dp[seq_len - 1, :, 0]) > numpy.max(dp[seq_len - 1, :, 1]) else 1
    p = numpy.argmax(dp[seq_len - 1, :, l])
    for i in range(seq_len - 1, -1, -1):
        tags.append(p)
        tags_score.append(score[i][p])
        if i > 0:
            p, l = trace[i][p][l]

    tags.reverse()

    tags_score.reverse()

    return tags, tags_score
