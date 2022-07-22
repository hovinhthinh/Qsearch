# Searching for Entities with Quantity Constraints over Web Content

[![Build Status](https://travis-ci.org/hovinhthinh/Qsearch.svg?branch=master)](https://travis-ci.org/hovinhthinh/Qsearch)

This is the repository of 3 projects: Qsearch, QuTE, QL.

---

## 1) Qsearch

A system for answering quantity queries from text. ([Demo](https://qsearch.mpi-inf.mpg.de/))

For more information, please read the specific README at [Qsearch.md](Qsearch.md)

### Search API

We host our text-based search API at the following address `http://qsearch.mpi-inf.mpg.de/search`. Below is an example
basic query:

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

## 2) QuTE

A system for answering quantity queries from web tables. ([Demo](https://qsearch.mpi-inf.mpg.de/table/))

For more information, please read the specific README at [QuTE.md](QuTE.md)

### Search API

We host our table-based search API at the following address `http://qsearch.mpi-inf.mpg.de/search_table`. Below is an
example basic query:

```
GET /search_table?full=skyscrapers%20higher%20than%201000%20feet
```

- List of available parameters:

| Name      | Default value | Description |
| --- | ---| --- |
| `full`      | --       |  The quantity filter query |
| `ntop`   | `20`        | Number of top retrieved answers |
| `HEADER_MATCH_WEIGHT` | `1.0` | Matching weight of column header, see [2] |
| `CAPTION_MATCH_WEIGHT` | `1.0` | Matching weight of table caption, see [2] |
| `TITLE_MATCH_WEIGHT` | `0.9` | Matching weight of page title, see [2] |
| `DOM_HEADING_MATCH_WEIGHT` | `0.9` | Matching weight of DOM headings, see [2] |
| `SAME_ROW_MATCH_WEIGHT` | `0.9` | Matching weight of same-row cells, see [2] |
| `RELATED_TEXT_MATCH_WEIGHT` | `0.8` | Matching weight of table surrounding text, see [2] |
| `linking-threshold` | `0` (no thresholding) | Column alignment thresholding value (a float between `0` and `1`) |
| `rescore`   | `false`        | Enable rescoring based on consistency learning: `true` or `false`, see [2] |

---

## 3) QL

Please read the specific README at [QL.md](QL.md)

## 4) Resources
- In these projects, we developed a quantity recognition tool ([link](https://github.com/hovinhthinh/quantulum3)), which is a fork of [quantulum3](https://github.com/nielstron/quantulum3), adding an extra feature of linking recognized units to YAGO4.

---
If you have any questions or requests, please contact me at the following email address:
[hvthinh@mpi-inf.mpg.de](mailto:hvthinh@mpi-inf.mpg.de?subject=[Qsearch]%20Contact)
or [hovinhthinh@gmail.com](mailto:hovinhthinh@gmail.com?subject=[Qsearch]%20Contact)

## References

[1] V.T. Ho, Y. Ibrahim, K. Pal, K. Berberich, and G. Weikum. Qsearch: Answering Quantity Queries from Text. In *The
18th International Semantic Web Conference (ISWC)*, 2019.

[2] V.T. Ho, K. Pal, S. Razniewski, K. Berberich, and G. Weikum. Extracting Contextualized Quantity Facts from Web
Tables. In *The Web Conference (WWW)*, 2021.

[3] V.T. Ho, D. Stepanova, D. Milchevski, J. Stroetgen, and G. Weikum. Enhancing Knowledge Bases with Quantity Facts.
In *The Web Conference (WWW)*, 2022.
