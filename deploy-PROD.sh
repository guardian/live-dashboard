#!/bin/bash

TCROOT=http://teamcity.gudev.gnl:8111/guestAuth/repository/download
JARDIR=target/

wget $TCROOT/tools::deploy/latest.lastSuccessful/magenta.jar -O $JARDIR/magenta.jar

wget $TCROOT/tools::ec2-host-provider/latest.lastSuccessful/ec2-host-provider.jar -O $JARDIR/ec2-host-provider.jar

java -jar $JARDIR/magenta.jar --deployinfo "java -jar $JARDIR/ec2-host-provider.jar" PROD Analytics::live-dashboard $*
