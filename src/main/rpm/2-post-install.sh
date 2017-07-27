#!/usr/bin/env bash
#
# Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

NUMBER_OF_INSTALLATIONS=$1
MODULE_NAME=easy-archive-bag
INSTALL_DIR=/opt/dans.knaw.nl/${MODULE_NAME}
PHASE="POST-INSTALL"
ARCHIVE_STAGING_DIR=/var/opt/dans.knaw.nl/tmp/easy-archive-bag-staging

echo "$PHASE: START (Number of current installations: $NUMBER_OF_INSTALLATIONS)"

if [ ! -d ${ARCHIVE_STAGING_DIR} ]; then
    echo -n "Creating archive staging directory..."
    mkdir -p ${ARCHIVE_STAGING_DIR}
    chmod 777 ${ARCHIVE_STAGING_DIR}
    echo "OK"
fi

echo "$PHASE: DONE"
