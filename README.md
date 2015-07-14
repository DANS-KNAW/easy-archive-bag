*Note: this project is in pre-alpha state, so below instructions may not work completely yet*

easy-archive-bag
================

Send a bag to archival storage.


SYNOPSIS
--------

    easy-archive-bag <bag directory> [<storage-service-url>]


DESCRIPTION
-----------



ARGUMENTS
---------

* ``<bag directory>`` -- the directory on the local file system containing the bag
* ``<storage-service-url>`` -- URL of a SWORD v2 service that stores the bag in archival storage


INSTALLATION AND CONFIGURATION
------------------------------

### Installation steps:

1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-archive-bag-<version> will be created
3. Create an environment variabele ``EASY_ARCHIVE_BAG_HOME`` with the directory from step 2 as its value
4. Add ``$EASY_ARCHIVE_BAG_HOME/bin`` to your ``PATH`` environment variable.


### Configuration

General configuration settings can be set in ``$EASY_ARCHIVE_BAG_HOME/cfg/appliation.properties`` and logging can be configured
in ``$EASY_ARCHIVE_BAG_HOME/cfg/logback.xml``. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 7 or higher
* Maven 3.3.3 or higher
 
Steps:

1. Clone and build the [dans-parent] project (*can be skipped if you have access to the DANS maven repository*)
      
        git clone https://github.com/DANS-KNAW/dans-parent.git
        cd dans-parent
        mvn install
2. Clone and build this project

        git clone https://github.com/DANS-KNAW/easy-archive-bag.git
        cd easy-archive-bag
        mvn install


[Fedora Digital Object Model]: https://wiki.duraspace.org/display/FEDORA38/Fedora+Digital+Object+Model
[Service APIs]: https://wiki.duraspace.org/display/FEDORA38/Service+APIs
[client command-line utilities]: https://wiki.duraspace.org/display/FEDORA38/Client+Command-line+Utilities
[FOXML]: https://wiki.duraspace.org/pages/viewpage.action?pageId=66585857
[dans-parent]: https://github.com/DANS-KNAW/dans-parent
[Digital Object Configuration]: #digital-object-configuration-file
[json]: http://json.org/
[ingest]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-ingest
[addDatastream]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-addDatastream
[addRelationship]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-addRelationship 
[SDOs]: #staged-digital-objects
[SDO-set]: #staged-digital-object-set
