
```
if [  $# -lt 2 ]
then
  echo "usage: create_project groupId artifactId"
  exit
fi

mvn archetype:generate \
-DarchetypeGroupId=org.apache.maven.archetypes \
-DgroupId=$1 \
-DartifactId=$2 \
-DinteractiveMode=false 
```