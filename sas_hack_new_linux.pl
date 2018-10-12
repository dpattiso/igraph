#!/usr/bin/perl

#why does this file exist? Well, Java seems to have problems dealing with the "<" symbol in the SAS preprocess command (and ">" and "|" etc.), so the only
#way to get the command it execute is through a 3rd party script.

use strict;
use warnings;

system("lama/preprocess/preprocess < output.sas");
