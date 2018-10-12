# IGRAPH -- Intermediate Goal Recognition with A Planning Heuristic
A tool for performing goal recognition using domain independent heuristics. Also known as AUTOGRAPH in a previous incarnation. See "A New Heuristic-Based model of Goal Recognition Without Libraries" by David Pattison, 2015.

# Usage
This code requires that JavaFF 2.1 ([https://github.com/dpattiso/javaff]), JavaSAS ([[https://github.com/dpattiso/javasasplus]) and PlanThreader ([https://github.com/dpattiso/planthreader]) all be on the classpath, along with the contents of the /lib directory.

Note that IGRAPH uses a specific build (circa 2011?) of Helmert's SAS+ translation scripts which disables reachability analysis, so this cannot be swapped out for a more recent version.

## Oct 2018
This is the code as it was post-corrections. It is untested in its current form, but it should still work (although a lot of the additional fluff for running experiments has been removed from Git... so let me know if it fails).
