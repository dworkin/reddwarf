#!/usr/xpg4/bin/sh -xe
#
# This script is used to automatically branch a project,
# increment the version number, and verify that the updates are correct
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

# create a new branch
svn --non-interactive --username $USERNAME --password $PASSWORD copy $URL/trunk $URL/branches/$BRANCH -m "branching for release $VERSION"

# remove any old working copy
rm -rf co

# checkout the branch
svn --non-interactive --username $USERNAME --password $PASSWORD co $URL/branches/$BRANCH co
cd co

# verify POMs
echo "Verify each POM has at most one SNAPSHOT version"
! (grep -c  "<version>.*SNAPSHOT<\/version>" $(find . -name pom.xml) | grep -E "pom.xml:[2-9]")

# update version in POMs
echo "Update version number in POMs"
perl -i -pe "s/<version>.*SNAPSHOT<\/version>/<version>$VERSION<\/version>/g" $(find . -name pom.xml)

# verify POM updates
! (grep -c  "<version>.*SNAPSHOT<\/version>" $(find . -name pom.xml))
mvn install -DskipTests
mvn clean

# checkin POM updates
svn --non-interactive commit -m "updating POM versions for release $VERSION"
