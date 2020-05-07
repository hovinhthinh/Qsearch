def print_to_readable(predictions, num_tokens, label_dict, input_path, output_path=None):
    """ Print predictions to human-readable format.
    """
    if output_path is not None:
        fout = open(output_path, 'w')
    sample_id = 0
    for line in open(input_path, 'r'):
        if output_path is not None:
            fout.write(line.strip() + ' ||| ')
            fout.write(' '.join([label_dict.idx2str[p] for p in predictions[sample_id][0:num_tokens[sample_id]]]))
            fout.write('\n')
        else:
            print(' '.join([line.strip(), '|||'] + [label_dict.idx2str[p] for p in
                                           predictions[sample_id][0:num_tokens[sample_id]]]))
        sample_id += 1
    if output_path is not None:
        fout.close()
