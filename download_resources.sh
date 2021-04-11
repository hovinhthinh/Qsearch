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

echo "Download Yago4 Taxonomy"
wget https://yago-knowledge.org/data/yago4/en/2020-02-24/yago-wd-class.nt.gz -P ./resources/yago/
wget https://yago-knowledge.org/data/yago4/en/2020-02-24/yago-wd-full-types.nt.gz -P ./resources/yago/

echo "Download OpenIE5"
wget https://qsearch.mpi-inf.mpg.de/resources/openie5/openie-assembly-5.1-SNAPSHOT-modified.jar -P ./lib/
mkdir -p ./resources/openie5/
wget https://qsearch.mpi-inf.mpg.de/resources/openie5/languageModel -P ./resources/openie5/

echo "Download treeTagger"
mkdir -p ./lib/treeTagger
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/tree-tagger-linux-3.2.3.tar.gz -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/tagger-scripts.tar.gz -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/install-tagger.sh -P ./lib/treeTagger/
wget https://www.cis.uni-muenchen.de/~schmid/tools/TreeTagger/data/english-bnc.par.gz -P ./lib/treeTagger/
(cd ./lib/treeTagger && sh install-tagger.sh)