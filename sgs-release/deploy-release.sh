#!/usr/xpg4/bin/sh -xe
#
# This script is used to tag and deploy a branch of a project to the
# java.net maven repository.
#
# NOTE: This script is hardcoded to the Solaris /usr/xpg4/bin/sh shell
#

if [ $# -ne 6 ]; then
    echo "Invalid number of arguments"
    echo "Usage: $0 [url] [branchname] [tagname] [username] [password] [version]"
    exit 1
fi

URL=$1
BRANCH=$2
TAG=$3
USERNAME=$4
PASSWORD=$5
VERSION=$6

# remove any old working copy
rm -rf co

# checkout the branch
svn --non-interactive --username $USERNAME --password $PASSWORD co $URL/branches/$BRANCH co
cd co

# deploy to java.net
mvn deploy -DskipTests

# tag the branch
svn --non-interactive --username $USERNAME --password $PASSWORD copy $URL/branches/$BRANCH $URL/tags/$TAG -m "tagging release $VERSION"

