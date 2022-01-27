# Searching for Entities with Quantity Constraints over Web Content
[![Build Status](https://travis-ci.org/hovinhthinh/Qsearch.svg?branch=master)](https://travis-ci.org/hovinhthinh/Qsearch)

This is the repository of 2 projects: Qsearch, QuTE. 

---
## Qsearch
A system for answering quantity queries from text. ([Demo](https://qsearch.mpi-inf.mpg.de/))

### Search API
We host our text-based search API at the following address `http://qsearch.mpi-inf.mpg.de/search`. Below is an example basic query:
```
GET /search?full=skyscrapers%20higher%20than%201000%20feet
```

- List of available parameters:

| Name      | Default value | Description |
| --- | ---| --- |
| `full`      | --       |  The quantity filter query |
| `ntop`   | `20`        | Number of top retrieved answers |
| `model`   | `EMBEDDING`        | The ranking model being used, either `EMBEDDING` or `KL`, for *Context Embedding Distance* or *Kullback-Leibler Divergence* |
| `alpha`   | `0.5`      | Parameter of `EMBEDDING` model, see [1] |
| `lambda`   | `0.1`      | Parameter of `KL` model, see [1]  |

---
## QuTE
A system for answering quantity queries from web tables. ([Demo](https://qsearch.mpi-inf.mpg.de/table/))

### Search API
We host our text-based search API at the following address `http://qsearch.mpi-inf.mpg.de/search_table`. Below is an example basic query:
```
GET /search_table?full=skyscrapers%20higher%20than%201000%20feet
```

- List of available parameters:

| Name      | Default value | Description |
| --- | ---| --- |
| `full`      | --       |  The quantity filter query |
| `ntop`   | `20`        | Number of top retrieved answers |
| `HEADER_MATCH_WEIGHT` | `1.0` | Matching weight of column header, see [3] |
| `CAPTION_MATCH_WEIGHT` | `1.0` | Matching weight of table caption, see [3] |
| `TITLE_MATCH_WEIGHT` | `0.9` | Matching weight of page title, see [3] |
| `DOM_HEADING_MATCH_WEIGHT` | `0.9` | Matching weight of DOM headings, see [3] |
| `SAME_ROW_MATCH_WEIGHT` | `0.9` | Matching weight of same-row cells, see [3] |
| `RELATED_TEXT_MATCH_WEIGHT` | `0.8` | Matching weight of table surrounding text, see [3] |
| `linking-threshold` | `0` (no thresholding) | Column alignment thresholding value (a float between `0` and `1`) |
| `rescore`   | `false`        | Enable rescoring based on consistency learning: `true` or `false`, see [3] |

---
If you have any questions or requests, please contact me at the following email address:
[hvthinh@mpi-inf.mpg.de](mailto:hvthinh@mpi-inf.mpg.de?subject=[Qsearch]%20Contact)

## References
[1] V.T. Ho, Y. Ibrahim, K. Pal, K. Berberich, and G. Weikum. Qsearch: Answering Quantity Queries from Text. In *The 18th International Semantic Web Conference (ISWC)*, 2019.

[2] V.T. Ho, K. Pal, N. Kleer, K. Berberich, and G. Weikum. Entities with Quantities: Extraction, Search, and Ranking. In *The 13th ACM International Conference on Web Search and Data Mining (WSDM)*, 2020.

[3] V.T. Ho, K. Pal, S. Razniewski, K. Berberich, and G. Weikum. Extracting Contextualized Quantity Facts from Web Tables. In *The Web Conference (WWW)*, 2021.

[4] V.T. Ho, K. Pal, and G. Weikum. QuTE: Answering Quantity Queries from Web Tables. In *The International Conference on Management of Data (SIGMOD)*, 2021.
