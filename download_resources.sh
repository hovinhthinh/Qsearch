#!/usr/bin/env bash

echo "Downloading Wordnet"
wget http://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz -P ./resources/
tar -zxvf ./resources/WordNet-3.0.tar.gz -C ./resources/
rm ./resources/WordNet-3.0.tar.gz

echo "Downloading Glove"
wget http://nlp.stanford.edu/data/glove.6B.zip -P ./resources/
unzip ./resources/glove.6B.zip -d resources/glove/
rm ./resources/glove.6B.zip

echo "Download Yago3 Taxonomy"
wget http://resources.mpi-inf.mpg.de/yago-naga/yago3.1/yagoTaxonomy.tsv.7z -P ./resources/yago/
wget http://resources.mpi-inf.mpg.de/yago-naga/yago3.1/yagoTypes.tsv.7z -P ./resources/yago/

echo "Download treeTagger"
mkdir -p ./lib/treeTagger
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/tree-tagger-linux-3.2.3.tar.gz -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/tagger-scripts.tar.gz -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/install-tagger.sh -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/english-bnc.par.gz -P ./lib/treeTagger/
(cd ./lib/treeTagger && sh install-tagger.sh)