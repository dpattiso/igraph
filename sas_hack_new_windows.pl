#!perl

#why does this file exist? Well, Java seems to have problems dealing with the "<" symbol in the SAS preprocess command (and ">" and "|" etc.), so the only
#way to get the command it execute is through a 3rd party script.

#window backslashed version without linux perl path

use strict;
use warnings;

system("lama\\preprocess\\preprocess.exe < output.sas");
