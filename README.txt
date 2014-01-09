LV Tagger
----------------------------------------------

Latvian morphological tagger (analysis + disambiguation) and named entity recognition module.

(c) 2012 University of Latvia, Institute of Mathematics and Computer science
Machine learning code based on Stanford NLP kit at http://nlp.stanford.edu/software/

LICENSE 

The software is licensed under the full GPL.  Please see the file LICENCE.txt
The included text corpus data, books, newspaper extracts and dictionaries are copyrighted by their respective authors, and are available for research purposes only.

CONTACT

For information, bug reports, and any problems, contact:
    Pēteris Paikens
    PeterisP@gmail.com

USAGE

Run the included morphotagger.sh script for tokenization and morphological tagging of plaintext data. 
Input one sentence or paragraph per line; end processing with an empty line.
Output format is JSON by default, or tab-delimited columns of token-tag-lemma when run as './pipetool.sh -vert'.
./morphotagger.sh --help
for information on other input/output formats.
All input and output should be in UTF-8.

File interaction with standard UNIX stdin/stdout, i.e.
./morphotagger.sh <inputfile.txt >outputfile.txt

JSON example: [{"Vārds":"es","Marķējums":"pp10snn","Pamatforma":"es"}, {"Vārds":"roku","Marķējums":"vmnipt11san","Pamatforma":"rakt"}, {"Vārds":"roku","Marķējums":"ncfsa4","Pamatforma":"roka"}]
'Vārds' - exact surface form of the token; 'Marķējums' - morphological tag; first letter is part of speech, and each remaining letter denotes a separate morphological feature; and 'Pamatforma' contains the identified lemma.

For named entity tagging with a pre-trained model, run the included nertagger.sh script for a basic three-category (person, organization, location) tagging of data that is previously annotated with morphological features (see test_file.txt for an example)

SYSTEM REQUIREMENTS

JRE 1.7+ should be installed and available on path. 
The analyzer is rather memory-hungry; ~2gb memory should be available.

PERFORMANCE ESTIMATES

Performance will vary depending on text content, genre and quality. 
For morphological tagging in current test data we are seeing accuracy of 92.8% for the full morphological tag/lemma, and 98.2% for the part of speech disambiguation.

COMPILING FROM SOURCE

Eclipse project files and Ant build scripts are included.
The build scripts assume that the morphological word analysis module (https://github.com/PeterisP/morphology) will be located next to this project (../Morphology)
The following commands should build a working system on MacOS/Linux, assuming that Java/ant is installed.

git clone https://github.com/PeterisP/morphology.git
git clone https://github.com/PeterisP/LVTagger.git
cd morphology/
ant
cd ../LVTagger/
ant

DOWNLOADS

The binary downloads and pretrained models are excluded from github due to their size. They can be built from source or obtained by e-mailing at the contacts listed above.

REFERENCES

Properties of Latvian morphological tagger are published in http://www.ep.liu.se/ecp_article/index.en.aspx?issue=085;article=024
The named entity recognition system was initially described at http://www.booksonline.iospress.nl/Content/View.aspx?piid=32333 
