# IGRAPH -- Intermediate Goal Recognition with A Planning Heuristic
A tool for performing goal recognition using domain independent heuristics. Also known as AUTOGRAPH in a previous incarnation. See "A New Heuristic-Based model of Goal Recognition Without Libraries" by David Pattison, 2015.


## Oct 2018
This is the code as it was post-corrections. It is untested in its current form, but it should still work (although a lot of the additional fluff for running experiments has been removed from Git... so let me know if it fails).


# Usage
This code requires that JavaFF 2.1 (https://github.com/dpattiso/javaff), JavaSAS (https://github.com/dpattiso/javasasplus) and PlanThreader (https://github.com/dpattiso/planthreader) all be on the classpath, along with the contents of the /lib directory.

Note that IGRAPH uses a specific build (circa 2011?) of Helmert's SAS+ translation scripts which disables reachability analysis, so this cannot be swapped out for a more recent version.

More detailed usage is in doc/README.

# Citation

The PhD thesis contains the most complete and representitive picture of this code, so it should probably be the thing to cite.

```
@phdthesis{phdthesis,
  author       = {David Pattison}, 
  title        = {A New Heuristic Based Model of Goal Recognition Without Libraries},
  school       = {University of Strathclyde},
  year         = 2015
}
```

Or the associated papers

```
@INPROCEEDINGS{pattison2011002,
	author = "D. Pattison and D. Long",
	title = "Accurately Determining Intermediate and Terminal Plan States Using Bayesian Goal Recognition",
	booktitle = "Proceedings of the 1st Workshop on Goal, Activity and Plan Recognition",
	year = "2011",
	editor = "D. Pattison and D. Long and C. W. Geib",
	pages = "32 -- 37",
	month = "June",
}
```

```
@INPROCEEDINGS{pattison2011001,
	author = "D. Pattison and D. Long",
	title = "Extracting Plans From Plans",
	booktitle = "Proceedings of the Eleventh {AI}*{IA} Symposium on Artificial Intelligence",
	year = "2010",
	editor = "A. Gerevini and A. Saetti",
	pages = "162 -- 168",
	month = "December",
	issn = "9788890492419",
}
```

```
@INPROCEEDINGS{pattison2010,
	author = "D. Pattison and D. Long",
	title = "Domain Independent Goal Recognition",
	booktitle = "{STAIRS} 2010: Proceedings of the Fifth Starting {AI} Researchers' Symposium ",
	year = "2010",
	editor = "T. Agnotes",
	volume = "222",
	pages = "238 -- 250",
	publisher = "IOS Press",
	month = "August",
	issn = "0922-6389",
}
```
