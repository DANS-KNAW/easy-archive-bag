easy-archive-bag
================
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-archive-bag.svg?branch=master)](https://travis-ci.org/DANS-KNAW/easy-archive-bag)

Send a bag to archival storage.


SYNOPSIS
--------

    easy-archive-bag [--user|-u <user>][--password,-p <password] \
                       <bag-directory> <uuid> [<bag-store-service-url>]


DESCRIPTION
-----------

Takes a directory in BagIt format, zips it and sends it via an HTTP `PUT` request to 
`bag-store-service-url/<uuid>`. If the `bag-info.txt` contains an entry `Is-Version-Of` the
value must be a valid bag-id. This bag-id is used to 



ARGUMENTS
---------

// TODO: build in readme unit test

INSTALLATION AND CONFIGURATION
------------------------------

### Installation steps:

// TODO: update


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


