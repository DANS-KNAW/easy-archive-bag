easy-archive-bag
================
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-archive-bag.svg?branch=master)](https://travis-ci.org/DANS-KNAW/easy-archive-bag)

Send a bag to archival storage.


SYNOPSIS
--------

    easy-archive-bag <bag directory> [<storage-service-url>]


DESCRIPTION
-----------

Takes a directory in BagIt format, zips it and sends it to the provided storage service URL. The storage service 
must support the [SWORD v2] protocol.


ARGUMENTS
---------

* ``<bag directory>`` -- the directory on the local file system containing the bag
* ``<storage-service-url>`` -- URL of a SWORD v2 service that stores the bag in archival storage. By default the 
   URL configured in ``$EASY_ARCHIVE_BAG_HOME/cfg/application.properties`` will be used.


INSTALLATION AND CONFIGURATION
------------------------------

### Installation steps:

1. Unzip the tarball to a directory of your choice, e.g. /opt/.
2. A new directory called ``easy-archive-bag-<version>`` will be created.
3. Create an environment variabele ``EASY_ARCHIVE_BAG_HOME`` with the directory from step 2 as its value.
4. Add ``$EASY_ARCHIVE_BAG_HOME/bin`` to your ``PATH`` environment variable.


### Configuration

General configuration settings can be set in ``$EASY_ARCHIVE_BAG_HOME/cfg/appliation.properties`` and logging can be
configured in ``$EASY_ARCHIVE_BAG_HOME/cfg/logback.xml``. The available settings are explained in comments in 
aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
 
Steps:

        git clone https://github.com/DANS-KNAW/easy-archive-bag.git
        cd easy-archive-bag
        mvn install

[SWORD v2]: http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html

