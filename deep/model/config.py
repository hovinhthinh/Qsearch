embedding_size = 200  # 50, 100, 200, 300

feed_forward_dim_medium = 128
feed_forward_dim_small = 64

batch_size = 128
learning_rate = 0.001

transformer_num_hidden_layers = 1
transformer_hidden_size = 256
transformer_filter_size = 1024
transformer_num_heads = 4

max_num_epoches = 1000
save_model_frequency = 1

max_entity_type_desc_len = 10
max_quantity_desc_len = 10

negative_to_positive_sampling_rate = 3  # must be integer

glove_path = '/home/hvthinh/Qsearch/resources/glove'
yago_type_path = '/local/home/hvthinh/datasets/yagoTransitiveTypeCompact.tsv.gz'
