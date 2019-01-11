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
`bag-store-url/<uuid>`. If the `bag-info.txt` contains an entry `Is-Version-Of` the
value must be a valid bag-id. Note that the `bag-store-url` only contains the base
URL of the bag store, e.g. `http://myarchive/bagstores/mybagstore`.


ARGUMENTS
---------

    Options:

      -p, --password  <arg>   Password to use for authentication/authorisation to the storage service
      -u, --username  <arg>   Username to use for authentication/authorisation to the storage service
      -h, --help              Show help message
      -v, --version           Show version of this program

     trailing arguments:
      bag-directory (required)         Directory in BagIt format that will be sent to archival storage
      uuid (required)                  Identifier for the bag in archival storage
      bag-store-url (required)         base url to the store in which the bag needs to be archived

INSTALLATION AND CONFIGURATION
------------------------------
The preferred way of install this module is using the RPM package. This will install the binaries to
`/opt/dans.knaw.nl/easy-archive-bag`, the configuration files to `/etc/opt/dans.knaw.nl/easy-archive-bag`,
and will install the service script for `initd` or `systemd`. It will also set up a default bag store
at `/srv/dans.kanw.nl/bag-store`.

If you are on a system that does not support RPM, you can use the tarball. You will need to copy the
service scripts to the appropriate locations yourself.

BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM (if you want to build the RPM package).

Steps:

    git clone https://github.com/DANS-KNAW/easy-archive-bag.git
    cd easy-archive-bag
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.
